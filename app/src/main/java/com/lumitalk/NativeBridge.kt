package com.lumitalk

class NativeBridge {

    external fun add(a: Int, b: Int): Int
    external fun helloFromCpp(): String

    companion object {
        init {
            System.loadLibrary("native-lib")
        }
    }
}
