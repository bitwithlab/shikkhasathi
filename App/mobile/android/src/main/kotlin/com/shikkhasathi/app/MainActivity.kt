package com.shikkhasathi.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.shikkhasathi.shared.ui.App

/**
 * Single activity hosting the shared Compose UI. App Links (https://shikkhasathi.com/...) arrive here
 * (`singleTask`); routing them to destinations is added with the features they point at.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { App() }
    }
}
