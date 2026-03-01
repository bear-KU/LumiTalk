package com.lumitalk.ui.components

import android.graphics.SurfaceTexture
import android.view.Surface
import android.view.TextureView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.lumitalk.util.CameraState

@Composable
fun CameraPreview(
    cameraState: CameraState,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            TextureView(ctx).apply {
                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(
                        surface: SurfaceTexture,
                        width: Int,
                        height: Int
                    ) {
                        surface.setDefaultBufferSize(width, height)
                        cameraState.openCamera(
                            context,
                            Surface(surface),
                            width,
                            height
                        )
                    }

                    override fun onSurfaceTextureSizeChanged(
                        surface: SurfaceTexture, width: Int, height: Int) {}

                    override fun onSurfaceTextureDestroyed(
                        surface: SurfaceTexture): Boolean {
                        cameraState.closeCamera()
                        return true
                    }

                    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
                }
            }
        }
    )
}
