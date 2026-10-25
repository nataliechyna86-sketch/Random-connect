package com.example.webrtc

import android.content.Context
import android.util.Log
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.Camera1Enumerator
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraEnumerator
import org.webrtc.CameraVideoCapturer
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RendererCommon
import org.webrtc.RtpReceiver
import org.webrtc.RtpTransceiver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoCapturer
import org.webrtc.VideoSource
import org.webrtc.VideoTrack
import org.webrtc.audio.JavaAudioDeviceModule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val TAG = "WebRtcManager"

class WebRtcManager(private val context: Context) {

    val eglBase: EglBase by lazy { EglBase.create() }
    val appAudioManager by lazy { AppAudioManager(context) }

    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null

    private var videoCapturer: VideoCapturer? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null
    private var videoSource: VideoSource? = null
    private var audioSource: AudioSource? = null

    private val _localVideoTrackState = MutableStateFlow<VideoTrack?>(null)
    val localVideoTrackState: StateFlow<VideoTrack?> = _localVideoTrackState.asStateFlow()

    private val _remoteVideoTrackState = MutableStateFlow<VideoTrack?>(null)
    val remoteVideoTrackState: StateFlow<VideoTrack?> = _remoteVideoTrackState.asStateFlow()

    var localVideoTrack: VideoTrack?
        get() = _localVideoTrackState.value
        private set(value) { _localVideoTrackState.value = value }

    var localAudioTrack: AudioTrack? = null
        private set

    var remoteVideoTrack: VideoTrack?
        get() = _remoteVideoTrackState.value
        private set(value) { _remoteVideoTrackState.value = value }

    val isPeerConnectionActive: Boolean
        get() = peerConnection != null

    private val queuedRemoteCandidates = mutableListOf<IceCandidate>()
    private var isRemoteDescriptionSet = false

    private var isFrontCamera = true
    private var isMuted = false
    private var isVideoEnabled = true

    var onIceCandidateGenerated: ((IceCandidate) -> Unit)? = null
    var onRemoteTrackReceived: ((VideoTrack) -> Unit)? = null
    var onConnectionStateChanged: ((PeerConnection.PeerConnectionState) -> Unit)? = null

    init {
        try {
            initPeerConnectionFactory()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize PeerConnectionFactory", e)
        }
    }

    private fun initPeerConnectionFactory() {
        val initOptions = PeerConnectionFactory.InitializationOptions.builder(context)
            .setEnableInternalTracer(false)
            .createInitializationOptions()
        PeerConnectionFactory.initialize(initOptions)

        val options = PeerConnectionFactory.Options()
        val encoderFactory = DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true)
        val decoderFactory = DefaultVideoDecoderFactory(eglBase.eglBaseContext)

        val audioDeviceModule = try {
            JavaAudioDeviceModule.builder(context)
                .setUseHardwareAcousticEchoCanceler(true)
                .setUseHardwareNoiseSuppressor(true)
                .createAudioDeviceModule()
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing JavaAudioDeviceModule", e)
            null
        }

        val factoryBuilder = PeerConnectionFactory.builder()
            .setOptions(options)
            .setVideoEncoderFactory(encoderFactory)
            .setVideoDecoderFactory(decoderFactory)

        audioDeviceModule?.let {
            factoryBuilder.setAudioDeviceModule(it)
        }

