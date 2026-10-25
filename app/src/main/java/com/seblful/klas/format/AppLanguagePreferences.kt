package com.seblful.klas.format

import android.app.LocaleManager
import android.content.Context
import android.os.Build
import android.os.LocaleList
import com.seblful.klas.data.settings.SettingsRepository
import com.seblful.klas.di.ApplicationContext
import com.seblful.klas.di.ApplicationScope
import com.seblful.klas.domain.model.AppLanguage
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

interface AppLanguagePlatform {
    /** Null on Android versions without per-app language settings. */
    val language: AppLanguage?
    var migrated: Boolean
    fun apply(language: AppLanguage)
}

@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
class AndroidAppLanguagePlatform @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : AppLanguagePlatform {
    private val preferences by lazy { context.getSharedPreferences("app-language", Context.MODE_PRIVATE) }

    override val language: AppLanguage?
        get() {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
            val locales = context.getSystemService(LocaleManager::class.java).applicationLocales
            return if (locales.isEmpty) AppLanguage.SYSTEM else AppLanguage.of(listOf(locales[0].toLanguageTag()))
        }

    override var migrated: Boolean
        get() = preferences.getBoolean("migrated", false)
        set(value) { preferences.edit().putBoolean("migrated", value).apply() }

    override fun apply(language: AppLanguage) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || this.language == language) return
        context.getSystemService(LocaleManager::class.java).applicationLocales =
            LocaleList.forLanguageTags(language.tag.orEmpty())
    }
}

/** Mirrors platform changes into shared settings and publishes in-app choices back to Android. */
@SingleIn(AppScope::class)
class AppLanguagePreferences @Inject constructor(
    private val settings: SettingsRepository,
    private val platform: AppLanguagePlatform,
    @param:ApplicationScope private val scope: CoroutineScope,
) {
    private val started = AtomicBoolean()
    private val mutex = Mutex()
    private var lastPlatformLanguage: AppLanguage? = null

    fun start() {
        if (!started.compareAndSet(false, true)) return
        scope.launch {
            refresh()
            if (platform.language == null) return@launch
            settings.observe().map { it.language }.distinctUntilChanged().collect { language ->
                mutex.withLock {
                    withContext(Dispatchers.Main.immediate) { platform.apply(language) }
                    lastPlatformLanguage = language
                }
            }
        }
    }

    suspend fun refresh() = mutex.withLock {
        val requested = platform.language ?: return@withLock
        // An unchanged platform value must not overwrite an in-app choice awaiting publication.
        if (lastPlatformLanguage == requested) return@withLock
        val stored = settings.get().language
        if (!platform.migrated && requested == AppLanguage.SYSTEM && stored != AppLanguage.SYSTEM) {
            withContext(Dispatchers.Main.immediate) { platform.apply(stored) }
            lastPlatformLanguage = stored
        } else {
            if (stored != requested) settings.update { it.copy(language = requested) }
            lastPlatformLanguage = requested
        }
        platform.migrated = true
    }
}
