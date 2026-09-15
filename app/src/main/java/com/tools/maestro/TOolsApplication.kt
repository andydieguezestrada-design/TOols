package com.tools.maestro

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

/**
 * Application class for TOols IDE.
 * Sets up Hilt dependency injection and Timber logging.
 */
@HiltAndroidApp
class TOolsApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Initialize Timber logging
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        } else {
            Timber.plant(ReleaseTree())
        }

        Timber.i("TOols application initialized")
    }

    /**
     * Custom tree that omits sensitive information in release builds.
     */
    private class ReleaseTree : Timber.Tree() {
        override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
            // In release, only log important errors
            if (priority >= android.util.Log.WARN) {
                super.log(priority, tag, message, t)
            }
        }
    }
}
