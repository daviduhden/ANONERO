package io.anonero.util

import android.content.Context
import android.os.PowerManager


fun Context.isIgnoringBatteryOptimizations(): Boolean {
    val powerManager = this.getSystemService(Context.POWER_SERVICE) as PowerManager
    val packageName = this.packageName
    return powerManager.isIgnoringBatteryOptimizations(packageName)
}