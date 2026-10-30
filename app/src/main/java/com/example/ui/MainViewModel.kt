package com.example.ui

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.model.ChatMessage
import com.example.model.FloatingReaction
import com.example.model.MatchState
import com.example.model.ServerConfig
import com.example.model.StrangerInfo
import com.example.signaling.SandboxSignalingClient
import com.example.signaling.SignalingClient
import com.example.signaling.SocketSignalingClient
import com.example.webrtc.WebRtcManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.random.Random

private const val TAG = "MainViewModel"
private const val PREFS_NAME = "randomcam_prefs"
private const val KEY_SERVER_URL = "server_url"
private const val KEY_USE_SANDBOX = "use_sandbox"
private const val DEFAULT_SERVER_URL = "https://random-connect.onrender.com"

class MainViewModel(application: Application) : AndroidViewModel(application) {

    val webRtcManager: WebRtcManager = WebRtcManager(application.applicationContext)

    private val socketClient = SocketSignalingClient()
    private val sandboxClient = SandboxSignalingClient()
    private var activeSignaling: SignalingClient = socketClient

    private val _matchState = MutableStateFlow<MatchState>(MatchState.Idle)
    val matchState: StateFlow<MatchState> = _matchState.asStateFlow()

    private val _serverConfig = MutableStateFlow(
        ServerConfig(
            serverUrl = DEFAULT_SERVER_URL,
            useSandboxFallback = false,
            isConnected = false,
            isConnecting = true,
            onlineUsersCount = 1842
        )
    )
    val serverConfig: StateFlow<ServerConfig> = _serverConfig.asStateFlow()

    private val _selectedTopic = MutableStateFlow("All")
    val selectedTopic: StateFlow<String> = _selectedTopic.asStateFlow()

    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()

    private val _floatingReactions = MutableStateFlow<List<FloatingReaction>>(emptyList())
    val floatingReactions: StateFlow<List<FloatingReaction>> = _floatingReactions.asStateFlow()

    private val _isChatDrawerOpen = MutableStateFlow(false)
    val isChatDrawerOpen: StateFlow<Boolean> = _isChatDrawerOpen.asStateFlow()

    private val _reportedUsers = MutableStateFlow<Set<String>>(emptySet())

    private var timerJob: Job? = null
    private var isLocalMediaStarted = false

    private var currentCallRoomId: String? = null

    init {
        setupSignalingCallbacks(sandboxClient)
        setupSignalingCallbacks(socketClient)
        setupWebRtcCallbacks()

        // Load persisted server preferences
        val prefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedUrl = prefs.getString(KEY_SERVER_URL, DEFAULT_SERVER_URL) ?: DEFAULT_SERVER_URL
        val savedSandbox = prefs.getBoolean(KEY_USE_SANDBOX, false)

        _serverConfig.value = ServerConfig(
            serverUrl = savedUrl,
            useSandboxFallback = savedSandbox,
            isConnected = false,
            isConnecting = !savedSandbox,
            onlineUsersCount = 1842
        )

        if (savedSandbox) {
            useSandboxMode()
        } else {
            connectToSocketServer(savedUrl)
        }

        // Periodically fluctuate online users counter for realistic live feel
        viewModelScope.launch {
            while (isActive) {
                delay(4000)
                _serverConfig.update {
                    val delta = Random.nextInt(-3, 5)
                    it.copy(onlineUsersCount = (it.onlineUsersCount + delta).coerceIn(1200, 3500))
                }
            }
        }
    }

