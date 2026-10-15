package com.example.model

sealed interface MatchState {
    data object Idle : MatchState
    data class Searching(val topic: String, val secondsWaiting: Int = 0) : MatchState
    data class Connecting(val roomId: String, val stranger: StrangerInfo) : MatchState
    data class Connected(
        val roomId: String,
        val stranger: StrangerInfo,
        val callDurationSeconds: Int = 0,
        val isMuted: Boolean = false,
        val isVideoOff: Boolean = false,
        val isFrontCamera: Boolean = true
    ) : MatchState
    data class Disconnected(val reason: String, val previousStranger: StrangerInfo?) : MatchState
}

data class StrangerInfo(
    val id: String,
    val name: String,
    val flag: String,
    val country: String,
    val interest: String,
    val bio: String
)

data class ChatMessage(
    val id: String,
    val senderName: String,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isMe: Boolean = false
)

data class FloatingReaction(
    val id: String,
    val emoji: String,
    val xFraction: Float = 0.5f
)

data class ServerConfig(
    val serverUrl: String = "http://10.0.2.2:3000",
    val useSandboxFallback: Boolean = true,
    val isConnected: Boolean = false,
    val lastPingMs: Long = -1,
    val onlineUsersCount: Int = 1842
)

object SignalingEvents {
    const val JOIN_MATCHMAKING = "join_matchmaking"
    const val WAITING = "waiting"
    const val MATCHED = "matched"
    const val OFFER = "offer"
    const val ANSWER = "answer"
    const val ICE_CANDIDATE = "ice_candidate"
    const val SKIP = "skip"
    const val LEAVE = "leave"
    const val PARTNER_DISCONNECTED = "partner_disconnected"
    const val CHAT_MESSAGE = "chat_message"
    const val REACTION = "reaction"
}
