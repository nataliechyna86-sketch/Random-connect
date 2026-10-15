package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.model.ChatMessage
import com.example.model.MatchState
import com.example.model.StrangerInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("RandomCam", appName)
  }

  @Test
  fun `match state transitions work properly`() {
    val stranger = StrangerInfo(
      id = "test_1",
      name = "Maya",
      flag = "🇬🇧",
      country = "UK",
      interest = "Music",
      bio = "Musician"
    )

    val connectedState = MatchState.Connected(
      roomId = "room_123",
      stranger = stranger,
      callDurationSeconds = 45,
      isMuted = false,
      isVideoOff = false
    )

    assertEquals("room_123", connectedState.roomId)
    assertEquals("Maya", connectedState.stranger.name)
    assertEquals(45, connectedState.callDurationSeconds)
  }

  @Test
  fun `chat message model attributes are correct`() {
    val msg = ChatMessage(
      id = "m1",
      senderName = "You",
      text = "Hello stranger!",
      isMe = true
    )

    assertEquals("m1", msg.id)
    assertEquals("Hello stranger!", msg.text)
    assertTrue(msg.isMe)
  }
}
