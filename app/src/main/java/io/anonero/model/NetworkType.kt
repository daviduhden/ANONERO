package io.anonero.model

enum class NetworkType(val value: Int) {
    NetworkType_Mainnet(0), NetworkType_Testnet(1), NetworkType_Stagenet(2);

    companion object {
        fun fromInteger(n: Int): NetworkType? {
            when (n) {
                0 -> return NetworkType_Mainnet
                1 -> return NetworkType_Testnet
                2 -> return NetworkType_Stagenet
            }
            return null
        }
    }

    override fun toString(): String {
        return when (this) {
            NetworkType_Mainnet -> "mainnet"
            NetworkType_Testnet -> "testnet"
            NetworkType_Stagenet -> "stagenet"
        }
    }


    fun toStringForBackUp(): String {
        return when (this) {
            NetworkType_Mainnet -> "NetworkType_Mainnet"
            NetworkType_Testnet -> "NetworkType_Testnet"
            NetworkType_Stagenet -> "NetworkType_Stagenet"
        }
    }
}