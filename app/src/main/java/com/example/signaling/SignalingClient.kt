package com.example.signaling

import com.example.model.ChatMessage
import com.example.model.StrangerInfo

interface SignalingClient {
    var onConnected: (() -> Unit)?
    var onDisconnected: (() -> Unit)?
    var onConnectionError: ((String) -> Unit)?
    var onWaiting: (() -> Unit)?
    var onMatched: ((roomId: String, stranger: StrangerInfo, isInitiator: Boolean) -> Unit)?
    var onOfferReceived: ((roomId: String, sdp: String) -> Unit)?
    var onAnswerReceived: ((roomId: String, sdp: String) -> Unit)?
    var onIceCandidateReceived: ((roomId: String, sdpMid: String, sdpMLineIndex: Int, sdp: String) -> Unit)?
    var onChatMessageReceived: ((ChatMessage) -> Unit)?
    var onReactionReceived: ((String) -> Unit)?
    var onPartnerDisconnected: ((String) -> Unit)?

    val isConnected: Boolean

    fun connect(serverUrl: String)
    fun disconnect()
    fun joinMatchmaking(topic: String)
    fun sendOffer(roomId: String, sdp: String)
    fun sendAnswer(roomId: String, sdp: String)
    fun sendIceCandidate(roomId: String, sdpMid: String, sdpMLineIndex: Int, sdp: String)
    fun sendChatMessage(roomId: String, text: String)
    fun sendReaction(roomId: String, emoji: String)
    fun skip(roomId: String)
    fun leave(roomId: String)
}
