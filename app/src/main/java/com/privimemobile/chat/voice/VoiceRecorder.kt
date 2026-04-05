package com.privimemobile.chat.voice

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Voice recorder matching Telegram-X style:
 * - 48kHz mono 16-bit PCM via AudioRecord
 * - Native Opus encoding via JNI (32kbps, OGG container)
 * - Live amplitude callback for waveform (every ~57ms)
 * - 150ms start delay
 * - 700ms minimum duration (cancel if shorter)
 */
class VoiceRecorder(
    private val context: Context,
    private val amplitudeCallback: ((Int) -> Unit)? = null,
    private val onMaxDurationReached: (() -> Unit)? = null,
) {
    companion object {
        const val SAMPLE_RATE = 48000
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        const val MIN_DURATION_MS = 700L
        const val START_DELAY_MS = 150L
        const val AMPLITUDE_FRAME_DELAY_MS = 57L

        // Max duration to stay under SBBS inline size limit (~750KB after base64)
        // 32kbps Opus: ~42KB/sec after base64. 2 min = ~5MB raw but ~700KB after compression+base64
        const val MAX_DURATION_MS = 120_000L  // 2 minutes

        // Buffer size: 960 samples = 20ms at 48kHz (Opus frame size)
        const val FRAME_SIZE = 960

        /** Pack amplitude list to Telegram-style waveform bytes (packWaveform5Bit) */
        @JvmStatic
        fun packWaveform(amps: List<Int>): ByteArray {
            if (amps.isEmpty()) return byteArrayOf()
            // Downsample to 100 values
            val step = amps.size / 100.0
            val packed = ByteArray(100)
            for (i in 0 until 100) {
                val idx = (step * i).toInt().coerceAtMost(amps.size - 1)
                packed[i] = (amps[idx].coerceIn(0, 31)).toByte()
            }
            return packed
        }
    }

    private var audioRecord: AudioRecord? = null
    private var recordingThread: Thread? = null
    private val isRecording = AtomicBoolean(false)

    /** True when the recording loop should break but AudioRecord stays alive for resume */
    private var isPaused = AtomicBoolean(false)
    /** True when the audio capture is currently capturing (vs paused between AudioRecord stop/start) */
    private var isCapturing = AtomicBoolean(false)

    /** Total accumulated recording time in ms (excludes pause gaps) */
    private var cumulativeDurationMs: Long = 0
    /** Wall-clock mark when the current recording segment started */
    private var segmentStartMs: Long = 0

    var outputFile: File? = null
    private var firstStartTimeMs: Long = 0
    private val amplitudeList = mutableListOf<Int>()

    private val mainHandler = Handler(Looper.getMainLooper())
    private val jni = VoiceRecorderJni

    /**
     * Recording result returned after stop()
     */
    data class RecordingResult(
        val file: File,
        val waveform: ByteArray,
        val durationMs: Long,
        val mimeType: String = "audio/ogg",  // OGG/Opus
    )

    /**
     * Start recording. Returns output file path, or null on error.
     */
    fun start(): File? {
        if (isRecording.getAndSet(true)) {
            return null // Already recording
        }

        val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
            .coerceAtLeast(FRAME_SIZE * 2)

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )
        } catch (e: SecurityException) {
            isRecording.set(false)
            return null
        }

        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            audioRecord?.release()
            audioRecord = null
            isRecording.set(false)
            return null
        }

        // Create temp output file (OGG/Opus)
        outputFile = File(context.cacheDir, "voice_${System.currentTimeMillis()}.ogg")
        amplitudeList.clear()
        cumulativeDurationMs = 0
        segmentStartMs = SystemClock.elapsedRealtime()
        firstStartTimeMs = segmentStartMs

        // Start native encoder
        val result = jni.nativeStartRecording(outputFile!!.absolutePath)
        if (result < 0) {
            audioRecord?.release()
            audioRecord = null
            isRecording.set(false)
            outputFile = null
            return null
        }

        audioRecord?.startRecording()

        // Start recording thread
        recordingThread = Thread {
            recordLoopNative()
        }.apply { start() }

        return outputFile
    }

    /**
     * Recording loop using native Opus encoder.
     */
    private fun recordLoopNative() {
        val buffer = ShortArray(FRAME_SIZE)
        var lastAmplitudeTime = SystemClock.elapsedRealtime()

        while (isRecording.get()) {
            // If paused, just sleep and wait for resume flag
            if (isPaused.get()) {
                Thread.sleep(50)
                continue
            }

            val elapsed = (cumulativeDurationMs + (SystemClock.elapsedRealtime() - segmentStartMs))

            // Check max duration - auto-stop to stay under size limit
            if (elapsed >= MAX_DURATION_MS) {
                mainHandler.post {
                    onMaxDurationReached?.invoke()
                }
                break
            }

            val readCount = audioRecord?.read(buffer, 0, FRAME_SIZE) ?: 0

            if (readCount > 0) {
                // Encode frame via JNI
                jni.nativeEncodeFrame(buffer, readCount)

                // Calculate RMS amplitude for waveform
                val rms = calculateRms(buffer, readCount)
                val now = SystemClock.elapsedRealtime()

                // Dispatch amplitude every ~57ms
                if (now - lastAmplitudeTime >= AMPLITUDE_FRAME_DELAY_MS) {
                    lastAmplitudeTime = now
                    amplitudeList.add(rms)
                    mainHandler.post {
                        amplitudeCallback?.invoke(rms)
                    }
                }
            }
        }
    }

    private fun calculateRms(buffer: ShortArray, count: Int): Int {
        if (count == 0) return 0
        var sum = 0L
        for (i in 0 until count) {
            sum += buffer[i].toInt() * buffer[i].toInt()
        }
        val rms = kotlin.math.sqrt(sum.toDouble() / count).toInt()
        // Normalize to 0-100 range for amplitude display
        return (rms / 327.67).toInt().coerceIn(0, 100)
    }

    /**
     * Stop recording and return result. Returns null if duration < MIN_DURATION_MS.
     */
    fun stop(): RecordingResult? {
        if (!isRecording.getAndSet(false)) {
            // If not recording but paused, finalize the paused session
            return if (isPaused.get()) {
                finalizePaused()
            } else {
                null
            }
        }

        isPaused.set(false)
        val duration = cumulativeDurationMs + (SystemClock.elapsedRealtime() - segmentStartMs)

        // Stop audio record (may already be stopped if paused)
        try { audioRecord?.stop() } catch (_: IllegalStateException) { }
        audioRecord?.release()
        audioRecord = null
        isCapturing.set(false)

        // Wait for recording thread to finish
        recordingThread?.join(1000)
        recordingThread = null

        // Finalize OGG file via JNI
        jni.nativeStopRecording()

        // Cancel if too short
        if (duration < MIN_DURATION_MS) {
            outputFile?.delete()
            return null
        }

        val file = outputFile ?: return null
        val waveform = packWaveform(amplitudeList)

        return RecordingResult(
            file = file,
            waveform = waveform,
            durationMs = duration,
        )
    }

    /**
     * Cancel recording (discard file).
     */
    fun cancel() {
        if (!isRecording.getAndSet(false)) {
            return
        }

        isPaused.set(false)

        try { audioRecord?.stop() } catch (_: IllegalStateException) { }
        audioRecord?.release()
        audioRecord = null
        isCapturing.set(false)

        recordingThread?.join(500)
        recordingThread = null

        // Cancel via JNI
        jni.nativeCancelRecording()

        // Delete temp file
        outputFile?.delete()
        outputFile = null
        amplitudeList.clear()
        cumulativeDurationMs = 0
    }

    /**
     * Pause recording: stops the AudioRecord capture and pauses the recording loop,
     * but keeps the encoder and file open so resume() can continue.
     */
    fun pause(): Boolean {
        if (!isRecording.get()) return false
        if (!isPaused.compareAndSet(false, true)) return false

        // Capture the time spent on this segment BEFORE pausing
        cumulativeDurationMs += (SystemClock.elapsedRealtime() - segmentStartMs)

        // Stop AudioRecord capture but keep the object alive for resume
        try { audioRecord?.stop() } catch (_: IllegalStateException) { }
        isCapturing.set(false)

        // Recording loop will see isPaused=true on its next iteration (no sleep needed)
        // Do NOT call nativeStopRecording() — keep the OGG stream open
        return true
    }

    /**
     * Resume recording: restarts AudioRecord capture and clears pause flag.
     * Continues appending frames to the same OGG file.
     */
    fun resume(): Boolean {
        if (!isRecording.get()) return false
        if (!isPaused.compareAndSet(true, false)) return false

        // Start new timing segment
        segmentStartMs = SystemClock.elapsedRealtime()

        // Restart AudioRecord capture (object was never released)
        val rec = audioRecord
        if (rec != null) {
            try { rec.startRecording() } catch (_: IllegalStateException) { return false }
            isCapturing.set(true)
        } else {
            // AudioRecord was somehow released — fall back to a new start
            isPaused.set(true) // go back to paused since we can't resume
            return false
        }

        // Recording loop will see isPaused=false and resume encoding
        return true
    }

    /**
     * Check if currently paused.
     */
    fun isRecordingPaused(): Boolean = isPaused.get()

    /**
     * Finalize a paused recording (called when user chooses to send from preview).
     */
    private fun finalizePaused(): RecordingResult? {
        isPaused.set(false)

        // AudioRecord was already stopped in pause(), just release it
        try { audioRecord?.stop() } catch (_: IllegalStateException) { }
        audioRecord?.release()
        audioRecord = null
        isCapturing.set(false)

        // Wait for thread to stop
        Thread.sleep(200)

        // Finalize OGG file
        jni.nativeStopRecording()

        val duration = cumulativeDurationMs // already accumulated in pause()
        val file = outputFile ?: return null
        val waveform = packWaveform(amplitudeList)

        return RecordingResult(
            file = file,
            waveform = waveform,
            durationMs = duration,
        )
    }

    /**
     * Get current recording duration in seconds.
     */
    fun getDurationSeconds(): Int {
        if (!isRecording.get()) return 0
        return (getDurationMs() / 1000).toInt()
    }

    /**
     * Get current cumulative duration in milliseconds.
     */
    fun getDurationMs(): Long {
        if (!isRecording.get() && !isPaused.get()) return 0
        val segment = if (isPaused.get()) 0L else (SystemClock.elapsedRealtime() - segmentStartMs)
        return cumulativeDurationMs + segment
    }

    /**
     * Check if currently recording.
     */
    fun isRecording(): Boolean = isRecording.get()

    /**
     * Check if recording is paused (paused, not stopped).
     */
    fun isPaused(): Boolean = isPaused.get()

    /**
     * Get live amplitudes for waveform preview.
     */
    fun getAmplitudes(): List<Int> = amplitudeList.toList()
}