package com.tools.maestro.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import com.tools.maestro.ui.navigation.TOolsNavGraph
import com.tools.maestro.ui.theme.TOolsTheme
import timber.log.Timber

/**
 * Main activity for TOols application.
 * Entry point for Jetpack Compose UI.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Timber.i("MainActivity created")

        // Enable edge-to-edge layout
        enableEdgeToEdge()

        setContent {
            TOolsTheme {
                TOolsNavGraph()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Timber.i("MainActivity destroyed")
    }
}
