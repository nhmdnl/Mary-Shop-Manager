package com.example

import android.app.Application
import com.example.di.AppContainer
import com.example.di.DefaultAppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MaryShopApplication : Application() {
    lateinit var container: AppContainer
        private set

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        container = DefaultAppContainer(this)

        // Seed initial data if empty so emulator testing works out-of-the-box
        applicationScope.launch {
            try {
                container.shopRepository.seedSampleDataIfEmpty()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
