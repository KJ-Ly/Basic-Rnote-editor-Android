package com.rnote.baby.bridge


object RnoteNativeBridge {

    private var isNativeLibraryLoaded = false

    init {
        try {
            System.loadLibrary("rnote_engine_android")
            isNativeLibraryLoaded = true
        } catch (e: UnsatisfiedLinkError) {
            isNativeLibraryLoaded = false
        }
    }

    fun isNativeEngineAvailable(): Boolean = isNativeLibraryLoaded

    /**
     * Native bridge method signatures for future Rust rnote-engine binary calls.
     */
    external fun nativeInitEngine(): Long
    external fun nativeProcessStrokes(engineHandle: Long, documentJson: String): String
    external fun nativeExportSvg(engineHandle: Long, documentJson: String): String
}