        peerConnectionFactory = factoryBuilder.createPeerConnectionFactory()
    }

    fun startLocalMedia() {
        val factory = peerConnectionFactory ?: return

        // Initialize Audio
        if (localAudioTrack == null) {
            try {
                val audioConstraints = MediaConstraints().apply {
                    mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
                    mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl", "true"))
                    mandatory.add(MediaConstraints.KeyValuePair("googHighpassFilter", "true"))
                    mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
                }
                audioSource = factory.createAudioSource(audioConstraints)
                localAudioTrack = factory.createAudioTrack("ARDAMSa0", audioSource).apply {
                    setEnabled(!isMuted)
                }
                Log.d(TAG, "Local audio track created")
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing local audio", e)
            }
        }

        // Initialize Video
        if (localVideoTrack == null) {
            try {
                if (videoCapturer == null) {
                    videoCapturer = createCameraCapturer()
                }

                if (videoCapturer != null) {
                    surfaceTextureHelper = SurfaceTextureHelper.create("WebRtcCaptureThread", eglBase.eglBaseContext)
                    videoSource = factory.createVideoSource(videoCapturer!!.isScreencast)
                    videoCapturer?.initialize(surfaceTextureHelper, context, videoSource?.capturerObserver)
                    
                    try {
                        videoCapturer?.startCapture(640, 480, 30)
                        Log.d(TAG, "Camera capture started at 640x480@30fps")
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed 640x480@30, fallback to 480x360@24", e)
                        try {
                            videoCapturer?.startCapture(480, 360, 24)
                            Log.d(TAG, "Camera capture started at 480x360@24fps")
                        } catch (e2: Exception) {
                            Log.e(TAG, "Camera startCapture failed completely", e2)
                        }
                    }

                    localVideoTrack = factory.createVideoTrack("ARDAMSv0", videoSource).apply {
                        setEnabled(isVideoEnabled)
                    }
                    Log.d(TAG, "Local video track created successfully")
                } else {
                    Log.e(TAG, "videoCapturer is null, cannot start local video")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing camera video capturer", e)
            }
        }
    }

    private fun createCameraCapturer(): VideoCapturer? {
        val enumerator: CameraEnumerator = try {
            if (Camera2Enumerator.isSupported(context)) {
                Log.d(TAG, "Using Camera2Enumerator")
                Camera2Enumerator(context)
            } else {
                Log.d(TAG, "Camera2 not supported, falling back to Camera1Enumerator")
                Camera1Enumerator(true)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error checking Camera2 support, using Camera1Enumerator", e)
            Camera1Enumerator(true)
        }

        val deviceNames = enumerator.deviceNames
        Log.d(TAG, "Available camera devices: ${deviceNames.joinToString()}")

        // 1. Look for front camera first
        for (deviceName in deviceNames) {
            if (enumerator.isFrontFacing(deviceName)) {
                try {
                    val capturer = enumerator.createCapturer(deviceName, null)
                    if (capturer != null) {
                        isFrontCamera = true
                        Log.d(TAG, "Created front camera capturer for $deviceName")
                        return capturer
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to create front capturer for $deviceName", e)
                }
            }
        }

        // 2. Fallback to back camera
        for (deviceName in deviceNames) {
            if (enumerator.isBackFacing(deviceName)) {
                try {
                    val capturer = enumerator.createCapturer(deviceName, null)
                    if (capturer != null) {
                        isFrontCamera = false
                        Log.d(TAG, "Created back camera capturer for $deviceName")
                        return capturer
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to create back capturer for $deviceName", e)
                }
            }
        }

        // 3. Fallback to any camera available
        for (deviceName in deviceNames) {
            try {
                val capturer = enumerator.createCapturer(deviceName, null)
                if (capturer != null) {
                    Log.d(TAG, "Created fallback camera capturer for $deviceName")
                    return capturer
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to create fallback capturer for $deviceName", e)
            }
        }

        return null
    }

    fun flipCamera(onComplete: ((Boolean) -> Unit)? = null) {
        val cameraCapturer = videoCapturer as? CameraVideoCapturer ?: return
        cameraCapturer.switchCamera(object : CameraVideoCapturer.CameraSwitchHandler {
            override fun onCameraSwitchDone(isFront: Boolean) {
                isFrontCamera = isFront
                onComplete?.invoke(isFront)
            }

            override fun onCameraSwitchError(errorDescription: String?) {
                Log.e(TAG, "Camera switch error: $errorDescription")
            }
        })
    }

    fun toggleMute(muted: Boolean) {
        isMuted = muted
        localAudioTrack?.setEnabled(!muted)
    }

    fun toggleVideo(enabled: Boolean) {
        isVideoEnabled = enabled
        localVideoTrack?.setEnabled(enabled)
    }

    fun createPeerConnection() {
        val factory = peerConnectionFactory ?: return

        closePeerConnection()

        val iceServers = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun2.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun3.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun4.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun.relay.metered.ca:80").createIceServer()
        )

        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }

        val observer = object : PeerConnection.Observer {
            override fun onSignalingChange(state: PeerConnection.SignalingState?) {
                Log.d(TAG, "Signaling State: $state")
            }

            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                Log.d(TAG, "ICE Connection State: $state")
            }

            override fun onIceConnectionReceivingChange(receiving: Boolean) {}

            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {
                Log.d(TAG, "ICE Gathering State: $state")
            }

            override fun onIceCandidate(candidate: IceCandidate?) {
                candidate?.let {
                    Log.d(TAG, "Generated ICE Candidate: ${it.sdpMid} -> ${it.sdp}")
                    onIceCandidateGenerated?.invoke(it)
                }
            }

            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}

            override fun onAddStream(stream: MediaStream?) {
                Log.d(TAG, "onAddStream called: ${stream?.id}")
                stream?.audioTracks?.forEach { audioTrack ->
                    audioTrack.setEnabled(true)
                    audioTrack.setVolume(10.0)
                    Log.d(TAG, "Remote AudioTrack from stream enabled with full volume")
                }
                stream?.videoTracks?.firstOrNull()?.let { track ->
                    Log.d(TAG, "Found VideoTrack from stream: ${track.id()}")
                    remoteVideoTrack = track
                    onRemoteTrackReceived?.invoke(track)
                }
            }

            override fun onRemoveStream(stream: MediaStream?) {}

            override fun onDataChannel(channel: org.webrtc.DataChannel?) {}

            override fun onRenegotiationNeeded() {}

            override fun onAddTrack(receiver: RtpReceiver?, mediaStreams: Array<out MediaStream>?) {
                val track = receiver?.track()
                Log.d(TAG, "onAddTrack called: ${track?.kind()} id: ${track?.id()}")
                if (track is VideoTrack) {
                    remoteVideoTrack = track
                    onRemoteTrackReceived?.invoke(track)
                } else if (track is AudioTrack) {
                    track.setEnabled(true)
                    track.setVolume(10.0)
                    Log.d(TAG, "Remote AudioTrack received, enabled with full volume")
                }
            }

            override fun onTrack(transceiver: RtpTransceiver?) {
                val track = transceiver?.receiver?.track()
                Log.d(TAG, "onTrack called: ${track?.kind()} id: ${track?.id()}")
                if (track is VideoTrack) {
                    remoteVideoTrack = track
                    onRemoteTrackReceived?.invoke(track)
                } else if (track is AudioTrack) {
                    track.setEnabled(true)
                    track.setVolume(10.0)
                    Log.d(TAG, "Remote AudioTrack received via onTrack, enabled with full volume")
                }
            }

            override fun onConnectionChange(newState: PeerConnection.PeerConnectionState?) {
                Log.d(TAG, "PeerConnection State: $newState")
                newState?.let { onConnectionStateChanged?.invoke(it) }
            }
        }

        peerConnection = factory.createPeerConnection(rtcConfig, observer)

        // Activate loudspeaker and communication audio routing
        appAudioManager.start()

        // Add local tracks with explicit stream label for Unified Plan
        val streamIds = listOf("ARDAMS")
        localVideoTrack?.let { peerConnection?.addTrack(it, streamIds) }
        localAudioTrack?.let { peerConnection?.addTrack(it, streamIds) }
    }

    fun createOffer(onOfferCreated: (SessionDescription) -> Unit) {
        val pc = peerConnection ?: return
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
        }

        pc.createOffer(object : SdpObserverAdapter("createOffer") {
            override fun onCreateSuccess(desc: SessionDescription?) {
                desc?.let { sessionDesc ->
                    pc.setLocalDescription(object : SdpObserverAdapter("setLocalDescription") {
                        override fun onSetSuccess() {
                            onOfferCreated(sessionDesc)
                        }
                    }, sessionDesc)
                }
            }
        }, constraints)
    }

    fun handleOffer(offerSdp: String, onAnswerCreated: (SessionDescription) -> Unit) {
        val pc = peerConnection ?: return
        val sdp = SessionDescription(SessionDescription.Type.OFFER, offerSdp)

        pc.setRemoteDescription(object : SdpObserverAdapter("setRemoteOffer") {
            override fun onSetSuccess() {
                drainQueuedCandidates()
                val constraints = MediaConstraints().apply {
                    mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
                    mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
                }
                pc.createAnswer(object : SdpObserverAdapter("createAnswer") {
                    override fun onCreateSuccess(desc: SessionDescription?) {
                        desc?.let { answerDesc ->
                            pc.setLocalDescription(object : SdpObserverAdapter("setLocalAnswer") {
                                override fun onSetSuccess() {
                                    onAnswerCreated(answerDesc)
                                }
                            }, answerDesc)
                        }
                    }
                }, constraints)
            }
        }, sdp)
    }

    fun handleAnswer(answerSdp: String) {
        val pc = peerConnection ?: return
        val sdp = SessionDescription(SessionDescription.Type.ANSWER, answerSdp)
        pc.setRemoteDescription(object : SdpObserverAdapter("setRemoteAnswer") {
            override fun onSetSuccess() {
                drainQueuedCandidates()
            }
        }, sdp)
    }

    fun addRemoteIceCandidate(sdpMid: String, sdpMLineIndex: Int, sdp: String) {
        val candidate = IceCandidate(sdpMid, sdpMLineIndex, sdp)
        val pc = peerConnection
        if (pc != null && isRemoteDescriptionSet && pc.remoteDescription != null) {
            Log.d(TAG, "Adding remote ICE candidate: $sdpMid")
            pc.addIceCandidate(candidate)
        } else {
            Log.d(TAG, "Queuing ICE candidate (remote description not ready yet): $sdpMid")
            queuedRemoteCandidates.add(candidate)
        }
    }

    private fun drainQueuedCandidates() {
        isRemoteDescriptionSet = true
        val pc = peerConnection ?: return
        if (queuedRemoteCandidates.isNotEmpty()) {
            Log.d(TAG, "Draining ${queuedRemoteCandidates.size} queued ICE candidates")
            for (candidate in queuedRemoteCandidates) {
                pc.addIceCandidate(candidate)
            }
            queuedRemoteCandidates.clear()
        }
    }

    fun setupSurfaceRenderer(renderer: SurfaceViewRenderer, isMirror: Boolean) {
        try {
            renderer.init(eglBase.eglBaseContext, null)
            renderer.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
            renderer.setMirror(isMirror)
            renderer.setEnableHardwareScaler(true)
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing SurfaceViewRenderer", e)
        }
    }

    fun closePeerConnection() {
        try {
            appAudioManager.stop()
            isRemoteDescriptionSet = false
            queuedRemoteCandidates.clear()
            peerConnection?.close()
            peerConnection = null
            remoteVideoTrack = null
        } catch (e: Exception) {
            Log.e(TAG, "Error closing peer connection", e)
        }
    }

    fun release() {
        closePeerConnection()
        try {
            videoCapturer?.stopCapture()
            videoCapturer?.dispose()
            videoCapturer = null

            surfaceTextureHelper?.dispose()
            surfaceTextureHelper = null

            localVideoTrack?.dispose()
            localVideoTrack = null

            localAudioTrack?.dispose()
            localAudioTrack = null

            videoSource?.dispose()
            videoSource = null

            audioSource?.dispose()
            audioSource = null

            peerConnectionFactory?.dispose()
            peerConnectionFactory = null

            eglBase.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing WebRtcManager resources", e)
        }
    }
}

open class SdpObserverAdapter(private val tag: String) : org.webrtc.SdpObserver {
    override fun onCreateSuccess(p0: SessionDescription?) {
        Log.d("SdpObserver", "$tag: onCreateSuccess")
    }

    override fun onSetSuccess() {
        Log.d("SdpObserver", "$tag: onSetSuccess")
    }

    override fun onCreateFailure(p0: String?) {
        Log.e("SdpObserver", "$tag: onCreateFailure: $p0")
    }

    override fun onSetFailure(p0: String?) {
        Log.e("SdpObserver", "$tag: onSetFailure: $p0")
    }
}
