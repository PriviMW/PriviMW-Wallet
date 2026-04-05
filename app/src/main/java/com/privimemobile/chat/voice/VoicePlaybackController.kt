package com.privimemobile.chat.voice

import android.content.Context
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import java.io.File

/**
 * Singleton managing voice message playback.
 * - One active playback at a time
 * - Progress updates every 100ms
 * - Speed control (1x, 2x, 3x)
 * - Play/pause/seek/resume
 */
object VoicePlaybackController {

    private var mediaPlayer: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())

    private val _isPlaying = mutableStateOf(false)
    val isPlaying: State<Boolean> = _isPlaying

    private val _currentId = mutableStateOf<String?>(null)
    val currentId: State<String?> = _currentId

    private val _progress = mutableStateOf(0f)
    val progress: State<Float> = _progress

    private val _durationMs = mutableStateOf(0L)
    val durationMs: State<Long> = _durationMs

    private val _positionMs = mutableStateOf(0L)
    val positionMs: State<Long> = _positionMs

    private val _speed = mutableStateOf(1f)
    val speed: State<Float> = _speed

    private var currentFile: File? = null
    private var preparedFile: File? = null

    private val progressRunnable = object : Runnable {
        override fun run() {
            mediaPlayer?.let { player ->
                if (player.isPlaying) {
                    val duration = player.duration.toLong()
                    val position = player.currentPosition.toLong()
                    if (duration > 0) {
                        _progress.value = position.toFloat() / duration.toFloat()
                        _positionMs.value = position
                    }
                }
            }
            handler.postDelayed(this, 100)
        }
    }

    /**
     * Play a voice file. Stops any current playback.
     *
     * @param id Unique identifier for the message
     * @param file Audio file to play
     * @param startPosition Progress position 0-1 to start from
     */
    fun play(
        id: String,
        file: File,
        waveform: ByteArray? = null,
        startPosition: Float = 0f
    ) {
        // Stop current playback if different file
        if (_currentId.value != id) {
            stop()
        }

        // If paused, resume
        if (_currentId.value == id && preparedFile == file && !_isPlaying.value) {
            resume()
            return
        }

        // Prepare new file
        try {
            currentFile = file
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                setOnPreparedListener {
                    _durationMs.value = it.duration.toLong()
                    _currentId.value = id
                    preparedFile = file

                    // Apply speed if not 1x
                    if (_speed.value != 1f) {
                        setPlaybackParams(it.playbackParams.setSpeed(_speed.value))
                    }

                    // Seek to start position if specified
                    if (startPosition > 0f) {
                        val seekMs = (it.duration * startPosition).toInt()
                        it.seekTo(seekMs)
                        _positionMs.value = seekMs.toLong()
                        _progress.value = startPosition
                    }

                    it.start()
                    _isPlaying.value = true
                    handler.post(progressRunnable)
                }
                setOnCompletionListener {
                    _isPlaying.value = false
                    _progress.value = 1f
                    _positionMs.value = it.duration.toLong()
                    handler.removeCallbacks(progressRunnable)
                }
                setOnErrorListener { _, _, _ ->
                    _isPlaying.value = false
                    handler.removeCallbacks(progressRunnable)
                    true
                }
                prepareAsync()
            }
        } catch (e: Exception) {
            _isPlaying.value = false
            _currentId.value = null
        }
    }

    /**
     * Pause playback.
     */
    fun pause() {
        mediaPlayer?.let { player ->
            if (player.isPlaying) {
                player.pause()
                _isPlaying.value = false
            }
        }
    }

    /**
     * Resume paused playback.
     */
    fun resume() {
        mediaPlayer?.let { player ->
            if (!player.isPlaying && preparedFile != null) {
                player.start()
                _isPlaying.value = true
                handler.post(progressRunnable)
            }
        }
    }

    /**
     * Stop playback and reset state.
     */
    fun stop() {
        handler.removeCallbacks(progressRunnable)
        mediaPlayer?.let { player ->
            if (player.isPlaying) {
                player.stop()
            }
            player.release()
        }
        mediaPlayer = null
        currentFile = null
        preparedFile = null
        _isPlaying.value = false
        _currentId.value = null
        _progress.value = 0f
        _positionMs.value = 0
        _durationMs.value = 0
    }

    /**
     * Seek to position (0-1 range).
     */
    fun seekTo(progress: Float) {
        mediaPlayer?.let { player ->
            val position = (player.duration * progress.coerceIn(0f, 1f)).toInt()
            player.seekTo(position)
            _progress.value = progress
            _positionMs.value = position.toLong()
        }
    }

    /**
     * Set playback speed (1x, 2x, 3x).
     */
    fun setSpeed(speed: Float) {
        val validSpeed = when {
            speed <= 1f -> 1f
            speed <= 2f -> 2f
            else -> 3f
        }
        _speed.value = validSpeed
        mediaPlayer?.let { player ->
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                try {
                    player.playbackParams = player.playbackParams.setSpeed(validSpeed)
                } catch (e: Exception) {
                    // Some devices don't support speed change
                }
            }
        }
    }

    /**
     * Cycle through speeds: 1x -> 2x -> 3x -> 1x
     */
    fun cycleSpeed() {
        val next = when (_speed.value) {
            1f -> 2f
            2f -> 3f
            else -> 1f
        }
        setSpeed(next)
    }

    /**
     * Check if a specific ID is currently playing.
     */
    fun isPlaying(id: String): Boolean = _isPlaying.value && _currentId.value == id

    /**
     * Toggle play/pause for a specific file.
     */
    fun toggle(id: String, file: File, waveform: ByteArray? = null) {
        if (isPlaying(id)) {
            pause()
        } else if (_currentId.value == id) {
            resume()
        } else {
            play(id, file, waveform)
        }
    }

    /**
     * Format duration as mm:ss
     */
    fun formatDuration(ms: Long): String {
        val totalSeconds = (ms / 1000).toInt()
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format("%d:%02d", minutes, seconds)
    }

    /**
     * Format position and duration as "0:00 / 1:23"
     */
    fun formatPositionDuration(positionMs: Long, durationMs: Long): String {
        return "${formatDuration(positionMs)} / ${formatDuration(durationMs)}"
    }
}