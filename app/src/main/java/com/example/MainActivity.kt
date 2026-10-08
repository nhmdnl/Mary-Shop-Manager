package com.example

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.AppDatabase
import com.example.ui.navigation.MaryShopApp
import com.example.ui.theme.MyApplicationTheme
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as MaryShopApplication
        val settingsManager = app.container.settingsManager

        setContent {
            val settings by settingsManager.settings.collectAsStateWithLifecycle()
            val context = LocalContext.current
            val baseConfig = LocalConfiguration.current

            val targetLocale = remember(settings.appLanguage) {
                when (settings.appLanguage) {
                    "sw" -> Locale("sw")
                    "en" -> Locale("en")
                    else -> Locale.getDefault()
                }
            }

            val localizedConfig = remember(baseConfig, targetLocale) {
                Configuration(baseConfig).apply {
                    setLocale(targetLocale)
                    setLayoutDirection(targetLocale)
                }
            }

            val localizedContext = remember(context, localizedConfig) {
                context.createConfigurationContext(localizedConfig)
            }

            MyApplicationTheme(themeMode = settings.themeMode) {
                CompositionLocalProvider(
                    LocalConfiguration provides localizedConfig,
                    LocalContext provides localizedContext
                ) {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        MaryShopApp()
                    }
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // Ensure Room SQLite WAL transactions are fully flushed to disk when the tablet sleeps or backgrounded
        AppDatabase.checkpointDatabase()
    }
}

/**
 * Kept for test backwards compatibility
 */
@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(text = "Hello $name!", modifier = modifier)
}

