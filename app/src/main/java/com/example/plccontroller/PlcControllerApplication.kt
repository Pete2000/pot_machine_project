package com.example.plccontroller

import android.app.Application

class PlcControllerApplication : Application() {
    val appContainer: AppContainer by lazy { AppContainer(this) }
}
