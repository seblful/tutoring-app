package com.seblful.klas

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner

class KlasTestRunner : AndroidJUnitRunner() {
    override fun newApplication(cl: ClassLoader?, name: String?, context: Context?): Application =
        super.newApplication(cl, KlasTestApp::class.java.name, context)
}
