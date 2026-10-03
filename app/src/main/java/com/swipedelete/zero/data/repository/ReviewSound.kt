package com.swipedelete.zero.data.repository

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.swipedelete.zero.domain.feedback.ActionFeedback
import com.swipedelete.zero.domain.feedback.ReviewFeedback
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.PI
import kotlin.math.sin

/** Original 90ms sine chimes, synthesized locally; no downloaded sound or network. */
@Singleton
class ReviewSound @Inject constructor(@ApplicationContext context: Context) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
    private var lastPlayedAt = -1000L
    private val preferences = context.getSharedPreferences("review_feedback", Context.MODE_PRIVATE)
    private val _enabled = MutableStateFlow(preferences.getBoolean("sound_enabled", true))
    val enabled = _enabled.asStateFlow()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var track: AudioTrack? = null
    private var foreground = false
    private val feedback = ActionFeedback({ _enabled.value }, ::play)

    fun setEnabled(value: Boolean) {
        preferences.edit().putBoolean("sound_enabled", value).apply()
        _enabled.value = value
        if (!value) { track?.release(); track = null }
    }

    fun setForeground(active: Boolean) {
        foreground = active
        if (!active) { track?.release(); track = null }
    }

    suspend fun afterCommit(event: ReviewFeedback, canPlay: () -> Boolean = { true }, operation: suspend () -> Unit) {
        operation()
        if (foreground && canPlay()) feedback.committed(event)
    }

    private fun play(event: ReviewFeedback) {
        val now = android.os.SystemClock.elapsedRealtime()
        if (!foreground || now - lastPlayedAt < 180 || audioManager.isMusicActive ||
            audioManager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC) == 0) return
        val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
        val focus = android.media.AudioFocusRequest.Builder(android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(attributes).setOnAudioFocusChangeListener { change ->
                if (change < 0) { track?.release(); track = null }
            }.build()
        if (audioManager.requestAudioFocus(focus) != android.media.AudioManager.AUDIOFOCUS_REQUEST_GRANTED) return
        lastPlayedAt = now
        // A tiny ascending harmonic gesture; no default platform notification tones.
        val samples = ShortArray(1984) { index ->
            val t = index / 22050.0
            val envelope = sin(PI * index / 1984).let { it * it }
            val frequency = event.frequencyHz * (1.0 + 0.15 * index / 1984)
            ((sin(2 * PI * frequency * t) + 0.18 * sin(4 * PI * frequency * t)) * envelope * 2800).toInt().toShort()
        }
        track?.release()
        val audio = try { AudioTrack.Builder()
            .setAudioAttributes(attributes)
            .setAudioFormat(AudioFormat.Builder().setSampleRate(22050)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setTransferMode(AudioTrack.MODE_STATIC).setBufferSizeInBytes(samples.size * 2).build()
        } catch (error: Exception) { audioManager.abandonAudioFocusRequest(focus); throw error }
        track = audio
        try { audio.write(samples, 0, samples.size); audio.play() }
        catch (error: Exception) { audio.release(); if (track === audio) track = null; audioManager.abandonAudioFocusRequest(focus); throw error }
        scope.launch { delay(150); if (track === audio) { audio.release(); track = null }; audioManager.abandonAudioFocusRequest(focus) }
    }
}
