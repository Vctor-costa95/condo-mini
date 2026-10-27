package com.example.condo_mini

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.condo_mini.ui.navigation.AppNavigation
import com.example.condo_mini.ui.theme.CondominiTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CondominiTheme {
                AppNavigation()
            }
        }
    }
}
