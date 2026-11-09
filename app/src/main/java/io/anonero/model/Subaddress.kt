package io.anonero.model

import kotlinx.serialization.Serializable
import java.util.regex.Pattern

@Serializable
class Subaddress(
    private val accountIndex: Int,
    val addressIndex: Int,
    val address: String,
    var label: String
) : Comparable<Subaddress> {
    var amount: Long = 0

    override fun compareTo(other: Subaddress): Int { // newer is <
        val compareAccountIndex = other.accountIndex - accountIndex
        return if (compareAccountIndex == 0) other.addressIndex - addressIndex else compareAccountIndex
    }

    val squashedAddress: String
        get() = address.substring(0, 8) + "…" + address.substring(address.length - 8)

    val displayLabel: String
        get() = if (label.isEmpty() || DEFAULT_LABEL_FORMATTER.matcher(label)
                .matches()
        ) "SubAddress #$addressIndex" else label

    companion object {
        val DEFAULT_LABEL_FORMATTER: Pattern =
            Pattern.compile("^[0-9]{4}-[0-9]{2}-[0-9]{2}-[0-9]{2}:[0-9]{2}:[0-9]{2}$")
    }

    val totalAmount: Long
        get() = amount


}