package com.example.plccontroller

import android.app.ActivityOptions
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.graphics.Color
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.view.Display
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.plccontroller.ui.MainScreen
import com.example.plccontroller.ui.MainViewModel
import com.example.plccontroller.ui.PlcControllerTheme

class MainActivity : ComponentActivity() {
    private var chaosReceiver: BroadcastReceiver? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val appContainer = (application as PlcControllerApplication).appContainer
        configureKioskWindow()
        setContent {
            PlcControllerTheme {
                val viewModel: MainViewModel =
                    viewModel(
                        factory = MainViewModel.factory(appContainer),
                    )
                MainScreen(
                    viewModel = viewModel,
                    onOpenSecondaryDisplay = ::openSecondaryDisplayForDebug,
                    onPreviewSecondaryDisplay = ::openSecondaryDisplayPreview,
                )
            }
        }

        chaosReceiver =
            object : BroadcastReceiver() {
                override fun onReceive(
                    context: Context,
                    intent: Intent,
                ) {
                    val faultTypeStr = intent.getStringExtra("type") ?: return
                    when (faultTypeStr) {
                        "DISCONNECT" -> {
                            appContainer.plcController.chaosMonkeyDisconnectActive = true
                            Toast.makeText(context, "Chaos Monkey: Simulated Disconnect Active", Toast.LENGTH_LONG).show()
                        }
                        "RECONNECT" -> {
                            appContainer.plcController.chaosMonkeyDisconnectActive = false
                            Toast.makeText(context, "Chaos Monkey: Reconnected", Toast.LENGTH_LONG).show()
                        }
                        "EMERGENCY_STOP" -> {
                            appContainer.plcController.chaosMonkeyEStopActive = true
                            Toast.makeText(context, "Chaos Monkey: Physical E-Stop Pressed", Toast.LENGTH_LONG).show()
                        }
                        "EMERGENCY_RELEASE" -> {
                            appContainer.plcController.chaosMonkeyEStopActive = false
                            Toast.makeText(context, "Chaos Monkey: Physical E-Stop Released", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        val isDebug = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        if (isDebug) {
            val filter = IntentFilter("com.plc.INJECT_FAULT")
            androidx.core.content.ContextCompat.registerReceiver(
                this,
                chaosReceiver,
                filter,
                androidx.core.content.ContextCompat.RECEIVER_EXPORTED,
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        val isDebug = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        if (isDebug) {
            chaosReceiver?.let { unregisterReceiver(it) }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            configureKioskWindow()
        }
    }

    private fun configureKioskWindow() {
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

    private fun openSecondaryDisplayForDebug() {
        val displayManager = getSystemService(DisplayManager::class.java)
        val secondaryDisplay =
            displayManager
                .getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)
                .firstOrNull()
                ?: displayManager.displays.firstOrNull { it.displayId != Display.DEFAULT_DISPLAY }

        if (secondaryDisplay == null) {
            Toast
                .makeText(
                    this,
                    "未检测到第二显示器，请先在模拟器 Displays 中添加副屏。",
                    Toast.LENGTH_LONG,
                ).show()
            return
        }

        val intent =
            Intent(this, SecondaryDisplayActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        val options =
            ActivityOptions.makeBasic().apply {
                launchDisplayId = secondaryDisplay.displayId
            }
        startActivity(intent, options.toBundle())
        Toast
            .makeText(
                this,
                "副屏调试已启动到显示器 ${secondaryDisplay.displayId}",
                Toast.LENGTH_SHORT,
            ).show()
    }

    private fun openSecondaryDisplayPreview() {
        startActivity(Intent(this, SecondaryDisplayPreviewActivity::class.java))
    }

    override fun dispatchGenericMotionEvent(ev: android.view.MotionEvent): Boolean =
        try {
            super.dispatchGenericMotionEvent(ev)
        } catch (e: IllegalStateException) {
            android.util.Log.w("HoverGuard", "Swallowed ACTION_HOVER_EXIT exception to prevent crash", e)
            true
        }
}
