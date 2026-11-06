package com.example.condo_mini

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.example.condo_mini.data.AppContainer
import com.example.condo_mini.push.PushNotifications
import com.example.condo_mini.ui.theme.ThemePreference

class CondoApplication : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        AppContainer.init(this)
        ThemePreference.init(this)
        PushNotifications.init(this)
    }

    /** As fotos da API exigem o token da sessão: o Coil usa o mesmo cliente HTTP. */
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .apply { AppContainer.apiClient?.let { okHttpClient(it.http) } }
        .build()
}
