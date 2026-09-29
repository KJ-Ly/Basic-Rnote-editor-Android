package io.github.kjly.brna.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import android.os.Handler
import android.os.Looper
import io.github.kjly.brna.R

/**
 * Rnote's pen sounds (rnote-engine's `AudioPlayer`), from Rnote's own recordings: a
 * pencil scratching while the brush draws, a felt tip's squeak at each marker stroke,
 * and a typewriter for the Typewriter. Like Rnote's, they are off until switched on in
 * the canvas menu.
 *
 * The pencil is one long recording, looped, started at one of a few places Rnote picks
 * from and stopped [BRUSH_TIMEOUT_MS] after the pen last moved, as Rnote stops it; the
 * short sounds are picked at random from their sets.
 */
class PenSounds(context: Context) {

    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    private val pool = SoundPool.Builder().setMaxStreams(MAX_STREAMS).setAudioAttributes(attributes).build()
    private val markers = MARKER_SOUNDS.map { pool.load(context, it, 1) }
    private val keys = TYPEWRITER_SOUNDS.map { pool.load(context, it, 1) }
    private val bell = pool.load(context, R.raw.typewriter_bell, 1)
    private val linefeed = pool.load(context, R.raw.typewriter_linefeed, 1)
    private val thump = pool.load(context, R.raw.typewriter_thump, 1)

    private val brush: MediaPlayer? = MediaPlayer.create(context, R.raw.brush, attributes, 0)?.apply { isLooping = true }
    private val handler = Handler(Looper.getMainLooper())
    private val stopBrush = Runnable { brush?.takeIf { it.isPlaying }?.pause() }

    /**
     * Rnote's `trigger_random_brush_sound`, on every movement of a brush other than the
     * marker: the pencil goes on, or starts again somewhere in its recording.
     */
    fun brush() {
        val player = brush ?: return
        handler.removeCallbacks(stopBrush)
        if (!player.isPlaying) {
            player.seekTo(BRUSH_SEEK_TIMES_MS.random(), MediaPlayer.SEEK_CLOSEST)
            player.start()
        }
        handler.postDelayed(stopBrush, BRUSH_TIMEOUT_MS)
    }

    /** Rnote's `play_random_marker_sound`, as the marker touches down. */
    fun marker() {
        play(markers.random())
    }

    /**
     * Rnote's `play_typewriter_key_sound` for typing: a key for a character, a deletion or
     * a tab; the bell and then, a moment later, the line feed for a new line.
     */
    fun typed(newLine: Boolean) {
        if (newLine) {
            play(bell)
            handler.postDelayed({ play(linefeed) }, LINEFEED_DELAY_MS)
        } else {
            play(keys.random())
        }
    }

    /** A key that only moves the cursor: the typewriter's thump, as in Rnote. */
    fun cursorKey() {
        play(thump)
    }

    private fun play(sound: Int) {
        pool.play(sound, 1f, 1f, 1, 0, 1f)
    }

    fun release() {
        handler.removeCallbacksAndMessages(null)
        pool.release()
        brush?.release()
    }

    private companion object {
        /** `AudioPlayer::BRUSH_SOUND_TIMEOUT`. */
        const val BRUSH_TIMEOUT_MS = 600L

        /** `AudioPlayer::SOUND_FILE_BRUSH_SEEK_TIMES_MS`: where in the recording the pencil starts. */
        val BRUSH_SEEK_TIMES_MS = longArrayOf(0L, 910L, 4129L, 6000L, 8560L)

        /** How long after the bell the line feed follows, as Rnote delays it. */
        const val LINEFEED_DELAY_MS = 200L

        const val MAX_STREAMS = 8

        val MARKER_SOUNDS = intArrayOf(
            R.raw.marker_00, R.raw.marker_01, R.raw.marker_02, R.raw.marker_03, R.raw.marker_04,
            R.raw.marker_05, R.raw.marker_06, R.raw.marker_07, R.raw.marker_08, R.raw.marker_09,
            R.raw.marker_10, R.raw.marker_11, R.raw.marker_12, R.raw.marker_13, R.raw.marker_14
        )

        val TYPEWRITER_SOUNDS = intArrayOf(
            R.raw.typewriter_00, R.raw.typewriter_01, R.raw.typewriter_02, R.raw.typewriter_03, R.raw.typewriter_04,
            R.raw.typewriter_05, R.raw.typewriter_06, R.raw.typewriter_07, R.raw.typewriter_08, R.raw.typewriter_09,
            R.raw.typewriter_10, R.raw.typewriter_11, R.raw.typewriter_12, R.raw.typewriter_13, R.raw.typewriter_14,
            R.raw.typewriter_15, R.raw.typewriter_16, R.raw.typewriter_17, R.raw.typewriter_18, R.raw.typewriter_19,
            R.raw.typewriter_20, R.raw.typewriter_21, R.raw.typewriter_22, R.raw.typewriter_23, R.raw.typewriter_24,
            R.raw.typewriter_25, R.raw.typewriter_26, R.raw.typewriter_27, R.raw.typewriter_28, R.raw.typewriter_29
        )
    }
}
