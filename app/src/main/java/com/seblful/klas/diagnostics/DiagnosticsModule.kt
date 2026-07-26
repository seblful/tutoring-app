package com.seblful.klas.diagnostics

import android.content.Context
import android.os.Build
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DiagnosticsModule {

    @Binds
    @Singleton
    abstract fun bindCrashLogStore(impl: FileCrashLogStore): CrashLogStore

    companion object {
        /**
         * The build/device facts a crash header carries. Read once here so [CrashLogger] takes them
         * as plain data — `versionName` falls back to "?" rather than letting a
         * `PackageManager` hiccup become a second crash while recording the first.
         */
        @Provides
        @Singleton
        fun provideDeviceInfo(@ApplicationContext context: Context): DeviceInfo = DeviceInfo(
            appVersion = runCatching {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
            }.getOrDefault("?"),
            androidSdk = Build.VERSION.SDK_INT,
            device = "${Build.MANUFACTURER} ${Build.MODEL}",
        )
    }
}
