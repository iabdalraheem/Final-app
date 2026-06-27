package com.example.chess

import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlin.math.sin

object ChessSoundPlayer {

    private const val SAMPLE_RATE = 22050

    fun playNormalMove() {
        GlobalScope.launch(Dispatchers.Default) {
            val durationMs = 80
            val bufferSize = SAMPLE_RATE * durationMs / 1000
            val buffer = ShortArray(bufferSize)
            for (i in 0 until bufferSize) {
                val t = i.toDouble() / SAMPLE_RATE
                val envelope = (1.0 - (i.toDouble() / bufferSize))
                // A quick wooden pop/click
                val sampleValue = (sin(2.0 * Math.PI * 550.0 * t) * 32767.0 * 0.4 * envelope).toInt()
                buffer[i] = sampleValue.coerceIn(-32768, 32767).toShort()
            }
            playBuffer(buffer)
        }
    }

    fun playCapture() {
        GlobalScope.launch(Dispatchers.Default) {
            val durationMs = 120
            val bufferSize = SAMPLE_RATE * durationMs / 1000
            val buffer = ShortArray(bufferSize)
            for (i in 0 until bufferSize) {
                val t = i.toDouble() / SAMPLE_RATE
                val envelope = (1.0 - (i.toDouble() / bufferSize))
                // A crisp, lower snap sound
                val sampleValue = (sin(2.0 * Math.PI * 320.0 * t) * 32767.0 * 0.55 * envelope).toInt()
                buffer[i] = sampleValue.coerceIn(-32768, 32767).toShort()
            }
            playBuffer(buffer)
        }
    }

    fun playCheck() {
        GlobalScope.launch(Dispatchers.Default) {
            val durationMs = 240
            val bufferSize = SAMPLE_RATE * durationMs / 1000
            val buffer = ShortArray(bufferSize)
            for (i in 0 until bufferSize) {
                val t = i.toDouble() / SAMPLE_RATE
                // Double pulse envelope
                val envelope = if (i < bufferSize / 2) {
                    (1.0 - (i.toDouble() / (bufferSize / 2)))
                } else {
                    (1.0 - ((i - bufferSize / 2).toDouble() / (bufferSize / 2)))
                }
                // Dissonant high pitch warning beeps
                val sampleValue = (sin(2.0 * Math.PI * 880.0 * t) * 32767.0 * 0.45 * envelope).toInt()
                buffer[i] = sampleValue.coerceIn(-32768, 32767).toShort()
            }
            playBuffer(buffer)
        }
    }

    fun playStart() {
        GlobalScope.launch(Dispatchers.Default) {
            val durationMs = 350
            val bufferSize = SAMPLE_RATE * durationMs / 1000
            val buffer = ShortArray(bufferSize)
            for (i in 0 until bufferSize) {
                val t = i.toDouble() / SAMPLE_RATE
                val progress = i.toDouble() / bufferSize
                // C major rising arpeggio (C5 -> E5 -> G5)
                val freq = when {
                    progress < 0.33 -> 523.25
                    progress < 0.66 -> 659.25
                    else -> 783.99
                }
                val envelope = sin(progress * Math.PI)
                val sampleValue = (sin(2.0 * Math.PI * freq * t) * 32767.0 * 0.4 * envelope).toInt()
                buffer[i] = sampleValue.coerceIn(-32768, 32767).toShort()
            }
            playBuffer(buffer)
        }
    }

    fun playEnd() {
        GlobalScope.launch(Dispatchers.Default) {
            val durationMs = 450
            val bufferSize = SAMPLE_RATE * durationMs / 1000
            val buffer = ShortArray(bufferSize)
            for (i in 0 until bufferSize) {
                val t = i.toDouble() / SAMPLE_RATE
                val progress = i.toDouble() / bufferSize
                // Decreasing chime (G5 -> E5 -> C5)
                val freq = when {
                    progress < 0.33 -> 783.99
                    progress < 0.66 -> 659.25
                    else -> 523.25
                }
                val envelope = 1.0 - progress
                val sampleValue = (sin(2.0 * Math.PI * freq * t) * 32767.0 * 0.4 * envelope).toInt()
                buffer[i] = sampleValue.coerceIn(-32768, 32767).toShort()
            }
            playBuffer(buffer)
        }
    }

    private fun playBuffer(buffer: ShortArray) {
        try {
            val minBufSize = AudioTrack.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            val track = AudioTrack(
                AudioManager.STREAM_MUSIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                maxOf(buffer.size * 2, minBufSize),
                AudioTrack.MODE_STATIC
            )
            track.write(buffer, 0, buffer.size)
            track.play()
            
            // Marker to release memory when audio track finishes playing
            track.notificationMarkerPosition = buffer.size
            track.setPlaybackPositionUpdateListener(object : AudioTrack.OnPlaybackPositionUpdateListener {
                override fun onMarkerReached(t: AudioTrack?) {
                    try {
                        t?.stop()
                        t?.release()
                    } catch (ex: Exception) {
                        ex.printStackTrace()
                    }
                }
                override fun onPeriodicNotification(t: AudioTrack?) {}
            })
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
