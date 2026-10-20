package com.seblful.klas.di

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import com.seblful.klas.notifications.ReminderActionReceiver
import com.seblful.klas.notifications.ReminderReceiver
import com.seblful.klas.testutil.ProjectFiles
import com.seblful.klas.testutil.SourceText
import com.seblful.klas.work.BootReceiver
import com.seblful.klas.work.ClockChangeReceiver
import dev.zacsweers.metro.createGraphFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = KlasGraphTest.GraphApp::class)
class KlasGraphTest {

    class GraphApp : Application(), AppGraph.Holder {
        override val graph: AppGraph by lazy { createGraphFactory<KlasGraph.Factory>().create(this) }
    }

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val graph: AppGraph = context.appGraph

    @Test
    fun `every view model in the app is one the graph can build`() {
        val declared = declaredSubclassesOf("ViewModel").keys
        val bound = (graph.viewModelProviders.keys + graph.assistedFactoryProviders.keys)
            .map { it.simpleName }.toSet()

        assertTrue("the scan found the app's view models (${declared.size})", declared.size >= MIN_VIEW_MODELS)
        assertEquals(
            "A view model the graph cannot build fails as its screen opens. Annotate it " +
                "@ViewModelKey + @ContributesIntoMap(AppScope::class), or give it an assisted Factory.",
            declared,
            bound,
        )
    }

    @Test
    fun `every worker in the app is one WorkManager is handed by the graph`() {
        val workers = declaredSubclassesOf("CoroutineWorker")
        assertTrue("the scan found the app's workers", workers.isNotEmpty())

        for ((name, className) in workers) {
            @Suppress("UNCHECKED_CAST")
            val type = Class.forName(className) as Class<out ListenableWorker>
            val worker = runCatching {
                TestListenableWorkerBuilder.from(context, type).setWorkerFactory(graph.workerFactory).build()
            }.getOrNull()
            assertTrue(
                "$name is not built by the graph's worker factory — does it have its Factory?",
                type.isInstance(worker),
            )
        }
    }

    @Test
    fun `every manifest receiver is injected from the application's graph`() {
        assertNotNull(ReminderReceiver().also { context.appGraph.inject(it) }.due)
        assertNotNull(ReminderActionReceiver().also { context.appGraph.inject(it) }.actions)
        assertNotNull(BootReceiver().also { context.appGraph.inject(it) }.maintenance)
        assertNotNull(ClockChangeReceiver().also { context.appGraph.inject(it) }.maintenance)
    }

    /** Every class in the app's sources extending [supertype] directly: simple name → qualified name. */
    private fun declaredSubclassesOf(supertype: String): Map<String, String> {
        val declaration = Regex("""\bclass (\w+)((?!\bclass\b)[^{}])*?\)\s*:\s*$supertype\(""")
        return ProjectFiles.sources().flatMap { file ->
            val code = SourceText.code(file.readText())
            val pkg = Regex("""^package ([\w.]+)""", RegexOption.MULTILINE).find(code)?.groupValues?.get(1)
            declaration.findAll(code).map { it.groupValues[1] to "$pkg.${it.groupValues[1]}" }.toList()
        }.toMap()
    }

    private companion object {
        /** Sixteen today; a scan finding far fewer has stopped matching, not found the app smaller. */
        const val MIN_VIEW_MODELS = 10
    }
}
