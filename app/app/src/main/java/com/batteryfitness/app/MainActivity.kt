package com.batteryfitness.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.batteryfitness.app.ui.BatteryFitnessApp
import com.batteryfitness.app.ui.LanguageController
import com.batteryfitness.app.ui.WithAppLanguage
import com.batteryfitness.app.ui.readSavedLanguage
import com.batteryfitness.app.ui.theme.BatteryFitnessTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val context = LocalContext.current
            // Starts in English regardless of the device locale; the user
            // switches deliberately. See ui/Localization.kt for why.
            val languages = remember { LanguageController(readSavedLanguage(context)) }
            val language by languages.current

            WithAppLanguage(language) {
                BatteryFitnessTheme {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background,
                    ) {
                        BatteryFitnessApp(languages)
                    }
                }
            }
        }
    }
}
