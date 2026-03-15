package com.privimemobile

import android.app.Application
import com.privimemobile.protocol.SecureStorage

class PriviMWApp : Application() {
    override fun onCreate() {
        super.onCreate()
        SecureStorage.init(this)
    }
}
