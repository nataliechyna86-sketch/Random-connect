const express = require("express");
const http = require("http");
const { Server } = require("socket.io");
const cors = require("cors");

const app = express();
app.use(cors());
app.use(express.json());

const server = http.createServer(app);
const io = new Server(server, {
  cors: {
    origin: "*",
    methods: ["GET", "POST"]
  },
  pingInterval: 10000,
  pingTimeout: 5000,
  transports: ["websocket", "polling"]
});

const PORT = process.env.PORT || 8080;

// Matchmaking queues partitioned by interest topic ("All", "Gaming", etc.)
const queues = new Map();
// Active rooms: roomId -> { peer1: socketId, peer2: socketId, topic: string }
const activeRooms = new Map();
// User socket mapping: socketId -> { currentRoomId, queuedTopic }
const userState = new Map();

// Helper: Get or initialize a topic queue
function getQueue(topic) {
  const key = topic || "All";
  if (!queues.has(key)) {
    queues.set(key, []);
  }
  return queues.get(key);
}

// Random stranger identity generator for fun display
const sampleCountries = [
  { flag: "🇺🇸", country: "United States" },
  { flag: "🇬🇧", country: "United Kingdom" },
  { flag: "🇨🇦", country: "Canada" },
  { flag: "🇦🇺", country: "Australia" },
  { flag: "🇩🇪", country: "Germany" },
  { flag: "🇫🇷", country: "France" },
  { flag: "🇯🇵", country: "Japan" },
  { flag: "🇧🇷", country: "Brazil" },
  { flag: "🇪🇸", country: "Spain" },
  { flag: "🇮🇳", country: "India" }
];

function getRandomIdentity() {
  const item = sampleCountries[Math.floor(Math.random() * sampleCountries.length)];
  return {
    name: "Stranger #" + Math.floor(1000 + Math.random() * 9000),
    flag: item.flag,
    country: item.country
  };
}

// Health check endpoint for Render / Cloud Run / monitoring
app.get("/", (req, res) => {
  res.json({
    status: "ok",
    service: "RandomCam Signaling Server",
    onlineUsers: io.engine.clientsCount,
    activeCalls: activeRooms.size,
    queuedUsers: Array.from(queues.values()).reduce((sum, q) => sum + q.length, 0),
    uptime: Math.floor(process.uptime()) + "s"
  });
});

app.get("/health", (req, res) => {
  res.status(200).send("OK");
});

