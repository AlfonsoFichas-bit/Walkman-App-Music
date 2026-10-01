package com.buga.walkman

import android.app.Application
import com.buga.walkman.data.CoverStore

/**
 * Installs process-wide singletons that need a [Context].
 *
 * This is the right place for it: [CoverStore] is used from the widget and from `Song.albumArtUri`,
 * neither of which can rely on `MainActivity` having been created. Registering it on the Application
 * also means the custom-cover lookup works while only the playback service is alive.
 */
class WalkmanApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        CoverStore.install(this)
    }
}
