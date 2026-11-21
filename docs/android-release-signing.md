# Firma Android y registro para distribución externa

| Aplicación | Identificador de producción | Huella SHA-256 del nuevo certificado |
| --- | --- | --- |
| ANON | `dev.uhden.anon` | `fef0f95389f16772a0e51d6b0fd5be3d376c63a5add0d5034f8d05ee956d7877` |
| NERO | `dev.uhden.nero` | `913287af46665e23a5e980e040b44a7b1621bfba1db9c604862e0eb2f9c0fa53` |

Las versiones históricas de [ANON](https://appteka.store/app/74cr307271) y
[NERO](https://appteka.store/app/18ar307272) usan los identificadores
`io.anonero.anon` y `io.anonero.nero`. En los APK 1.0.1 de ese espejo se verificaron
las firmas v2 y el certificado compartido
`234063d78bcb125b864c7e7a61374c8e0731a5aebd9265e84b73b65fdaa1c044`.
Los identificadores nuevos crean instalaciones independientes con su propio
almacenamiento y Android Keystore. La instalación anterior conserva sus datos. Antes
de retirar la aplicación anterior, verifica el acceso a la cartera desde la instalación nueva
mediante los mecanismos de recuperación compatibles con tu cartera.

## Modelo de amenazas y validación

- Las claves protegen la identidad de cada APK y las actualizaciones que Android acepta.
  Un colaborador, una dependencia comprometida o alguien que controle un runner o
  la cuenta administradora podría intentar distribuir APK alterados.
- El código y Gradle se ejecutan con permisos de lectura y sin secretos de firma. La
  compilación entrega los APK sin firmar en artefactos separados. El despacho desde
  `main` debe coincidir con el commit de la etiqueta. Los entornos requieren aprobación
  y entregan cada clave a un job sin checkout ni ejecución de código del proyecto.
- Se hacen públicos los identificadores y las huellas, que vinculan las versiones de
  cada APK con su editor. No se incorpora ningún identificador de usuario ni de cartera.
  Aprobar un despliegue implica confiar en el commit revisado, los binarios compilados,
  los runners de GitHub y las herramientas fijadas del SDK.
- Una clave incorrecta bloquearía actualizaciones, y una clave perdida impediría
  publicar actualizaciones con la identidad original. Conserva copias cifradas
  desconectadas y guarda las contraseñas por separado. Recupera la misma clave y
  comprueba su huella antes de restaurar cualquier credencial de CI.
- Verifica en CI ambos conjuntos de APK, las huellas, los hashes, las pruebas
  instrumentadas y la instalación/actualización de ambos paquetes. La inspección
  estática y la verificación ZIP no sustituyen una prueba en un dispositivo de 16 KiB.
  Ante un fallo, interrumpe la publicación y restaura el workflow revisado sin cambiar
  las identidades. Si ya se publicó una versión, corrige hacia adelante con un
  `versionCode` superior.

## Custodia y recuperación

Desde una terminal Linux local, el propietario puede ejecutar
`bash scripts/provision-android-signing.sh` para preparar los dos almacenes y registrar
sus credenciales. Se solicitan contraseñas sin eco. El programa reutiliza almacenes
existentes, verifica el certificado y la copia de seguridad, y se detiene si un entorno
ya contiene secretos sin una huella que permita asociarlos a la clave local. Consulta
GitHub por nombre de secreto. Los valores permanecen en el almacén local y en el entorno.

En `~/.local/share/android-signing/anonero/` se guardan cada
`<flavor>-release.p12`, certificado público `<flavor>-release.pem` y copia
cifrada `<flavor>-release.p12.gpg`. Conserva copias desconectadas de ambos archivos
`.gpg` y de las huellas públicas. Guarda las contraseñas independientes en un gestor
seguro y comprueba periódicamente la recuperación.

Para restaurar, crea un directorio privado de recuperación en Linux. Descifra cada copia
hacia un archivo nuevo con `umask 077`, por ejemplo:

```bash
umask 077
recovery="$(mktemp -d "$HOME/.local/share/android-signing/anonero/restore.XXXXXXXX")"
gpg --output "$recovery/anon-release.p12" --decrypt /ruta/copia/anon-release.p12.gpg
keytool -exportcert -keystore "$recovery/anon-release.p12" -alias anon | sha256sum
```

Comprueba por separado que GnuPG y `keytool` terminaron correctamente y que la
huella coincide con la fijada. Repite con `nero`. Coloca el almacén recuperado en su
ruta de custodia únicamente si falta el original, con modo `0600`. Conserva el
alias y la contraseña PKCS#12 vigentes.

## Android Developer Console

Crea o usa una cuenta de **distribución completa** en Android Developer Console y
completa la verificación de identidad. En **Packages**, registra por separado
`dev.uhden.anon` y `dev.uhden.nero` con las huellas verificadas de esta página.
Comprueba que la consola muestra cada paquete como registrado. El registro vincula
públicamente la aplicación con su editor. La distribución de APK sigue en GitHub y
otros canales independientes. [Guía oficial](https://developer.android.com/developer-verification/guides/android-developer-console)

Si la consola exige demostrar la propiedad de un paquete:

1. Descarga del job `build` el APK **universal sin firmar** del paquete, a un
   directorio privado fuera del checkout. Copia el contenido exacto que te dé
   la consola a `assets/adi-registration.properties` dentro de ese directorio.
2. En una copia local del APK sin firma, añade el archivo mediante
   `zip unsigned-proof.apk assets/adi-registration.properties`. En este ejemplo,
   `unsigned-proof.apk` es la copia local que ya existe y el directorio actual
   contiene `assets/`.
3. Alinea y firma esa copia con las Android SDK Build Tools oficiales. Selecciona
   `flavor=anon` o `flavor=nero` según el desafío. `apksigner` solicita la
   contraseña en la terminal.

   ```bash
   flavor=anon
   tools="$ANDROID_SDK_ROOT/build-tools/37.0.0"
   store="$HOME/.local/share/android-signing/anonero/$flavor-release.p12"
   "$tools/zipalign" -P 16 4 unsigned-proof.apk aligned-proof.apk
   "$tools/apksigner" sign --ks "$store" --ks-key-alias "$flavor" \
     --v1-signing-enabled false --v2-signing-enabled true \
     --v3-signing-enabled true --v4-signing-enabled false \
     --out "$flavor-verification.apk" aligned-proof.apk
   "$tools/apksigner" verify --verbose --print-certs "$flavor-verification.apk"
   "$tools/aapt2" dump packagename "$flavor-verification.apk"
   ```

4. Comprueba el paquete y la huella del firmante. Entrega a la consola únicamente el
   APK de verificación y conserva el desafío fuera del repositorio y de Releases.
   El workflow rechaza cualquier APK de distribución que contenga ese archivo.