io.on("connection", (socket) => {
  console.log(`[+] User connected: ${socket.id} (Total: ${io.engine.clientsCount})`);
  userState.set(socket.id, { currentRoomId: null, queuedTopic: null });

  // Matchmaking request
  socket.on("join_matchmaking", (data) => {
    const topic = (data && data.topic) || "All";
    leaveCurrentRoom(socket);
    removeFromAllQueues(socket.id);

    console.log(`[Queue] ${socket.id} looking for match in [${topic}]`);

    // Match logic: check topic queue, or check "All" queue
    let queue = getQueue(topic);
    let partnerId = null;

    // Filter out disconnected sockets
    while (queue.length > 0 && !partnerId) {
      const candidateId = queue.shift();
      if (candidateId !== socket.id && io.sockets.sockets.has(candidateId)) {
        partnerId = candidateId;
      }
    }

    // If no match in specific topic and not "All", try "All" queue
    if (!partnerId && topic !== "All") {
      const allQueue = getQueue("All");
      while (allQueue.length > 0 && !partnerId) {
        const candidateId = allQueue.shift();
        if (candidateId !== socket.id && io.sockets.sockets.has(candidateId)) {
          partnerId = candidateId;
        }
      }
    }

    if (partnerId) {
      // Create new WebRTC room
      const roomId = `room_${Date.now()}_${Math.random().toString(36).substr(2, 6)}`;
      const partnerSocket = io.sockets.sockets.get(partnerId);

      socket.join(roomId);
      partnerSocket.join(roomId);

      activeRooms.set(roomId, { peer1: socket.id, peer2: partnerId, topic });
      userState.get(socket.id).currentRoomId = roomId;
      userState.get(partnerId).currentRoomId = roomId;

      const identity1 = getRandomIdentity();
      const identity2 = getRandomIdentity();

      console.log(`[Matched] ${socket.id} <--> ${partnerId} in ${roomId}`);

      // Peer 1 is the WebRTC offer initiator
      socket.emit("matched", {
        roomId,
        peerId: partnerId,
        isInitiator: true,
        name: identity2.name,
        flag: identity2.flag,
        country: identity2.country,
        interest: topic
      });

      // Peer 2 is the answer receiver
      partnerSocket.emit("matched", {
        roomId,
        peerId: socket.id,
        isInitiator: false,
        name: identity1.name,
        flag: identity1.flag,
        country: identity1.country,
        interest: topic
      });
    } else {
      // Put user in queue and notify client to show radar scanning animation
      queue.push(socket.id);
      userState.get(socket.id).queuedTopic = topic;
      socket.emit("waiting");
    }
  });

  // WebRTC Offer
  socket.on("offer", (data) => {
    if (!data || !data.roomId || !data.sdp) return;
    socket.to(data.roomId).emit("offer", {
      roomId: data.roomId,
      sdp: data.sdp
    });
  });

  // WebRTC Answer
  socket.on("answer", (data) => {
    if (!data || !data.roomId || !data.sdp) return;
    socket.to(data.roomId).emit("answer", {
      roomId: data.roomId,
      sdp: data.sdp
    });
  });

  // ICE Candidate
  socket.on("ice_candidate", (data) => {
    if (!data || !data.roomId) return;
    const candidateStr = data.candidate || data.sdp;
    if (!candidateStr) return;
    socket.to(data.roomId).emit("ice_candidate", {
      roomId: data.roomId,
      sdpMid: data.sdpMid || "",
      sdpMLineIndex: data.sdpMLineIndex != null ? data.sdpMLineIndex : 0,
      candidate: candidateStr,
      sdp: candidateStr
    });
  });

  // Real-time Text Chat
  socket.on("chat_message", (data) => {
    if (!data || !data.roomId || !data.text) return;
    socket.to(data.roomId).emit("chat_message", {
      id: "msg_" + Date.now(),
      senderName: "Stranger",
      text: data.text
    });
  });

  // Floating Emoji Reaction
  socket.on("reaction", (data) => {
    if (!data || !data.roomId || !data.emoji) return;
    socket.to(data.roomId).emit("reaction", data.emoji);
  });

  // Skip / Next stranger button
  socket.on("skip", (data) => {
    const roomId = data?.roomId || userState.get(socket.id)?.currentRoomId;
    console.log(`[Skip] User ${socket.id} skipped room: ${roomId}`);
    leaveCurrentRoom(socket);
  });

  // Disconnect handler
  socket.on("disconnect", () => {
    console.log(`[-] User disconnected: ${socket.id}`);
    leaveCurrentRoom(socket);
    removeFromAllQueues(socket.id);
    userState.delete(socket.id);
  });
});

// Clean up room and notify partner
function leaveCurrentRoom(socket) {
  const state = userState.get(socket.id);
  if (!state || !state.currentRoomId) return;

  const roomId = state.currentRoomId;
  const room = activeRooms.get(roomId);

  if (room) {
    const partnerId = room.peer1 === socket.id ? room.peer2 : room.peer1;
    const partnerSocket = io.sockets.sockets.get(partnerId);

    if (partnerSocket) {
      partnerSocket.emit("partner_disconnected", { roomId });
      partnerSocket.leave(roomId);
      const partnerState = userState.get(partnerId);
      if (partnerState) partnerState.currentRoomId = null;
    }

    activeRooms.delete(roomId);
  }

  socket.leave(roomId);
  state.currentRoomId = null;
}

// Remove from all waiting queues
function removeFromAllQueues(socketId) {
  for (const [topic, list] of queues.entries()) {
    const index = list.indexOf(socketId);
    if (index !== -1) {
      list.splice(index, 1);
    }
  }
}

server.listen(PORT, "0.0.0.0", () => {
  console.log(`===============================================`);
  console.log(`RandomCam Signaling Server running on port ${PORT}`);
  console.log(`Ready for real-time WebRTC matchmaking!`);
  console.log(`===============================================`);
});
