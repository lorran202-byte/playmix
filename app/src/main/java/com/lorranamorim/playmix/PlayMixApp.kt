package com.lorranamorim.playmix

import android.app.Application
import com.lorranamorim.playmix.data.LibraryStore
import com.lorranamorim.playmix.data.LyricsRepository
import com.lorranamorim.playmix.data.MusicRepository
import com.lorranamorim.playmix.playback.PlayerConnection

class AppContainer(app: Application) {
    val store = LibraryStore(app)
    val music = MusicRepository(app, store)
    val lyrics = LyricsRepository(app)
    val player: PlayerConnection by lazy { PlayerConnection(app) }
}

class PlayMixApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
