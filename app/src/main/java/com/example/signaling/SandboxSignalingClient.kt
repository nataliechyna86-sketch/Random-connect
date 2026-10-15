package com.example.signaling

import android.os.Handler
import android.os.Looper
import com.example.model.ChatMessage
import com.example.model.StrangerInfo
import java.util.UUID
import kotlin.random.Random

class SandboxSignalingClient : SignalingClient {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var isMatching = false
    private var currentRoomId: String? = null
    private var messageReplyRunnable: Runnable? = null
    private var reactionRunnable: Runnable? = null

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

    override var isConnected: Boolean = true
        private set

    private val sampleStrangers = listOf(
        StrangerInfo("stranger_1", "Maya", "🇬🇧", "United Kingdom", "🎵 Music", "Guitarist and indie rock fan from London"),
        StrangerInfo("stranger_2", "Kenji", "🇯🇵", "Japan", "🎮 Gaming", "Playing RPGs and speedrunning retro games"),
        StrangerInfo("stranger_3", "Sofia", "🇪🇸", "Spain", "🌍 Travel", "Photographer backpacking across Europe"),
        StrangerInfo("stranger_4", "Lucas", "🇧🇷", "Brazil", "💻 Coding", "Kotlin and mobile enthusiast"),
        StrangerInfo("stranger_5", "Chloe", "🇺🇸", "United States", "🎬 Movies", "Film student from NYC obsessed with sci-fi"),
        StrangerInfo("stranger_6", "Liam", "🇦🇺", "Australia", "☕ Casual", "Surfing, beach vibes and good coffee"),
        StrangerInfo("stranger_7", "Amira", "🇦🇪", "UAE", "🌍 Travel", "Architect and digital nomad in Dubai"),
        StrangerInfo("stranger_8", "Noah", "🇨🇦", "Canada", "🎮 Gaming", "Competitive gamer and game dev enthusiast")
    )

    private val sampleGreetings = listOf(
        "Hey there! Where are you calling from?",
        "Hello! Nice to meet you 👋",
        "Hi! How is your day going?",
        "Hey! What kind of music do you listen to?",
        "Yo! What are you up to today?"
    )

    private val cannedReplies = mapOf(
        "hi" to "Hey! How are you doing today?",
        "hello" to "Hello! Having a nice day?",
        "where" to "I'm calling from my hometown! What about you?",
        "cool" to "Thanks! Really like chatting with random people here 🙌",
        "music" to "I love indie rock and synthwave! You?",
        "game" to "Mostly PC games and Switch lately! What do you play?"
    )

    override fun connect(serverUrl: String) {
        isConnected = true
        mainHandler.postDelayed({ onConnected?.invoke() }, 200)
    }

    override fun disconnect() {
        cancelScheduledTasks()
        isConnected = false
        mainHandler.post { onDisconnected?.invoke() }
    }

    override fun joinMatchmaking(topic: String) {
        isMatching = true
        mainHandler.post { onWaiting?.invoke() }

        // Simulate matching delay of 1.5 - 2.8 seconds
        val delayMs = Random.nextLong(1500, 2800)
        mainHandler.postDelayed({
            if (!isMatching) return@postDelayed
            val roomId = "sandbox_room_${UUID.randomUUID().toString().take(8)}"
            currentRoomId = roomId

            // Find matching stranger or random
            val matched = sampleStrangers.shuffled().firstOrNull {
                topic == "All" || it.interest.contains(topic, ignoreCase = true)
            } ?: sampleStrangers.random()

            onMatched?.invoke(roomId, matched, true)

            // Stranger greets after 1.5s
            messageReplyRunnable = Runnable {
                val greeting = sampleGreetings.random()
                onChatMessageReceived?.invoke(
                    ChatMessage(
                        id = UUID.randomUUID().toString(),
                        senderName = matched.name,
                        text = greeting,
                        isMe = false
                    )
                )
            }
            mainHandler.postDelayed(messageReplyRunnable!!, 1800)

            // Stranger sends a friendly reaction after 3s
            reactionRunnable = Runnable {
                val emojis = listOf("👋", "❤️", "🔥", "✨")
                onReactionReceived?.invoke(emojis.random())
            }
            mainHandler.postDelayed(reactionRunnable!!, 3200)

        }, delayMs)
    }

    override fun sendOffer(roomId: String, sdp: String) {
        // In sandbox mode, the offer is accepted by the simulated room without loopback
    }

    override fun sendAnswer(roomId: String, sdp: String) {}

    override fun sendIceCandidate(roomId: String, sdpMid: String, sdpMLineIndex: Int, sdp: String) {}

    override fun sendChatMessage(roomId: String, text: String) {
        // Simulate a smart contextual response from stranger after 1.2s
        mainHandler.postDelayed({
            val lower = text.lowercase()
            val reply = cannedReplies.entries.firstOrNull { lower.contains(it.key) }?.value
                ?: "Haha that's awesome! Live video chat is so fun 😃"

            onChatMessageReceived?.invoke(
                ChatMessage(
                    id = UUID.randomUUID().toString(),
                    senderName = "Stranger",
                    text = reply,
                    isMe = false
                )
            )
        }, 1200)
    }

    override fun sendReaction(roomId: String, emoji: String) {
        // Stranger sometimes echoes reaction
        if (Random.nextBoolean()) {
            mainHandler.postDelayed({
                val responseEmoji = if (emoji == "❤️") "❤️" else listOf("🔥", "👏", "😂", "✨").random()
                onReactionReceived?.invoke(responseEmoji)
            }, 800)
        }
    }

    override fun skip(roomId: String) {
        cancelScheduledTasks()
        isMatching = false
        currentRoomId = null
        onPartnerDisconnected?.invoke("Partner skipped to next stranger")
    }

    override fun leave(roomId: String) {
        cancelScheduledTasks()
        isMatching = false
        currentRoomId = null
    }

    private fun cancelScheduledTasks() {
        messageReplyRunnable?.let { mainHandler.removeCallbacks(it) }
        reactionRunnable?.let { mainHandler.removeCallbacks(it) }
        messageReplyRunnable = null
        reactionRunnable = null
    }
}
