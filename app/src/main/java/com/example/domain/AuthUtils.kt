package com.example.domain

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Locale

object AuthUtils {
    const val DEFAULT_ADMIN_MOBILE = "9988221707"
    const val DEFAULT_ADMIN_PASSWORD = "AdmiN@6227#"
    const val DEFAULT_ADMIN_NAME = "PBS Admin"

    private val random = SecureRandom()

    fun hashPassword(rawPassword: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(rawPassword.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun normalizeMobile(input: String): String =
        input.filter { it.isDigit() }.takeLast(10)

    fun generateEmployeeLoginId(
        employeeCode: String,
        existingLoginIds: Set<String>,
        existingCount: Int
    ): String {
        val normalizedExisting = existingLoginIds.map { it.trim().uppercase(Locale.US) }.toSet()
        val preferred = employeeCode.trim().uppercase(Locale.US)
        if (preferred.isNotBlank() && preferred !in normalizedExisting) {
            return preferred
        }
        var number = existingCount + 101
        while (true) {
            val candidate = "PBS-$number"
            if (candidate !in normalizedExisting) {
                return candidate
            }
            number++
        }
    }

    fun generateReadablePassword(): String {
        val upper = "ABCDEFGHJKLMNPQRSTUVWXYZ"
        val lower = "abcdefghjkmnpqrstuvwxyz"
        val digits = "23456789"
        val symbols = "@#$"
        val c1 = upper[random.nextInt(upper.length)]
        val c2 = lower[random.nextInt(lower.length)]
        val c3 = lower[random.nextInt(lower.length)]
        val sym = symbols[random.nextInt(symbols.length)]
        val d1 = digits[random.nextInt(digits.length)]
        val d2 = digits[random.nextInt(digits.length)]
        val d3 = digits[random.nextInt(digits.length)]
        val d4 = digits[random.nextInt(digits.length)]
        return "$c1$c2$c3$sym$d1$d2$d3$d4"
    }
}
