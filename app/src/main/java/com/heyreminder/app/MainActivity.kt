package com.heyreminder.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.heyreminder.app.ui.HomeRoute
import com.heyreminder.app.ui.theme.HeyReminderTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HeyReminderTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    HomeRoute()
                }
            }
        }
    }
}

