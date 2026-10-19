package com.seblful.klas.diagnostics

import android.content.Context
import android.os.Build
import com.seblful.klas.di.ApplicationContext
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

@ContributesTo(AppScope::class)
@BindingContainer
object DiagnosticsModule {

    /**
     * The build/device facts a crash header carries. Read once here so [CrashLogger] takes them
     * as plain data — `versionName` falls back to "?" rather than letting a
     * `PackageManager` hiccup become a second crash while recording the first.
     */
    @Provides
    @SingleIn(AppScope::class)
    fun provideDeviceInfo(@ApplicationContext context: Context): DeviceInfo = DeviceInfo(
        appVersion = runCatching {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
        }.getOrDefault("?"),
        androidSdk = Build.VERSION.SDK_INT,
        device = "${Build.MANUFACTURER} ${Build.MODEL}",
    )
}
