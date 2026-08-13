package com.qrzzzz.lyricscard

import android.app.Application

class LyricsCardApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = DefaultAppContainer(this).also(AppContainer::start)
    }

    override fun onTerminate() {
        container.close()
        super.onTerminate()
    }
}
