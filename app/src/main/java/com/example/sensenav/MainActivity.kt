package com.example.sensenav

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.sensenav.ui.screens.SenseNavApp
import com.example.sensenav.ui.theme.SenseNavTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SenseNavTheme {
                SenseNavApp()
            }
        }
    }
}
