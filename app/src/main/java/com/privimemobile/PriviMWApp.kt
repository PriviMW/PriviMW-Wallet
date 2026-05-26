package com.privimemobile

import android.app.Application
import com.privimemobile.protocol.SecureStorage

class PriviMWApp : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
        SecureStorage.init(this)
    }

    companion object {
        @Volatile
        lateinit var instance: PriviMWApp
            private set
    }
}