    private fun setupSignalingCallbacks(client: SignalingClient) {
        client.onConnected = {
            if (activeSignaling === client) {
                Log.i(TAG, "Signaling connected successfully")
                _serverConfig.update { it.copy(isConnected = true, isConnecting = false, connectionError = null) }
            }
        }

        client.onDisconnected = {
            if (activeSignaling === client) {
                Log.i(TAG, "Signaling disconnected")
                _serverConfig.update { it.copy(isConnected = false, isConnecting = false) }
            }
        }

        client.onConnectionError = { error ->
            Log.w(TAG, "Signaling connection error: $error")
            if (activeSignaling === socketClient) {
                if (_serverConfig.value.useSandboxFallback) {
                    Log.i(TAG, "Falling back to Sandbox mode as configured")
                    useSandboxMode()
                } else {
                    _serverConfig.update {
                        it.copy(
                            isConnected = false,
                            isConnecting = false,
                            connectionError = error
                        )
                    }
                }
            }
        }

        client.onWaiting = {
            _matchState.update { MatchState.Searching(_selectedTopic.value, 0) }
            startSearchingTimer()
        }

        client.onMatched = { roomId, stranger, isInitiator ->
            stopTimer()
            currentCallRoomId = roomId
            _matchState.update { MatchState.Connecting(roomId, stranger) }

            // Ensure camera and audio tracks are active before WebRTC negotiation
            onPermissionsGranted()

            // WebRTC peer setup
            webRtcManager.createPeerConnection()

            if (isInitiator) {
                webRtcManager.createOffer { sdp ->
                    activeSignaling.sendOffer(roomId, sdp.description)
                }
            }

            // Transition to Connected
            _matchState.update {
                MatchState.Connected(
                    roomId = roomId,
                    stranger = stranger,
                    callDurationSeconds = 0
                )
            }
            startCallTimer()
        }

        client.onOfferReceived = { roomId, sdp ->
            currentCallRoomId = roomId
            if (!webRtcManager.isPeerConnectionActive) {
                webRtcManager.createPeerConnection()
            }
            webRtcManager.handleOffer(sdp) { answerSdp ->
                activeSignaling.sendAnswer(roomId, answerSdp.description)
            }
        }

        client.onAnswerReceived = { _, sdp ->
            webRtcManager.handleAnswer(sdp)
        }

        client.onIceCandidateReceived = { _, sdpMid, sdpMLineIndex, candidate ->
            webRtcManager.addRemoteIceCandidate(sdpMid, sdpMLineIndex, candidate)
        }

        client.onChatMessageReceived = { message ->
            _chatMessages.update { it + message }
        }

        client.onReactionReceived = { emoji ->
            triggerFloatingReaction(emoji)
        }

        client.onPartnerDisconnected = { reason ->
            stopTimer()
            currentCallRoomId = null
            webRtcManager.closePeerConnection()
            val currentStranger = (_matchState.value as? MatchState.Connected)?.stranger
            _matchState.update { MatchState.Disconnected(reason, currentStranger) }
        }
    }

    private fun setupWebRtcCallbacks() {
        webRtcManager.onIceCandidateGenerated = { candidate ->
            val roomId = currentCallRoomId
                ?: (matchState.value as? MatchState.Connected)?.roomId
                ?: (matchState.value as? MatchState.Connecting)?.roomId

            if (!roomId.isNullOrEmpty()) {
                Log.d(TAG, "Sending generated ICE candidate for room: $roomId")
                activeSignaling.sendIceCandidate(
                    roomId,
                    candidate.sdpMid ?: "",
                    candidate.sdpMLineIndex,
                    candidate.sdp
                )
            } else {
                Log.w(TAG, "Dropped candidate: no active roomId found")
            }
        }
    }

    fun onPermissionsGranted() {
        webRtcManager.resumeCamera()
        if (!isLocalMediaStarted) {
            webRtcManager.startLocalMedia()
            isLocalMediaStarted = true
        }
    }

    fun onResume() {
        webRtcManager.resumeCamera()
    }

    fun onPause() {
        if (_matchState.value is MatchState.Idle) {
            webRtcManager.pauseCamera()
        }
    }

    fun setTopic(topic: String) {
        _selectedTopic.value = topic
    }

    fun toggleChatDrawer() {
        _isChatDrawerOpen.update { !it }
    }

    fun startMatchmaking() {
        onPermissionsGranted()
        webRtcManager.resumeCamera()
        _chatMessages.value = emptyList()
        _floatingReactions.value = emptyList()
        _isChatDrawerOpen.value = false

        val config = _serverConfig.value
        if (!config.useSandboxFallback && !activeSignaling.isConnected) {
            connectToSocketServer(config.serverUrl)
        }

        _matchState.update { MatchState.Searching(_selectedTopic.value, 0) }
        startSearchingTimer()

        activeSignaling.joinMatchmaking(_selectedTopic.value)
    }

    fun skipStranger() {
        stopTimer()
        currentCallRoomId = null
        val current = _matchState.value
        val roomId = when (current) {
            is MatchState.Connected -> current.roomId
            is MatchState.Connecting -> current.roomId
            else -> ""
        }

        if (roomId.isNotEmpty()) {
            activeSignaling.skip(roomId)
        }

        webRtcManager.closePeerConnection()
        _chatMessages.value = emptyList()

        // Immediately search for next stranger
        startMatchmaking()
    }

