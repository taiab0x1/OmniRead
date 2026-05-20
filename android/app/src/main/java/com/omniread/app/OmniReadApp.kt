package com.omniread.app

import android.app.Application
import com.omniread.app.util.AdManager
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class OmniReadApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AdManager.initialize(this)
    }
}
