package com.example.billiardtracker

import android.app.Application
import com.example.billiardtracker.di.AppContainer

class BilliardApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
