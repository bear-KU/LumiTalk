package com.lumitalk.util.camera

enum class CameraMode {
    STANDARD,
    HIGH_SPEED
}

data class CameraConfig(
    val width: Int,
    val height: Int,
    val fps: Int,
    val mode: CameraMode
) {
    val label: String get() = "${fps}fps ${width}x${height}"
}

val STANDARD_CONFIG = CameraConfig(640, 480, 30, CameraMode.STANDARD)
val HIGH_SPEED_CONFIG = CameraConfig(1920, 1080, 240, CameraMode.HIGH_SPEED)
