package com.privimemobile.chat.voice

/**
 * JNI bridge to native Opus encoder.
 *
 * Uses libopus + libogg to encode PCM audio to OGG/Opus format.
 * This produces smaller files than AAC at the same quality (32kbps Opus ≈ 64kbps AAC).
 */
object VoiceRecorderJni {

    init {
        System.loadLibrary("voice")
    }

    /**
     * Start recording to a file.
     * @param path Output file path (should end with .ogg)
     * @return 0 on success, negative on error
     */
    external fun nativeStartRecording(path: String): Int

    /**
     * Encode a frame of PCM audio.
     * @param samples Short array of PCM samples (mono, 48kHz)
     * @param frameSize Number of samples (typically 960 for 20ms)
     * @return Bytes written, negative on error
     */
    external fun nativeEncodeFrame(samples: ShortArray, frameSize: Int): Int

    /**
     * Stop recording and finalize the OGG file.
     * @return Waveform data (currently null, waveform computed in Kotlin)
     */
    external fun nativeStopRecording(): ByteArray?

    /**
     * Cancel recording and discard file.
     */
    external fun nativeCancelRecording()

    // Track if recording is active
    private var isRecording = false
    private var outputPath: String? = null

    /**
     * Start recording with validation.
     */
    fun start(path: String): Boolean {
        if (isRecording) return false
        val result = nativeStartRecording(path)
        if (result == 0) {
            isRecording = true
            outputPath = path
            return true
        }
        return false
    }

    /**
     * Encode a frame with validation.
     */
    fun encode(samples: ShortArray, frameSize: Int = FRAME_SIZE): Int {
        if (!isRecording) return -1
        return nativeEncodeFrame(samples, frameSize)
    }

    /**
     * Stop and return waveform.
     */
    fun stop(): ByteArray? {
        if (!isRecording) return null
        isRecording = false
        val result = nativeStopRecording()
        outputPath = null
        return result
    }

    /**
     * Cancel and cleanup.
     */
    fun cancel() {
        if (!isRecording) return
        nativeCancelRecording()
        isRecording = false
        outputPath = null
    }

    const val SAMPLE_RATE = 48000
    const val CHANNELS = 1
    const val FRAME_SIZE = 960  // 20ms at 48kHz
}