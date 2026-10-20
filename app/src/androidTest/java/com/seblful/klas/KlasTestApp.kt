package com.seblful.klas

import android.app.Application
import androidx.work.Configuration
import com.seblful.klas.di.AppGraph
import com.seblful.klas.di.KlasGraph
import dev.zacsweers.metro.createGraphFactory

class KlasTestApp : Application(), Configuration.Provider, AppGraph.Holder {

    override val graph: AppGraph by lazy { createGraphFactory<KlasGraph.Factory>().create(this) }

    override val workManagerConfiguration: Configuration get() = Configuration.Builder().build()
}
