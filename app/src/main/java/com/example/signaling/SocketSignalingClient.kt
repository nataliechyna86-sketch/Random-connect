package com.example.signaling

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.model.ChatMessage
import com.example.model.SignalingEvents
import com.example.model.StrangerInfo
import io.socket.client.IO
import io.socket.client.Socket
import org.json.JSONObject
import java.net.URI
import java.util.UUID

private const val TAG = "SocketSignaling"

class SocketSignalingClient : SignalingClient {

    private var socket: Socket? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val myUserId = UUID.randomUUID().toString()

    override var onConnected: (() -> Unit)? = null
    override var onDisconnected: (() -> Unit)? = null
    override var onConnectionError: ((String) -> Unit)? = null
    override var onWaiting: (() -> Unit)? = null
    override var onMatched: ((roomId: String, stranger: StrangerInfo, isInitiator: Boolean) -> Unit)? = null
    override var onOfferReceived: ((roomId: String, sdp: String) -> Unit)? = null
    override var onAnswerReceived: ((roomId: String, sdp: String) -> Unit)? = null
    override var onIceCandidateReceived: ((roomId: String, sdpMid: String, sdpMLineIndex: Int, sdp: String) -> Unit)? = null
    override var onChatMessageReceived: ((ChatMessage) -> Unit)? = null
    override var onReactionReceived: ((String) -> Unit)? = null
    override var onPartnerDisconnected: ((String) -> Unit)? = null

    override val isConnected: Boolean
        get() = socket?.connected() == true

