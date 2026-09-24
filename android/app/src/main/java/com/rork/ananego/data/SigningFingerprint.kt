package com.rork.ananego.data

import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import java.security.MessageDigest

/**
 * Returns the SHA-1 fingerprint (AA:BB:… format) of the certificate that signed the
 * installed app. Google Play re-signs builds, so this reveals which fingerprint the
 * Maps API key restriction must allow on this exact device.
 */
fun installedSigningSha1(context: Context): String? = runCatching {
    val pm = context.packageManager
    val signatures: Array<Signature>? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            .signingInfo?.apkContentsSigners
    } else {
        @Suppress("DEPRECATION")
        pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES).signatures
    }
    val cert = signatures?.firstOrNull() ?: return@runCatching null
    MessageDigest.getInstance("SHA-1").digest(cert.toByteArray())
        .joinToString(":") { "%02X".format(it) }
}.getOrNull()
