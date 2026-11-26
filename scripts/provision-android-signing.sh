#!/usr/bin/env bash
#
# Copyright (c) 2026, David Uhden Collado
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

set +x
set -Eeuo pipefail
trap 'printf "Aprovisionamiento detenido en la línea %d.\n" "$LINENO" >&2' ERR
umask 077
# Provision production signing from the owner's interactive session.
if [[ ! -t 0 ]]; then
	echo "Ejecuta este programa en una terminal local interactiva." >&2
	exit 1
fi

repo=daviduhden/ANONERO
[[ "$(gh api user --jq .login)" == daviduhden ]]
[[ "$(gh repo view "$repo" --json nameWithOwner --jq .nameWithOwner)" == "$repo" ]]
# Keep scratch and key stores on one filesystem for no-overwrite hard links.
base="$HOME/.local/share/android-signing"
dir="$base/anonero"
for path in "$base" "$dir"; do
	[[ ! -L $path ]]
	mkdir -p -- "$path"
	chmod 700 -- "$path"
done
exec 9>"$dir/.provision.lock"
flock -n 9
scratch="$(mktemp -d "$dir/.provision.XXXXXXXX")"
trap 'rm -rf -- "$scratch"' EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
export GPG_TTY
GPG_TTY="$(tty)"

# Validate both environments before writing any secrets.
for flavor in anon nero; do
	env="android-$flavor-release"
	prefix="${flavor^^}"
	gh api "repos/$repo/environments/$env" |
		jq -e '.can_admins_bypass == false and
      .deployment_branch_policy.custom_branch_policies == true and
      any(.protection_rules[]; .type == "required_reviewers" and
        any(.reviewers[]; .reviewer.login == "daviduhden"))' >/dev/null
	gh api "repos/$repo/environments/$env/deployment-branch-policies" |
		jq -e '.total_count == 1 and .branch_policies[0].name == "main" and
      .branch_policies[0].type == "branch"' >/dev/null
	gh secret list --repo "$repo" --env "$env" --json name --jq '.[].name' >"$scratch/$flavor.secrets"
	pinned="$(gh variable list --repo "$repo" --env "$env" --json name,value \
		--jq ".[] | select(.name == \"${prefix}_CERT_SHA256\") | .value")"
	if [[ -n $pinned ]]; then
		[[ $pinned =~ ^[0-9a-f]{64}$ ]]
		if [[ ! -f "$dir/$flavor-release.p12" ]]; then
			echo "Restaura el almacén de ${flavor^^} asociado a la huella registrada." >&2
			exit 1
		fi
		printf '%s\n' "$pinned" >"$scratch/$flavor.pin"
	else
		# Existing secrets without a trusted pin cannot be tied to a local key.
		[[ ! -s "$scratch/$flavor.secrets" ]]
	fi
done

