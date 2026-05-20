package com.omniread.app.util

import android.content.Context
import android.provider.Settings
import java.security.MessageDigest

fun deviceFingerprint(ctx: Context): String {
    val androidId = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown"
    val raw = "$androidId:${android.os.Build.MANUFACTURER}:${android.os.Build.MODEL}"
    val md = MessageDigest.getInstance("SHA-256")
    val bytes = md.digest(raw.toByteArray())
    return bytes.joinToString("") { "%02x".format(it) }.take(48)
}

fun currentApiBase(): String = com.omniread.app.BuildConfig.API_BASE_URL
