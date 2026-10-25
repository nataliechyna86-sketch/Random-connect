package com.example.webrtc

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.util.Log

private const val TAG = "AppAudioManager"

class AppAudioManager(private val context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var previousAudioMode = AudioManager.MODE_NORMAL
    private var previousSpeakerphoneOn = false
    private var isCallAudioActive = false

    fun start() {
        if (isCallAudioActive) return
        isCallAudioActive = true

        try {
            previousAudioMode = audioManager.mode
            previousSpeakerphoneOn = audioManager.isSpeakerphoneOn

            // Request Audio Focus
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .build()
                audioManager.requestAudioFocus(focusRequest)
            } else {
                @Suppress("DEPRECATION")
                audioManager.requestAudioFocus(
                    null,
                    AudioManager.STREAM_VOICE_CALL,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
                )
            }

            // Set Communication Mode for Hardware Echo Cancellation & AudioRecord routing
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION

            // Route audio to loudspeaker
            setSpeakerphone(true)

            Log.d(TAG, "Call audio active: mode=MODE_IN_COMMUNICATION, speakerphone=ON")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting call audio", e)
        }
    }

    fun setSpeakerphone(enable: Boolean) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val devices = audioManager.availableCommunicationDevices
                val targetType = if (enable) {
                    AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
                } else {
                    AudioDeviceInfo.TYPE_BUILTIN_EARPIECE
                }

                val speakerDevice = devices.find { it.type == targetType }
                if (speakerDevice != null) {
                    val success = audioManager.setCommunicationDevice(speakerDevice)
                    Log.d(TAG, "setCommunicationDevice($targetType) result: $success")
                } else {
                    @Suppress("DEPRECATION")
                    audioManager.isSpeakerphoneOn = enable
                }
            } else {
                @Suppress("DEPRECATION")
                audioManager.isSpeakerphoneOn = enable
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting speakerphone to $enable", e)
        }
    }

    fun stop() {
        if (!isCallAudioActive) return
        isCallAudioActive = false

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                audioManager.clearCommunicationDevice()
            }

            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn = previousSpeakerphoneOn
            audioManager.mode = AudioManager.MODE_NORMAL

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                @Suppress("DEPRECATION")
                audioManager.abandonAudioFocus(null)
            }

            Log.d(TAG, "Call audio stopped, restored MODE_NORMAL")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping call audio", e)
        }
    }
}
