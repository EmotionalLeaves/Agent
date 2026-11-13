package com.emotionalleaves.studioagent.media

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.random.Random

/**
 * A steady low hum of brown noise for a session: it covers kitchen clatter and a busy head, and many people with ADHD
 * find it easier to settle with it on. Made on the phone, nothing to download; it fades in and out so it never startles.
 */
object Noise {
    @Volatile private var track: AudioTrack? = null
    @Volatile private var running = false
    val playing: Boolean get() = running

    fun start(volume: Float = 0.35f) {
        if (running) return
        running = true
        Thread {
            val rate = 22050
            val size = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT).coerceAtLeast(4096)
            val t = try {
                AudioTrack.Builder()
                    .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                    .setAudioFormat(AudioFormat.Builder().setSampleRate(rate).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                    .setBufferSizeInBytes(size * 2).setTransferMode(AudioTrack.MODE_STREAM).build()
            } catch (e: Exception) { running = false; return@Thread }
            track = t
            t.play()
            val buf = ShortArray(size / 2)
            var last = 0.0
            var gain = 0.0
            while (running) {
                for (i in buf.indices) {
                    // Brown noise: each sample a small random step from the last, pulled gently back to the middle.
                    last = (last + (Random.nextDouble() * 2 - 1) * 0.02) * 0.998
                    if (gain < volume) gain += 0.00002
                    buf[i] = (last * 3.5 * gain * Short.MAX_VALUE).coerceIn(-32000.0, 32000.0).toInt().toShort()
                }
                if (t.write(buf, 0, buf.size) < 0) break
            }
            try { t.pause(); t.flush(); t.release() } catch (e: Exception) { }
            track = null
        }.apply { isDaemon = true; name = "studio-noise" }.start()
    }

    fun stop() { running = false }
}
