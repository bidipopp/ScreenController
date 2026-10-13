package com.immensive.screencontroller

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.lifecycle.viewmodel.compose.viewModel
import com.immensive.screencontroller.ui.TestControllerScreen
import com.immensive.screencontroller.ui.TestControllerViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    val vm: TestControllerViewModel = viewModel()
                    TestControllerScreen(
                        state = vm.uiState,
                        onHostChanged = vm::updateHost,
                        onPortChanged = vm::updatePort,
                        onMonitorIdChanged = vm::updateMonitorId,
                        onSendAction = vm::send,
                        onCancelCurrent = vm::cancelCurrent,
                    )
                }
            }
        }
    }
}
