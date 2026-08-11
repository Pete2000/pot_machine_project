package com.example.plccontroller

import android.graphics.Color
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.plccontroller.ui.PlcControllerTheme
import com.example.plccontroller.ui.SecondaryDisplayScreen
import com.example.plccontroller.ui.SecondaryDisplayViewModel

class SecondaryDisplayPreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val appContainer = (application as PlcControllerApplication).appContainer
        configureWindow()
        setContent {
            PlcControllerTheme {
                val viewModel: SecondaryDisplayViewModel =
                    viewModel(
                        factory = SecondaryDisplayViewModel.factory(appContainer),
                    )
                SecondaryDisplayScreen(
                    viewModel = viewModel,
                    showDebugTools = true,
                )
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            configureWindow()
        }
    }

    private fun configureWindow() {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    override fun dispatchGenericMotionEvent(ev: android.view.MotionEvent): Boolean =
        try {
            super.dispatchGenericMotionEvent(ev)
        } catch (e: IllegalStateException) {
            android.util.Log.w("HoverGuard", "Swallowed ACTION_HOVER_EXIT exception to prevent crash", e)
            true
        }
}
