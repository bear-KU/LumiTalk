package com.lumitalk.ui.components

import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

@Composable
fun AeLockButton(
    isLocked: Boolean,
    onLock: () -> Unit,
    onUnlock: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = { if (isLocked) onUnlock() else onLock() },
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isLocked) Color(0xFFFF6B35) else Color(0xFF4CAF50)
        ),
        modifier = modifier
    ) {
        Text(text = if (isLocked) "🔒 AEロック中" else "🔓 AEロック")
    }
}
