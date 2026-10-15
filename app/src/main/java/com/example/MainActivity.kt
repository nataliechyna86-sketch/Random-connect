package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.model.MatchState
import com.example.ui.MainViewModel
import com.example.ui.components.RadarSearchingScreen
import com.example.ui.components.ServerSettingsDialog
import com.example.ui.screens.LobbyScreen
import com.example.ui.screens.VideoChatScreen
import com.example.ui.theme.DarkNavyBackground
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonViolet
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                RandomCamApp()
            }
        }
    }
}

@Composable
fun RandomCamApp(
    viewModel: MainViewModel = viewModel()
) {
    val context = LocalContext.current
    val matchState by viewModel.matchState.collectAsStateWithLifecycle()
    val serverConfig by viewModel.serverConfig.collectAsStateWithLifecycle()
    val selectedTopic by viewModel.selectedTopic.collectAsStateWithLifecycle()
    val chatMessages by viewModel.chatMessages.collectAsStateWithLifecycle()
    val floatingReactions by viewModel.floatingReactions.collectAsStateWithLifecycle()
    val isChatDrawerOpen by viewModel.isChatDrawerOpen.collectAsStateWithLifecycle()

    var showServerSettings by remember { mutableStateOf(false) }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasCameraPermission = permissions[Manifest.permission.CAMERA] ?: hasCameraPermission
        hasAudioPermission = permissions[Manifest.permission.RECORD_AUDIO] ?: hasAudioPermission
        if (hasCameraPermission && hasAudioPermission) {
            viewModel.onPermissionsGranted()
        }
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission || !hasAudioPermission) {
            permissionLauncher.launch(
                arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
            )
        } else {
            viewModel.onPermissionsGranted()
        }
    }

    // Handle back button when inside a call or radar searching
    BackHandler(enabled = matchState !is MatchState.Idle) {
        viewModel.leaveChat()
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkNavyBackground),
        containerColor = DarkNavyBackground
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (!hasCameraPermission && !hasAudioPermission) {
                // Permission Request Fallback Screen
                PermissionRequestScreen(
                    onRequestPermission = {
                        permissionLauncher.launch(
                            arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
                        )
                    }
                )
            } else {
                when (val state = matchState) {
                    is MatchState.Idle -> {
                        LobbyScreen(
                            webRtcManager = viewModel.webRtcManager,
                            serverConfig = serverConfig,
                            selectedTopic = selectedTopic,
                            onTopicSelected = { viewModel.setTopic(it) },
                            onStartMatchmaking = { viewModel.startMatchmaking() },
                            onOpenServerSettings = { showServerSettings = true },
                            onFlipCamera = { viewModel.flipCamera() }
                        )
                    }

                    is MatchState.Searching -> {
                        RadarSearchingScreen(
                            topic = state.topic,
                            secondsWaiting = state.secondsWaiting,
                            onCancel = { viewModel.leaveChat() }
                        )
                    }

                    is MatchState.Connecting,
                    is MatchState.Connected,
                    is MatchState.Disconnected -> {
                        VideoChatScreen(
                            matchState = state,
                            webRtcManager = viewModel.webRtcManager,
                            chatMessages = chatMessages,
                            floatingReactions = floatingReactions,
                            isChatDrawerOpen = isChatDrawerOpen,
                            onSkip = { viewModel.skipStranger() },
                            onToggleMute = { viewModel.toggleMute() },
                            onToggleVideo = { viewModel.toggleVideo() },
                            onFlipCamera = { viewModel.flipCamera() },
                            onToggleChat = { viewModel.toggleChatDrawer() },
                            onSendChatMessage = { viewModel.sendChatMessage(it) },
                            onSendReaction = { viewModel.sendReaction(it) },
                            onReportStranger = { viewModel.reportStranger(it) },
                            onLeave = { viewModel.leaveChat() }
                        )
                    }
                }
            }

            // Server Settings Dialog
            if (showServerSettings) {
                ServerSettingsDialog(
                    currentConfig = serverConfig,
                    onSaveConfig = { url, useSandbox ->
                        viewModel.updateServerConfig(url, useSandbox)
                    },
                    onDismiss = { showServerSettings = false }
                )
            }
        }
    }
}

@Composable
fun PermissionRequestScreen(
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkNavyBackground)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(NeonCyan, NeonViolet))),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Videocam,
                contentDescription = null,
                tint = Color.Black,
                modifier = Modifier.size(48.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Camera & Audio Access",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "RandomCam requires camera and microphone permissions so you can see and talk with strangers live over WebRTC.",
            fontSize = 14.sp,
            color = TextSecondary,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onRequestPermission,
            shape = RoundedCornerShape(24.dp),
            colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
            modifier = Modifier
                .height(50.dp)
                .testTag("grant_permissions_button")
        ) {
            Text(
                text = "Enable Camera & Microphone",
                color = Color.Black,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
        }
    }
}