    fun leaveChat() {
        stopTimer()
        currentCallRoomId = null
        val current = _matchState.value
        val roomId = when (current) {
            is MatchState.Connected -> current.roomId
            is MatchState.Connecting -> current.roomId
            else -> ""
        }

        if (roomId.isNotEmpty()) {
            activeSignaling.leave(roomId)
        }

        webRtcManager.closePeerConnection()
        _chatMessages.value = emptyList()
        _floatingReactions.value = emptyList()
        _isChatDrawerOpen.value = false
        _matchState.update { MatchState.Idle }
    }

    fun toggleMute() {
        val current = _matchState.value as? MatchState.Connected ?: return
        val newMuted = !current.isMuted
        webRtcManager.toggleMute(newMuted)
        _matchState.update { current.copy(isMuted = newMuted) }
    }

    fun toggleVideo() {
        val current = _matchState.value as? MatchState.Connected ?: return
        val newVideoOff = !current.isVideoOff
        webRtcManager.toggleVideo(!newVideoOff)
        _matchState.update { current.copy(isVideoOff = newVideoOff) }
    }

    fun flipCamera() {
        webRtcManager.flipCamera { isFront ->
            val current = _matchState.value as? MatchState.Connected
            if (current != null) {
                _matchState.update { current.copy(isFrontCamera = isFront) }
            }
        }
    }

    fun sendChatMessage(text: String) {
        if (text.isBlank()) return
        val current = _matchState.value as? MatchState.Connected ?: return

        val myMessage = ChatMessage(
            id = UUID.randomUUID().toString(),
            senderName = "You",
            text = text.trim(),
            isMe = true
        )
        _chatMessages.update { it + myMessage }
        activeSignaling.sendChatMessage(current.roomId, text.trim())
    }

    fun sendReaction(emoji: String) {
        val current = _matchState.value as? MatchState.Connected ?: return
        triggerFloatingReaction(emoji)
        activeSignaling.sendReaction(current.roomId, emoji)
    }

    fun reportStranger(reason: String) {
        val current = _matchState.value as? MatchState.Connected ?: return
        _reportedUsers.update { it + current.stranger.id }
        skipStranger()
    }

    private fun triggerFloatingReaction(emoji: String) {
        val id = UUID.randomUUID().toString()
        val xOffset = Random.nextFloat() * 0.7f + 0.15f
        val reaction = FloatingReaction(id, emoji, xOffset)
        _floatingReactions.update { it + reaction }

        viewModelScope.launch {
            delay(2500)
            _floatingReactions.update { list -> list.filter { it.id != id } }
        }
    }

    fun updateServerConfig(url: String, useSandbox: Boolean) {
        val cleanUrl = url.trim().removeSuffix("/")
        val prefs = getApplication<Application>().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_SERVER_URL, cleanUrl)
            .putBoolean(KEY_USE_SANDBOX, useSandbox)
            .apply()

        _serverConfig.update {
            it.copy(
                serverUrl = cleanUrl,
                useSandboxFallback = useSandbox,
                connectionError = null,
                isConnecting = !useSandbox,
                isConnected = false
            )
        }

        if (useSandbox) {
            useSandboxMode()
        } else {
            connectToSocketServer(cleanUrl)
        }
    }

    private fun connectToSocketServer(url: String) {
        _serverConfig.update { it.copy(isConnecting = true, connectionError = null) }
        activeSignaling.disconnect()
        activeSignaling = socketClient
        socketClient.connect(url)
    }

    private fun useSandboxMode() {
        activeSignaling.disconnect()
        activeSignaling = sandboxClient
        sandboxClient.connect("")
        _serverConfig.update { it.copy(isConnected = true, isConnecting = false, connectionError = null) }
    }

    private fun startSearchingTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            var seconds = 0
            while (isActive) {
                delay(1000)
                seconds++
                val current = _matchState.value
                if (current is MatchState.Searching) {
                    _matchState.update { current.copy(secondsWaiting = seconds) }
                }
            }
        }
    }

    private fun startCallTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            var seconds = 0
            while (isActive) {
                delay(1000)
                seconds++
                val current = _matchState.value
                if (current is MatchState.Connected) {
                    _matchState.update { current.copy(callDurationSeconds = seconds) }
                }
            }
        }
    }

    private fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
    }

    override fun onCleared() {
        super.onCleared()
        stopTimer()
        activeSignaling.disconnect()
        webRtcManager.release()
    }
}
