package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ServerConfig
import com.example.ui.theme.DarkNavyBackground
import com.example.ui.theme.DarkNavyElevated
import com.example.ui.theme.DarkNavySurface
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.NeonRose
import com.example.ui.theme.NeonViolet
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

private val NODE_SERVER_CODE = """
// RandomCam Signaling Server (Node.js + Socket.IO)
// Run: npm init -y && npm i express socket.io
const express = require('express');
const http = require('http');
const { Server } = require('socket.io');

const app = express();
const server = http.createServer(app);
const io = new Server(server, { cors: { origin: '*' } });

let waitingQueue = []; // [{ socketId, userId, topic }]

io.on('connection', (socket) => {
  console.log('User connected:', socket.id);

  socket.on('join_matchmaking', ({ userId, topic }) => {
    // Check if someone else is waiting
    const partnerIdx = waitingQueue.findIndex(p => p.socketId !== socket.id);
    if (partnerIdx !== -1) {
      const partner = waitingQueue.splice(partnerIdx, 1)[0];
      const roomId = 'room_' + socket.id + '_' + partner.socketId;

      socket.join(roomId);
      io.sockets.sockets.get(partner.socketId)?.join(roomId);

      // Notify both peers
      socket.emit('matched', { roomId, peerId: partner.userId, isInitiator: true });
      io.to(partner.socketId).emit('matched', { roomId, peerId: userId, isInitiator: false });
    } else {
      waitingQueue.push({ socketId: socket.id, userId, topic });
      socket.emit('waiting');
    }
  });

  socket.on('offer', (data) => socket.to(data.roomId).emit('offer', data));
  socket.on('answer', (data) => socket.to(data.roomId).emit('answer', data));
  socket.on('ice_candidate', (data) => socket.to(data.roomId).emit('ice_candidate', data));
  socket.on('chat_message', (data) => socket.to(data.roomId).emit('chat_message', data));
  socket.on('reaction', (data) => socket.to(data.roomId).emit('reaction', data));

  const cleanup = () => {
    waitingQueue = waitingQueue.filter(p => p.socketId !== socket.id);
    for (const room of socket.rooms) {
      if (room !== socket.id) socket.to(room).emit('partner_disconnected', { reason: 'Partner left' });
    }
  };

  socket.on('skip', (data) => {
    socket.to(data.roomId).emit('partner_disconnected', { reason: 'Skipped' });
    socket.leave(data.roomId);
  });

  socket.on('disconnect', cleanup);
});

server.listen(3000, () => console.log('RandomCam Signaling on :3000'));
"""

@Composable
fun ServerSettingsDialog(
    currentConfig: ServerConfig,
    onSaveConfig: (url: String, useSandbox: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var urlText by remember { mutableStateOf(currentConfig.serverUrl) }
    var useSandbox by remember { mutableStateOf(currentConfig.useSandboxFallback) }
    var showCodeSnippet by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkNavySurface,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Dns,
                    contentDescription = null,
                    tint = NeonCyan,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Signaling Server",
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                val (statusText, statusColor) = when {
                    useSandbox -> Pair("Interactive Sandbox Simulation", NeonViolet)
                    currentConfig.isConnected -> Pair("Connected to Live Server", NeonGreen)
                    currentConfig.isConnecting -> Pair("Connecting to Server...", Color(0xFFFFA726))
                    else -> Pair("Server Disconnected / Waking Up", NeonRose)
                }

                // Connection status pill
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(DarkNavyElevated)
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = statusText,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = statusColor
                        )
                        Text(
                            text = if (useSandbox) "Simulated partner response (local demo)" else currentConfig.serverUrl,
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                        if (!useSandbox && currentConfig.connectionError != null) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Notice: ${currentConfig.connectionError}",
                                fontSize = 10.sp,
                                color = Color(0xFFFFB4AB)
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(statusColor)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Sandbox toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Interactive Sandbox Mode",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Enable only for offline testing without a backend server",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }
                    Switch(
                        checked = useSandbox,
                        onCheckedChange = { useSandbox = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = NeonCyan,
                            checkedTrackColor = DarkNavyElevated
                        ),
                        modifier = Modifier.testTag("sandbox_mode_switch")
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Socket.io URL input
                Text(
                    text = "Live Server URL (Cloud Run or Render)",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = urlText,
                    onValueChange = { urlText = it },
                    placeholder = { Text("https://random-connect.onrender.com", color = TextSecondary) },
                    singleLine = true,
                    enabled = !useSandbox,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonCyan,
                        unfocusedBorderColor = Color(0x33FFFFFF),
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        disabledTextColor = TextSecondary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("server_url_input")
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Node.js server instructions & snippet toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { showCodeSnippet = !showCodeSnippet }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Code,
                        contentDescription = null,
                        tint = NeonCyan,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (showCodeSnippet) "Hide Node.js Server Code" else "View Node.js Server Code",
                        fontSize = 13.sp,
                        color = NeonCyan,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                if (showCodeSnippet) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(DarkNavyBackground)
                            .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(8.dp))
                            .padding(10.dp)
                    ) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "server.js",
                                    fontSize = 11.sp,
                                    color = TextSecondary,
                                    fontFamily = FontFamily.Monospace
                                )

                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .clickable {
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            val clip = ClipData.newPlainText("server.js", NODE_SERVER_CODE.trimIndent())
                                            clipboard.setPrimaryClip(clip)
                                            Toast.makeText(context, "Server code copied!", Toast.LENGTH_SHORT).show()
                                        }
                                        .padding(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = "Copy",
                                        tint = NeonCyan,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Copy",
                                        fontSize = 11.sp,
                                        color = NeonCyan
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = NODE_SERVER_CODE.trimIndent(),
                                fontSize = 10.sp,
                                color = Color(0xFF93C5FD),
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.heightIn(max = 160.dp).verticalScroll(rememberScrollState())
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSaveConfig(urlText.trim(), useSandbox)
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                modifier = Modifier.testTag("save_server_config_button")
            ) {
                Text("Save & Apply", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        }
    )
}
