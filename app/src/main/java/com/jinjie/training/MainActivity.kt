package com.jinjie.training

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repository = TrainingRepository(applicationContext)
        repository.startDate
        setContent { TrainingApp(repository) }
    }
}
