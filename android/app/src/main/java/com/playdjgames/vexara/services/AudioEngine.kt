package com.playdjgames.vexara.services

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.playdjgames.vexara.R
import kotlin.random.Random

/** Named sound effects mapped to their bundled resource files. */
enum class Sfx(val res: Int, val gain: Float) {
    SHOOT(R.raw.arcade_laser_pew, 0.3f),
    ENEMY_EXPLODE(R.raw.arcade_enemy_explosion, 0.55f),
    PLAYER_EXPLODE(R.raw.arcade_ship_explosion, 0.85f),
    POWER_UP(R.raw.arcade_power_up_sparkle, 0.75f),
    BOSS_APPEAR(R.raw.boss_arrival_alert, 0.75f),
    WAVE_COMPLETE(R.raw.arcade_wave_clear_fanfare, 0.75f),
    COMBO(R.raw.combo_blip_ascend, 0.45f),
    GAME_OVER(R.raw.arcade_game_over_sting, 0.75f),
    HIGH_SCORE(R.raw.arcade_high_score_jingle, 0.75f),
    HIT(R.raw.shield_impact_tick, 0.35f),
    CAPTURE(R.raw.alien_tractor_beam_capture, 0.75f),
    RESCUE(R.raw.rescue_reunion_triumph, 0.75f),
    UI_TAP(R.raw.arcade_menu_tap, 0.5f),
    BLAST(R.raw.energy_bomb_explosion, 0.85f)
}

enum class MusicTrack(val res: Int) {
    MENU(R.raw.retro_arcade_synthwave),
    BATTLE(R.raw.arcade_shooter_battle),
    BOSS(R.raw.dark_boss_battle_synth)
}

/** Pooled low-latency effects plus crossfaded looping music beds. */
object AudioEngine {
    private const val TAG = "AudioEngine"
    private const val MUSIC_VOLUME = 0.42f

    private var appContext: Context? = null
    private var pool: SoundPool? = null
    private val soundIds: MutableMap<Sfx, Int> = HashMap()
    private val loaded: MutableSet<Int> = HashSet()
    private val activeStreams: MutableList<Int> = ArrayList()

    private var musicPlayer: MediaPlayer? = null
    private var outgoingPlayer: MediaPlayer? = null
    private var currentTrack: MusicTrack? = null
    private var fadeProgress: Float = 1f
    private var suspended: Boolean = false
    private val handler = Handler(Looper.getMainLooper())

    var isMuted: Boolean = false
        set(value) {
            field = value
            musicPlayer?.setVolume(if (value) 0f else MUSIC_VOLUME, if (value) 0f else MUSIC_VOLUME)
            if (value) stopAllEffects()
        }

    /** Loads every effect into memory so the first shot never stutters. */
    fun preload(context: Context) {
        if (pool != null) return
        appContext = context.applicationContext
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val created = SoundPool.Builder().setMaxStreams(24).setAudioAttributes(attrs).build()
        created.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0) loaded.add(sampleId)
        }
        for (effect in Sfx.entries) {
            soundIds[effect] = created.load(context.applicationContext, effect.res, 1)
        }
        pool = created
    }

    fun play(effect: Sfx, rateVariation: Boolean = false) {
        if (isMuted || suspended) return
        val sound = pool ?: return
        val id = soundIds[effect] ?: return
        if (!loaded.contains(id)) return
        val rate = if (rateVariation) 0.94f + Random.nextFloat() * 0.14f else 1f
        val stream = sound.play(id, effect.gain, effect.gain, 1, 0, rate)
        if (stream != 0) {
            activeStreams.add(stream)
            if (activeStreams.size > 48) activeStreams.removeAt(0)
        }
    }

    private fun stopAllEffects() {
        val sound = pool ?: return
        for (stream in activeStreams) sound.stop(stream)
        activeStreams.clear()
    }

    fun playMusic(track: MusicTrack) {
        if (currentTrack == track) return
        currentTrack = track
        val context = appContext ?: return

        outgoingPlayer?.release()
        outgoingPlayer = musicPlayer
        val player = try {
            MediaPlayer.create(context, track.res)
        } catch (error: Exception) {
            Log.w(TAG, "music load failed")
            null
        } ?: return
        player.isLooping = true
        player.setVolume(0f, 0f)
        if (!suspended) player.start()
        musicPlayer = player
        fadeProgress = 0f
        handler.removeCallbacks(fadeStep)
        handler.post(fadeStep)
    }

    private val fadeStep: Runnable = object : Runnable {
        override fun run() {
            fadeProgress = (fadeProgress + 0.05f / 0.9f).coerceAtMost(1f)
            val target = if (isMuted) 0f else MUSIC_VOLUME
            musicPlayer?.setVolume(target * fadeProgress, target * fadeProgress)
            outgoingPlayer?.setVolume(target * (1 - fadeProgress), target * (1 - fadeProgress))
            if (fadeProgress >= 1f) {
                outgoingPlayer?.stop()
                outgoingPlayer?.release()
                outgoingPlayer = null
            } else {
                handler.postDelayed(this, 50)
            }
        }
    }

    /** Silences everything while the app is in the background. */
    fun suspend() {
        suspended = true
        musicPlayer?.takeIf { it.isPlaying }?.pause()
        outgoingPlayer?.takeIf { it.isPlaying }?.pause()
        stopAllEffects()
    }

    fun resumeFromBackground() {
        if (!suspended) return
        suspended = false
        musicPlayer?.start()
    }
}
