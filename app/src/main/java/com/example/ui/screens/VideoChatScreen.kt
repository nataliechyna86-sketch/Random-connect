package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ChatMessage
import com.example.model.FloatingReaction
import com.example.model.MatchState
import com.example.model.StrangerInfo
import com.example.ui.components.ChatOverlay
import com.example.ui.components.LocalVideoSurfaceView
import com.example.ui.components.RemoteVideoSurfaceView
import com.example.ui.components.ReportBlockDialog
import com.example.ui.components.VideoControlsBar
import com.example.ui.theme.DarkNavyBackground
import com.example.ui.theme.DarkNavyElevated
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonRose
import com.example.ui.theme.NeonViolet
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.webrtc.WebRtcManager

@Composable
fun VideoChatScreen(
    matchState: MatchState,
    webRtcManager: WebRtcManager,
    chatMessages: List<ChatMessage>,
    floatingReactions: List<FloatingReaction>,
    isChatDrawerOpen: Boolean,
    onSkip: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleVideo: () -> Unit,
    onFlipCamera: () -> Unit,
    onToggleChat: () -> Unit,
    onSendChatMessage: (String) -> Unit,
    onSendReaction: (String) -> Unit,
    onReportStranger: (String) -> Unit,
    onLeave: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showReportDialog by remember { mutableStateOf(false) }

    val stranger: StrangerInfo? = when (matchState) {
        is MatchState.Connected -> matchState.stranger
        is MatchState.Connecting -> matchState.stranger
        is MatchState.Disconnected -> matchState.previousStranger
        else -> null
    }

    val isConnected = matchState is MatchState.Connected
    val isDisconnected = matchState is MatchState.Disconnected
    val isMuted = (matchState as? MatchState.Connected)?.isMuted ?: false
    val isVideoOff = (matchState as? MatchState.Connected)?.isVideoOff ?: false
    val durationSeconds = (matchState as? MatchState.Connected)?.callDurationSeconds ?: 0

    val minutes = durationSeconds / 60
    val seconds = durationSeconds % 60
    val durationFormatted = "%02d:%02d".format(minutes, seconds)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DarkNavyBackground)
    ) {
        // Full screen Remote Stranger Stream
        RemoteVideoSurfaceView(
            webRtcManager = webRtcManager,
            stranger = stranger,
            modifier = Modifier.fillMaxSize()
        )

        // Floating Local PIP Camera Card (Top-Right)
        Card(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 70.dp, end = 16.dp)
                .size(width = 110.dp, height = 150.dp)
                .clip(RoundedCornerShape(16.dp))
                .border(2.dp, NeonCyan, RoundedCornerShape(16.dp)),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = DarkNavyElevated)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                LocalVideoSurfaceView(
                    webRtcManager = webRtcManager,
                    isVideoOff = isVideoOff,
                    modifier = Modifier.fillMaxSize()
                )

                // Flip button on PIP
                IconButton(
                    onClick = onFlipCamera,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color(0x99000000))
                ) {
                    Icon(
                        imageVector = Icons.Default.Cameraswitch,
                        contentDescription = "Flip",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // Top Status Header (Stranger Info, Duration, Report)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Stranger Identity Pill
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xCC0F172A))
                    .border(1.dp, Color(0x3300E5FF), RoundedCornerShape(20.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = stranger?.flag ?: "🌍",
                    fontSize = 18.sp
                )
                Spacer(modifier = Modifier.width(6.dp))
                Column {
                    Text(
                        text = stranger?.name ?: "Stranger",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "${stranger?.country ?: "Global"} • ${stranger?.interest ?: "Chat"}",
                        fontSize = 10.sp,
                        color = TextSecondary
                    )
                }
            }

            // Duration Pill & Report Button
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0x990F172A))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (isConnected) durationFormatted else "Connecting...",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isConnected) NeonCyan else TextSecondary
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = { showReportDialog = true },
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(0x990F172A))
                        .testTag("report_stranger_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Flag,
                        contentDescription = "Report Stranger",
                        tint = NeonRose,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // Floating Reactions Animation Overlay
        FloatingReactionsOverlay(
            reactions = floatingReactions,
            modifier = Modifier.fillMaxSize()
        )

        // Disconnected Overlay Prompt (When partner leaves)
        if (isDisconnected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xAA090D16)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Text(
                        text = "Stranger Disconnected",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Your chat partner left the conversation.",
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Button(
                        onClick = onSkip,
                        colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier.testTag("find_next_stranger_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = null,
                            tint = Color.Black
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Find Next Stranger",
                            color = Color.Black,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Bottom Controls Bar
        VideoControlsBar(
            isMuted = isMuted,
            isVideoOff = isVideoOff,
            onSkip = onSkip,
            onToggleMute = onToggleMute,
            onToggleVideo = onToggleVideo,
            onFlipCamera = onFlipCamera,
            onToggleChat = onToggleChat,
            onSendReaction = onSendReaction,
            onReport = { showReportDialog = true },
            onLeave = onLeave,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
        )

        // Chat Overlay Drawer (Animated slide up)
        AnimatedVisibility(
            visible = isChatDrawerOpen,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
        ) {
            ChatOverlay(
                messages = chatMessages,
                onSendMessage = onSendChatMessage,
                onClose = onToggleChat
            )
        }

        // Report & Block Dialog
        if (showReportDialog) {
            ReportBlockDialog(
                strangerName = stranger?.name ?: "Stranger",
                onConfirmReport = { reason ->
                    showReportDialog = false
                    onReportStranger(reason)
                },
                onDismiss = { showReportDialog = false }
            )
        }
    }
}

@Composable
fun FloatingReactionsOverlay(
    reactions: List<FloatingReaction>,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        reactions.forEach { reaction ->
            val animatedY by animateFloatAsState(
                targetValue = -300f,
                animationSpec = tween(durationMillis = 2200, easing = FastOutSlowInEasing),
                label = "reaction_y"
            )

            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .offset(
                        x = (reaction.xFraction * 300).dp,
                        y = animatedY.dp
                    )
            ) {
                Text(
                    text = reaction.emoji,
                    fontSize = 32.sp
                )
            }
        }
    }
}
