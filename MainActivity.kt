package com.aurora.media

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aurora.media.ui.AuroraApp
import com.aurora.media.ui.AuroraViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val model: AuroraViewModel = viewModel()
            AuroraApp(model)
        }
    }
}
