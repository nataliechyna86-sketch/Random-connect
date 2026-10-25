package com.example.ui.components

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.model.StrangerInfo
import com.example.ui.theme.DarkNavyBackground
import com.example.ui.theme.DarkNavyElevated
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonViolet
import com.example.ui.theme.TextSecondary
import com.example.webrtc.WebRtcManager
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun LocalVideoSurfaceView(
    webRtcManager: WebRtcManager,
    isVideoOff: Boolean,
    modifier: Modifier = Modifier
) {
    val localTrack by webRtcManager.localVideoTrackState.collectAsStateWithLifecycle()

    Box(modifier = modifier) {
        if (!isVideoOff && localTrack != null) {
            AndroidView(
                factory = { ctx ->
                    SurfaceViewRenderer(ctx).apply {
                        id = android.view.View.generateViewId()
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        // Critical: PIP overlay must be on top of the remote video SurfaceView
                        setZOrderMediaOverlay(true)
                        setZOrderOnTop(true)
                        webRtcManager.setupSurfaceRenderer(this, isMirror = true)
                        localTrack?.addSink(this)
                    }
                },
                update = { renderer ->
                    renderer.setMirror(true)
                },
                onRelease = { renderer ->
                    try {
                        localTrack?.removeSink(renderer)
                        renderer.release()
                    } catch (_: Exception) {}
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(DarkNavyElevated),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.VideocamOff,
                    contentDescription = "Camera Off",
                    tint = TextSecondary,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}

@Composable
fun RemoteVideoSurfaceView(
    webRtcManager: WebRtcManager,
    stranger: StrangerInfo?,
    modifier: Modifier = Modifier
) {
    val remoteTrack by webRtcManager.remoteVideoTrackState.collectAsStateWithLifecycle()

    Box(modifier = modifier) {
        if (remoteTrack != null) {
            AndroidView(
                factory = { ctx ->
                    SurfaceViewRenderer(ctx).apply {
                        id = android.view.View.generateViewId()
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        // Background full-screen surface
                        setZOrderMediaOverlay(false)
                        webRtcManager.setupSurfaceRenderer(this, isMirror = false)
                        remoteTrack?.addSink(this)
                    }
                },
                update = { renderer ->
                    renderer.setMirror(false)
                },
                onRelease = { renderer ->
                    try {
                        remoteTrack?.removeSink(renderer)
                        renderer.release()
                    } catch (_: Exception) {}
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // Live Stranger Video Canvas Stream with animated ambient waves while connecting
            LiveStrangerCanvas(stranger = stranger)
        }
    }
}

@Composable
fun LiveStrangerCanvas(
    stranger: StrangerInfo?,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "stranger_canvas")
    val pulse by transition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val waveOffset by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "waveOffset"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DarkNavyBackground),
        contentAlignment = Alignment.Center
    ) {
        // Ambient background audio-visual particle wave
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val baseRadius = size.minDimension * 0.35f * pulse

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        NeonViolet.copy(alpha = 0.35f),
                        NeonCyan.copy(alpha = 0.15f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = baseRadius * 1.5f
                ),
                center = center,
                radius = baseRadius * 1.5f
            )

            // Animated subtle concentric sound rings
            for (i in 1..3) {
                val ringRadius = baseRadius * (0.8f + i * 0.25f)
                drawCircle(
                    color = NeonCyan.copy(alpha = (0.3f / i)),
                    center = center,
                    radius = ringRadius,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
                )
            }
        }

        // Stranger Avatar Profile Card
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(110.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(NeonCyan, NeonViolet)
                        )
                    )
                    .padding(4.dp)
                    .clip(CircleShape)
                    .background(DarkNavyElevated),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stranger?.flag ?: "🌍",
                    fontSize = 54.sp
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stranger?.name ?: "Connecting Stranger...",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Text(
                text = "${stranger?.country ?: "Worldwide"} • ${stranger?.interest ?: "Live Video"}",
                fontSize = 14.sp,
                color = TextSecondary
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Simulated live audio feed indicator
            LiveAudioPill()
        }
    }
}

@Composable
fun LiveAudioPill() {
    val transition = rememberInfiniteTransition(label = "audio_bars")
    val b1 by transition.animateFloat(
        initialValue = 6f,
        targetValue = 18f,
        animationSpec = infiniteRepeatable(tween(350, easing = LinearEasing), RepeatMode.Reverse),
        label = "b1"
    )
    val b2 by transition.animateFloat(
        initialValue = 18f,
        targetValue = 8f,
        animationSpec = infiniteRepeatable(tween(420, easing = LinearEasing), RepeatMode.Reverse),
        label = "b2"
    )
    val b3 by transition.animateFloat(
        initialValue = 10f,
        targetValue = 22f,
        animationSpec = infiniteRepeatable(tween(310, easing = LinearEasing), RepeatMode.Reverse),
        label = "b3"
    )

    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(Color(0x3300E5FF))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Canvas(modifier = Modifier.size(width = 40.dp, height = 24.dp)) {
            val barWidth = 4.dp.toPx()
            val centerY = size.height / 2f

            drawLine(
                color = NeonCyan,
                start = Offset(6.dp.toPx(), centerY - b1.dp.toPx() / 2f),
                end = Offset(6.dp.toPx(), centerY + b1.dp.toPx() / 2f),
                strokeWidth = barWidth
            )
            drawLine(
                color = NeonCyan,
                start = Offset(16.dp.toPx(), centerY - b2.dp.toPx() / 2f),
                end = Offset(16.dp.toPx(), centerY + b2.dp.toPx() / 2f),
                strokeWidth = barWidth
            )
            drawLine(
                color = NeonCyan,
                start = Offset(26.dp.toPx(), centerY - b3.dp.toPx() / 2f),
                end = Offset(26.dp.toPx(), centerY + b3.dp.toPx() / 2f),
                strokeWidth = barWidth
            )
        }
    }
}