declare -A passwords
for flavor in anon nero; do
	printf '\nContraseña PKCS#12 para %s (mínimo 24 caracteres, usa una aleatoria del gestor de contraseñas).\n' "${flavor^^}"
	while true; do
		IFS= read -rs -p "Contraseña: " password
		printf '\n'
		IFS= read -rs -p "Repite la contraseña: " confirmation
		printf '\n'
		if [[ ${#password} -lt 24 || $password != "$confirmation" ]]; then
			echo "Contraseña demasiado corta o confirmación distinta. Inténtalo de nuevo." >&2
		elif [[ $flavor == nero && $password == "${passwords[anon]}" ]]; then
			echo "Usa una contraseña PKCS#12 distinta de la de ANON." >&2
		else
			break
		fi
	done
	passwords[$flavor]="$password"
	unset confirmation

	store="$dir/$flavor-release.p12"
	backup="$store.gpg"
	cert="$dir/$flavor-release.pem"
	[[ ! -L $store && ! -L $backup && ! -L $cert ]]
	if [[ ! -e $store ]]; then
		[[ ! -e $backup && ! -e $cert ]]
		SIGNING_PASSWORD="$password" keytool -genkeypair -keystore "$scratch/$flavor.p12" \
			-storetype PKCS12 -storepass:env SIGNING_PASSWORD -alias "$flavor" \
			-keyalg RSA -keysize 4096 -sigalg SHA256withRSA -validity 11323 \
			-dname "CN=${flavor^^} Android Release, O=ANONERO"
		ln -- "$scratch/$flavor.p12" "$store"
	fi
	[[ -f $store ]]
	chmod 600 -- "$store"
	SIGNING_PASSWORD="$password" keytool -J-Duser.language=en -J-Duser.country=US -list -v -keystore "$store" -storetype PKCS12 \
		-storepass:env SIGNING_PASSWORD -alias "$flavor" |
		grep -Fx 'Entry type: PrivateKeyEntry' >/dev/null
	SIGNING_PASSWORD="$password" keytool -exportcert -rfc -keystore "$store" \
		-storetype PKCS12 -storepass:env SIGNING_PASSWORD -alias "$flavor" >"$scratch/$flavor.pem"
	if [[ ! -e $cert ]]; then
		ln -- "$scratch/$flavor.pem" "$cert"
	fi
	cmp -- "$scratch/$flavor.pem" "$cert"
	openssl x509 -in "$cert" -outform DER | sha256sum | cut -d ' ' -f1 >"$scratch/$flavor.sha256"
	if [[ -f "$scratch/$flavor.pin" ]]; then
		cmp -- "$scratch/$flavor.sha256" "$scratch/$flavor.pin"
	fi
	openssl x509 -in "$cert" -pubkey -noout | openssl pkey -pubin -outform DER |
		sha256sum | cut -d ' ' -f1 >"$scratch/$flavor.spki"

	if [[ ! -e $backup ]]; then
		printf 'Crea una contraseña GnuPG independiente para la copia cifrada de %s.\n' "${flavor^^}"
		gpg --no-symkey-cache --symmetric --cipher-algo AES256 \
			--output "$scratch/$flavor.gpg" "$store"
		gpg --no-symkey-cache --decrypt "$scratch/$flavor.gpg" | cmp -- "$store" -
		ln -- "$scratch/$flavor.gpg" "$backup"
	else
		gpg --no-symkey-cache --decrypt "$backup" | cmp -- "$store" -
	fi
	unset password
done

anon_cert="$(<"$scratch/anon.sha256")"
nero_cert="$(<"$scratch/nero.sha256")"
[[ $anon_cert != "$nero_cert" ]]
[[ "$(<"$scratch/anon.spki")" != "$(<"$scratch/nero.spki")" ]]
# Register each identity before adding secrets so a retry can require the same key.
for flavor in anon nero; do
	env="android-$flavor-release"
	prefix="${flavor^^}"
	digest="$(<"$scratch/$flavor.sha256")"
	if [[ ! -f "$scratch/$flavor.pin" ]]; then
		gh variable set "${prefix}_CERT_SHA256" --repo "$repo" --env "$env" --body "$digest"
	fi
	if ! grep -Fxq "${prefix}_KEYSTORE_BASE64" "$scratch/$flavor.secrets"; then
		base64 -w0 -- "$dir/$flavor-release.p12" |
			gh secret set "${prefix}_KEYSTORE_BASE64" --repo "$repo" --env "$env"
	fi
	if ! grep -Fxq "${prefix}_KEYSTORE_PASSWORD" "$scratch/$flavor.secrets"; then
		printf '%s' "${passwords[$flavor]}" |
			gh secret set "${prefix}_KEYSTORE_PASSWORD" --repo "$repo" --env "$env"
	fi
	gh secret list --repo "$repo" --env "$env" --json name --jq '.[].name' >"$scratch/$flavor.secrets"
	grep -Fx "${prefix}_KEYSTORE_BASE64" "$scratch/$flavor.secrets"
	grep -Fx "${prefix}_KEYSTORE_PASSWORD" "$scratch/$flavor.secrets"
	[[ "$(gh variable get "${prefix}_CERT_SHA256" --repo "$repo" --env "$env" --json value --jq .value)" == "$digest" ]]
	printf '%s SHA-256 %s\n' "$prefix" "$digest"
done
unset passwords
