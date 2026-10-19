# RandomCam WebRTC Signaling & Matchmaking Server

This server handles 1-on-1 random stranger video chat matchmaking, WebRTC SDP exchange (offer/answer), ICE candidate relay, live text chat, and floating emoji reactions for the **RandomCam** Android app.

---

## Option 1: Deploy for FREE on Render (Fastest & Simplest)

Render provides free hosting with full WebSocket/Socket.IO support:

1. Sign up at [render.com](https://render.com) (free).
2. Click **New +** → **Web Service**.
3. Select **Build and deploy from a Git repository** and connect your GitHub repo.
4. Set the following settings:
   - **Name**: `randomcam-signaling`
   - **Root Directory**: `server`
   - **Runtime**: `Node`
   - **Build Command**: `npm install`
   - **Start Command**: `npm start`
   - **Instance Type**: **Free**
5. Click **Create Web Service**.
6. Render will build and launch your server in ~60 seconds and give you a public URL like:
   `https://randomcam-signaling.onrender.com`

---

## Option 2: Deploy for FREE on Google Cloud Run

Google Cloud Run has a permanent **Free Tier** (2 Million requests/month and 360,000 vCPU-seconds free every month):

### Using Google Cloud Console (Browser):
1. Go to [console.cloud.google.com/run](https://console.cloud.google.com/run).
2. Click **Create Service**.
3. Choose **Continuously deploy from a repository** (Cloud Build) and select your GitHub repo.
4. In Build Configuration:
   - Source directory: `/server`
   - Build Type: `Dockerfile` (`/server/Dockerfile`)
5. In Service Configuration:
   - Allow unauthenticated invocations (public).
   - Port: `3000`
   - In **Session Affinity** / **WebSockets**: Cloud Run supports WebSockets out of the box.
6. Click **Create**. You'll receive a free HTTPS URL like:
   `https://randomcam-signaling-xxxxxx.a.run.app`

### Using `gcloud` CLI (1 Command):
```bash
cd server
gcloud run deploy randomcam-server \
  --source . \
  --platform managed \
  --region us-central1 \
  --allow-unauthenticated \
  --port 3000
```

---

## How to Connect the Android App to Your Deployed Server

Once your server is deployed on Render or Cloud Run:

1. Open **RandomCam** on your Android device.
2. In the top-right of the lobby, tap the **Server Status pill** (showing *"● 1,842 online"*).
3. In the modal:
   - Toggle **OFF** *"Interactive Sandbox Mode"*.
   - In **Signaling Server URL**, paste your public URL (e.g. `https://randomcam-signaling.onrender.com` or `https://randomcam-signaling-xxxxxx.a.run.app`).
   - Tap **Save & Apply**.
4. The server pill will turn green: **"● Connected"**.
5. Install the app on two different phones (or share your APK with a friend):
   - Have both people tap **"START VIDEO CHAT"**.
   - The server will match them together and establish a direct peer-to-peer WebRTC video and audio call!
