package com.buga.walkman

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.buga.walkman.ui.navigation.WalkmanApp
import com.buga.walkman.ui.theme.WalkmanTheme

class MainActivity : ComponentActivity() {

    var pendingOpenPlayer by mutableStateOf(false)
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingOpenPlayer = intent.getBooleanExtra(EXTRA_OPEN_PLAYER, false)
        enableEdgeToEdge()
        setContent {
            WalkmanTheme {
                WalkmanApp()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingOpenPlayer = intent.getBooleanExtra(EXTRA_OPEN_PLAYER, false)
    }

    fun consumeOpenPlayer() {
        pendingOpenPlayer = false
    }

    companion object {
        const val EXTRA_OPEN_PLAYER = "com.buga.walkman.widget.OPEN_PLAYER"
    }
}