package io.github.merytech1.tvshields

import android.app.Application
import io.github.merytech1.tvshields.lists.ListUpdateWorker

class TvShieldsApplication : Application() {

    /** Manual dependency injection (architecture §3.1). */
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.engineLoader.start()
        ListUpdateWorker.schedule(this)
    }
}
