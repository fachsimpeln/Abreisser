package io.github.fachsimpeln.abreisser

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import io.github.fachsimpeln.abreisser.ui.AbreisserApp
import io.github.fachsimpeln.abreisser.ui.AbreisserTheme
import io.github.fachsimpeln.abreisser.ui.FlyerViewModel

class MainActivity : AppCompatActivity() {
    private val vm: FlyerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            AbreisserTheme {
                AbreisserApp(vm)
            }
        }
    }
}
