/*
 * Copyright (c) 2026, David Uhden Collado
 * Copyright (c) 2022-2026, r4v3r23
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package io.anonero.util.backup

/**
 * Validation for node records imported from a backup.
 *
 * A backup is untrusted input: it can contain a host with a scheme, credentials, a path,
 * whitespace, or a port outside the TCP range. Persisting such a value would later reach
 * wallet2 as a daemon address. Invalid records are dropped instead of becoming the active
 * node; a valid wallet restore does not need a node to succeed.
 */
internal object BackupNodePolicy {
    private val DNS_OR_IPV4 = Regex("^[A-Za-z0-9](?:[A-Za-z0-9.-]*[A-Za-z0-9])?$")

    /** Returns a normalized host without brackets, or null when the value is unsafe. */
    fun normalizeHost(host: String?): String? {
        val trimmed = host?.trim().orEmpty()
        if (trimmed.isEmpty() || trimmed.length > 253) return null
        if (trimmed.contains("://") || trimmed.contains('/') || trimmed.contains('@')) return null
        if (trimmed.any { it.isWhitespace() }) return null

        val unbracketed =
            if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                trimmed.substring(1, trimmed.length - 1)
            } else {
                trimmed
            }
        if (unbracketed.isEmpty()) return null

        return if (':' in unbracketed) {
            // IPv6 literal, optionally with a zone identifier. At least two colons are
            // required so a host:port value such as 1.2.3.4:8443 is rejected. No DNS lookup
            // is performed here; wallet2 receives the host unchanged.
            if (unbracketed.count { it == ':' } < 2) return null
            val zoneIndex = unbracketed.indexOf('%')
            val addressPart = if (zoneIndex >= 0) unbracketed.substring(0, zoneIndex) else unbracketed
            val zonePart = if (zoneIndex >= 0) unbracketed.substring(zoneIndex + 1) else null
            val validAddress =
                addressPart.isNotEmpty() &&
                    addressPart.all {
                        it.isDigit() || it in 'a'..'f' || it in 'A'..'F' ||
                            it == ':' || it == '.'
                    }
            val validZone =
                zonePart == null ||
                    (
                        zonePart.isNotEmpty() && zonePart.none { it == '%' } &&
                            zonePart.all { it.isLetterOrDigit() || it == '_' || it == '.' || it == '-' }
                    )
            if (validAddress && validZone) unbracketed else null
        } else {
            unbracketed.takeIf { DNS_OR_IPV4.matches(it) }
        }
    }

    fun normalizePort(port: Int): Int? = port.takeIf { it in 1..65535 }
}
