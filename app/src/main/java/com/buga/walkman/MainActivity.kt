package com.buga.walkman

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.buga.walkman.ui.navigation.WalkmanApp
import com.buga.walkman.ui.theme.WalkmanTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WalkmanTheme {
                WalkmanApp()
            }
        }
    }
}
