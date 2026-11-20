package com.rustcraft.launcher

object NativeBridge {
    init {
        System.loadLibrary("rustcraft_core")
    }

    external fun execute(request: String): String
}
