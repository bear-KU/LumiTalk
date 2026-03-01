package com.lumitalk

class NativeBridge {
    external fun generateSignalSequence(data: String, T: Int): IntArray

    companion object {
        init {
            System.loadLibrary("native-lib")
        }
    }
}
