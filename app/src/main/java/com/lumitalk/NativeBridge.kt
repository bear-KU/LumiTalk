package com.lumitalk

class NativeBridge {
    external fun generateSignalSequence(data: String, T: Int): IntArray
    external fun processFrame(frameData: ByteArray, width: Int, height: Int): String

    companion object {
        init {
            System.loadLibrary("native-send")
            System.loadLibrary("native-receive")
        }
    }
}
