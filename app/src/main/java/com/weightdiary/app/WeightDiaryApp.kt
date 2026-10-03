package com.weightdiary.app

import android.app.Application
import com.weightdiary.app.di.AppContainer

class WeightDiaryApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
