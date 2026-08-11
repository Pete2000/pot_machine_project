package com.example.plccontroller.data.local

import java.security.MessageDigest

object HashUtil {
    fun md5(value: String): String {
        val digest =
            MessageDigest
                .getInstance("MD5")
                .digest(value.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { byte -> "%02x".format(byte) }
    }

    fun potTaskBaseHash(
        ikmsOrder: String,
        rootPosFoodCode: String,
        orderedChildFoodCodes: List<String>,
    ): String = md5(ikmsOrder + rootPosFoodCode + orderedChildFoodCodes.joinToString(""))
}
