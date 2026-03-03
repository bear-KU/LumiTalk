package com.lumitalk.ui.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.lumitalk.util.CameraConfig

@Composable
fun CameraModeButton(
    currentConfig: CameraConfig,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Black.copy(alpha = 0.6f)
        ),
        modifier = modifier
    ) {
        Text(
            text = currentConfig.label,
            color = Color.White
        )
    }
}
