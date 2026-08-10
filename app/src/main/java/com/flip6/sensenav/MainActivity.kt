package com.flip6.sensenav

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.flip6.sensenav.ui.screens.SenseNavApp
import com.flip6.sensenav.ui.theme.SenseNavTheme

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
