package com.rasticpack.app.ui.theme

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sin

/**
 * ══ زیرمرحله ۱۱.۳ — صدای کلیک دکمه‌ها ══
 * معادل دقیق تابع playClickSound در 4.html:
 *   - نوسان‌ساز سینوسی که فرکانسش طی ۰.۰۷ ثانیه به‌صورت نمایی از ۷۲۰ هرتز به ۴۲۰ هرتز
 *     می‌رسد (exponentialRampToValueAtTime)
 *   - بلندی صدا (gain) از ۰.۰۹ طی ۰.۰۹ ثانیه به‌صورت نمایی به نزدیک صفر می‌رسد
 *   - طول کل صدا ۰.۱ ثانیه (۱۰۰ میلی‌ثانیه)
 * در اندروید معادل WebAudio API (AudioContext/OscillatorNode) وجود ندارد، پس این صدا
 * به‌صورت PCM 16-bit در حافظه ساخته و یک‌بار محاسبه می‌شود (نه هر بار پخش) تا پخش هر
 * کلیک فوری و بدون تاخیر محاسباتی باشد؛ سپس با AudioTrack پخش می‌شود.
 */
object ClickSound {
    private const val SAMPLE_RATE = 44100
    private const val DURATION_SEC = 0.1
    private const val FREQ_START = 720.0
    private const val FREQ_END = 420.0
    private const val FREQ_RAMP_SEC = 0.07
    private const val GAIN_START = 0.09
    private const val GAIN_RAMP_SEC = 0.09
    private const val GAIN_END = 0.0001

    // pcmData ساخته می‌شود فقط یک بار (lazy) — دقیقاً همان موج نسخه‌ی وب
    private val pcmData: ShortArray by lazy { buildTone() }

    private var track: AudioTrack? = null

    private fun buildTone(): ShortArray {
        val totalSamples = (SAMPLE_RATE * DURATION_SEC).toInt()
        val samples = ShortArray(totalSamples)
        var phase = 0.0
        for (i in 0 until totalSamples) {
            val t = i.toDouble() / SAMPLE_RATE

            // فرکانس: رمپ نمایی از FREQ_START به FREQ_END طی FREQ_RAMP_SEC، سپس ثابت روی FREQ_END
            val freq = if (t >= FREQ_RAMP_SEC) {
                FREQ_END
            } else {
                val ratio = t / FREQ_RAMP_SEC
                FREQ_START * exp(ln(FREQ_END / FREQ_START) * ratio)
            }

            // بلندی: رمپ نمایی از GAIN_START به نزدیک صفر طی GAIN_RAMP_SEC، سپس صفر
            val gain = if (t >= GAIN_RAMP_SEC) {
                0.0
            } else {
                val ratio = t / GAIN_RAMP_SEC
                GAIN_START * exp(ln(GAIN_END / GAIN_START) * ratio)
            }

            phase += 2.0 * PI * freq / SAMPLE_RATE
            val sampleValue = sin(phase) * gain
            samples[i] = (sampleValue * Short.MAX_VALUE).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return samples
    }

    private fun getOrCreateTrack(): AudioTrack {
        track?.let { return it }
        val bufferSize = maxOf(
            AudioTrack.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT),
            pcmData.size * 2
        )
        val newTrack = AudioTrack(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
            AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(SAMPLE_RATE)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build(),
            bufferSize,
            AudioTrack.MODE_STATIC,
            android.media.AudioManager.AUDIO_SESSION_ID_GENERATE
        )
        newTrack.write(pcmData, 0, pcmData.size)
        track = newTrack
        return newTrack
    }

    /** پخش صدای کلیک — بی‌صدا شکست می‌خورد اگر دستگاه/درایور صوتی مشکلی داشته باشد */
    fun play() {
        try {
            val t = getOrCreateTrack()
            if (t.playState == AudioTrack.PLAYSTATE_PLAYING) {
                t.stop()
            }
            t.reloadStaticData()
            t.setPlaybackHeadPosition(0)
            t.play()
        } catch (_: Exception) {
            // نادیده گرفتن خطای پخش صدا — نباید تجربه‌ی کاربر را مختل کند
        }
    }

    /** آزادسازی منابع — در صورت نیاز (مثلاً onDestroy اکتیویتی اصلی) */
    fun release() {
        try {
            track?.release()
        } catch (_: Exception) {
        } finally {
            track = null
        }
    }
}
