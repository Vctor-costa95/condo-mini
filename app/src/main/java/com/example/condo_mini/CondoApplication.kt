package com.example.condo_mini

import android.app.Application
import com.example.condo_mini.data.AppContainer

class CondoApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppContainer.init(this)
    }
}