    override fun connect(serverUrl: String) {
        disconnect()

        try {
            val uri = URI.create(serverUrl)
            val options = IO.Options().apply {
                reconnection = true
                reconnectionAttempts = 5
                reconnectionDelay = 1000
                timeout = 10000
                transports = arrayOf("websocket", "polling")
            }

            socket = IO.socket(uri, options).apply {
                on(Socket.EVENT_CONNECT) {
                    Log.d(TAG, "Socket connected to $serverUrl")
                    mainHandler.post { onConnected?.invoke() }
                }

                on(Socket.EVENT_DISCONNECT) {
                    Log.d(TAG, "Socket disconnected")
                    mainHandler.post { onDisconnected?.invoke() }
                }

                on(Socket.EVENT_CONNECT_ERROR) { args ->
                    val errorMsg = args.firstOrNull()?.toString() ?: "Connection error"
                    Log.w(TAG, "Socket connect error: $errorMsg")
                    mainHandler.post { onConnectionError?.invoke(errorMsg) }
                }

                on(SignalingEvents.WAITING) {
                    Log.d(TAG, "Signaling: waiting for stranger")
                    mainHandler.post { onWaiting?.invoke() }
                }

                on(SignalingEvents.MATCHED) { args ->
                    val data = args.firstOrNull() as? JSONObject
                    if (data != null) {
                        val roomId = data.optString("roomId", "room_${System.currentTimeMillis()}")
                        val peerId = data.optString("peerId", "stranger_${System.currentTimeMillis() % 1000}")
                        val isInitiator = data.optBoolean("isInitiator", false)
                        val name = data.optString("name", "Stranger")
                        val flag = data.optString("flag", "🌍")
                        val country = data.optString("country", "Worldwide")
                        val interest = data.optString("interest", "Casual")
                        val bio = data.optString("bio", "Live video chat companion")

                        val stranger = StrangerInfo(
                            id = peerId,
                            name = name,
                            flag = flag,
                            country = country,
                            interest = interest,
                            bio = bio
                        )

                        mainHandler.post {
                            onMatched?.invoke(roomId, stranger, isInitiator)
                        }
                    }
                }

                on(SignalingEvents.OFFER) { args ->
                    val data = args.firstOrNull() as? JSONObject
                    if (data != null) {
                        val roomId = data.optString("roomId")
                        val sdp = data.optString("sdp")
                        mainHandler.post { onOfferReceived?.invoke(roomId, sdp) }
                    }
                }

                on(SignalingEvents.ANSWER) { args ->
                    val data = args.firstOrNull() as? JSONObject
                    if (data != null) {
                        val roomId = data.optString("roomId")
                        val sdp = data.optString("sdp")
                        mainHandler.post { onAnswerReceived?.invoke(roomId, sdp) }
                    }
                }

                on(SignalingEvents.ICE_CANDIDATE) { args ->
                    val data = args.firstOrNull() as? JSONObject
                    if (data != null) {
                        val roomId = data.optString("roomId")
                        val candidate = data.optString("candidate")
                        val sdpMid = data.optString("sdpMid", "")
                        val sdpMLineIndex = data.optInt("sdpMLineIndex", 0)
                        mainHandler.post {
                            onIceCandidateReceived?.invoke(roomId, sdpMid, sdpMLineIndex, candidate)
                        }
                    }
                }

                on(SignalingEvents.CHAT_MESSAGE) { args ->
                    val data = args.firstOrNull() as? JSONObject
                    if (data != null) {
                        val id = data.optString("id", UUID.randomUUID().toString())
                        val sender = data.optString("sender", "Stranger")
                        val text = data.optString("text", "")
                        val isMe = data.optString("senderId") == myUserId
                        val message = ChatMessage(id, sender, text, System.currentTimeMillis(), isMe)
                        mainHandler.post { onChatMessageReceived?.invoke(message) }
                    }
                }

                on(SignalingEvents.REACTION) { args ->
                    val data = args.firstOrNull() as? JSONObject
                    val emoji = data?.optString("emoji") ?: args.firstOrNull()?.toString() ?: "❤️"
                    mainHandler.post { onReactionReceived?.invoke(emoji) }
                }

                on(SignalingEvents.PARTNER_DISCONNECTED) { args ->
                    val data = args.firstOrNull() as? JSONObject
                    val reason = data?.optString("reason") ?: "Partner left the chat"
                    mainHandler.post { onPartnerDisconnected?.invoke(reason) }
                }

                connect()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to connect to socket server: $serverUrl", e)
            onConnectionError?.invoke(e.message ?: "Failed to connect")
        }
    }

    override fun disconnect() {
        try {
            socket?.disconnect()
            socket?.off()
            socket = null
        } catch (e: Exception) {
            Log.e(TAG, "Error disconnecting socket", e)
        }
    }

    override fun joinMatchmaking(topic: String) {
        val payload = JSONObject().apply {
            put("userId", myUserId)
            put("topic", topic)
        }
        socket?.emit(SignalingEvents.JOIN_MATCHMAKING, payload)
    }

    override fun sendOffer(roomId: String, sdp: String) {
        val payload = JSONObject().apply {
            put("roomId", roomId)
            put("sdp", sdp)
            put("senderId", myUserId)
        }
        socket?.emit(SignalingEvents.OFFER, payload)
    }

    override fun sendAnswer(roomId: String, sdp: String) {
        val payload = JSONObject().apply {
            put("roomId", roomId)
            put("sdp", sdp)
            put("senderId", myUserId)
        }
        socket?.emit(SignalingEvents.ANSWER, payload)
    }

    override fun sendIceCandidate(roomId: String, sdpMid: String, sdpMLineIndex: Int, sdp: String) {
        val payload = JSONObject().apply {
            put("roomId", roomId)
            put("candidate", sdp)
            put("sdpMid", sdpMid)
            put("sdpMLineIndex", sdpMLineIndex)
            put("senderId", myUserId)
        }
        socket?.emit(SignalingEvents.ICE_CANDIDATE, payload)
    }

    override fun sendChatMessage(roomId: String, text: String) {
        val payload = JSONObject().apply {
            put("roomId", roomId)
            put("id", UUID.randomUUID().toString())
            put("senderId", myUserId)
            put("sender", "You")
            put("text", text)
        }
        socket?.emit(SignalingEvents.CHAT_MESSAGE, payload)
    }

    override fun sendReaction(roomId: String, emoji: String) {
        val payload = JSONObject().apply {
            put("roomId", roomId)
            put("emoji", emoji)
            put("senderId", myUserId)
        }
        socket?.emit(SignalingEvents.REACTION, payload)
    }

    override fun skip(roomId: String) {
        val payload = JSONObject().apply {
            put("roomId", roomId)
            put("userId", myUserId)
        }
        socket?.emit(SignalingEvents.SKIP, payload)
    }

    override fun leave(roomId: String) {
        val payload = JSONObject().apply {
            put("roomId", roomId)
            put("userId", myUserId)
        }
        socket?.emit(SignalingEvents.LEAVE, payload)
    }
}
