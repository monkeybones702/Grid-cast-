package com.example.casting

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.network.SignalingMessage
import com.example.webrtc.WebRtcManager
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.consumeEach
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.webrtc.*
import java.util.*

class ScreenCastService : Service() {

    private var isCasting = false
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private lateinit var webRtcManager: WebRtcManager
    private var peerConnection: PeerConnection? = null
    private var videoCapturer: VideoCapturer? = null
    private var localVideoTrack: VideoTrack? = null

    val clientId: String = UUID.randomUUID().toString().take(8)
    private val httpClient = HttpClient(CIO) {
        install(WebSockets)
    }
    private var signalingSession: DefaultClientWebSocketSession? = null

    private val binder = LocalBinder()

    inner class LocalBinder : Binder() {
        fun getService(): ScreenCastService = this@ScreenCastService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        webRtcManager = WebRtcManager(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopCasting()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    fun startCasting(resultCode: Int, data: Intent, hubAddress: String, hubSignalingPort: Int) {
        if (isCasting) return
        isCasting = true

        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, ScreenCastService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("GridCast Live Streaming")
            .setContentText("Broadcasting to $hubAddress:$hubSignalingPort (Background Active)")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setContentIntent(openAppPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop Stream", stopPendingIntent)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        serviceScope.launch {
            try {
                connectSignaling(hubAddress, hubSignalingPort)
                setupWebRtc(resultCode, data)
            } catch (e: Exception) {
                Log.e(TAG, "WebRTC setup failed", e)
                stopCasting()
            }
        }
    }

    private suspend fun connectSignaling(host: String, port: Int) {
        signalingSession = httpClient.webSocketSession(host = host, port = port, path = "/signaling/$clientId")

        serviceScope.launch {
            signalingSession?.incoming?.consumeEach { frame ->
                if (frame is Frame.Text) {
                    val message = Json.decodeFromString<SignalingMessage>(frame.readText())
                    handleSignalingMessage(message)
                }
            }
        }

        // Notify Hub of join
        sendMessage(SignalingMessage("join", from = clientId, to = "HUB"))
    }

    private fun setupWebRtc(resultCode: Int, data: Intent) {
        peerConnection = webRtcManager.createPeerConnection(object : PeerConnection.Observer {
            override fun onIceCandidate(candidate: IceCandidate) {
                sendMessage(SignalingMessage("candidate", from = clientId, to = "HUB", data = Json.encodeToString(candidate)))
            }
            override fun onDataChannel(p0: DataChannel?) {}
            override fun onIceConnectionChange(p0: PeerConnection.IceConnectionState?) {}
            override fun onIceConnectionReceivingChange(p0: Boolean) {}
            override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {}
            override fun onSignalingChange(p0: PeerConnection.SignalingState?) {}
            override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) {}
            override fun onAddStream(p0: MediaStream?) {}
            override fun onRemoveStream(p0: MediaStream?) {}
            override fun onRenegotiationNeeded() {}
            override fun onAddTrack(p0: RtpReceiver?, p1: Array<out MediaStream>?) {}
        })

        // Screen Capture via MediaProjection
        videoCapturer = ScreenCapturerAndroid(data, object : MediaProjection.Callback() {
            override fun onStop() {
                stopCasting()
            }
        })

        val videoSource = webRtcManager.peerConnectionFactory.createVideoSource(videoCapturer!!.isScreencast)
        videoCapturer!!.initialize(
            SurfaceTextureHelper.create("CaptureThread", webRtcManager.eglBase.eglBaseContext),
            this,
            videoSource.capturerObserver
        )
        // 720p downscale for balanced performance, low latency & smooth 30fps
        videoCapturer!!.startCapture(720, 1280, 30)

        localVideoTrack = webRtcManager.peerConnectionFactory.createVideoTrack("VIDEO_TRACK", videoSource)
        peerConnection?.addTrack(localVideoTrack, listOf("STREAM_ID"))

        // Create Offer to Hub
        peerConnection?.createOffer(object : SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription) {
                peerConnection?.setLocalDescription(this, desc)
                sendMessage(SignalingMessage("offer", from = clientId, to = "HUB", data = desc.description))
            }
            override fun onSetSuccess() {}
            override fun onCreateFailure(p0: String?) {}
            override fun onSetFailure(p0: String?) {}
        }, MediaConstraints())
    }

    private fun handleSignalingMessage(message: SignalingMessage) {
        when (message.type) {
            "answer" -> {
                val sdp = SessionDescription(SessionDescription.Type.ANSWER, message.data)
                peerConnection?.setRemoteDescription(object : SdpObserver {
                    override fun onCreateSuccess(p0: SessionDescription?) {}
                    override fun onSetSuccess() {}
                    override fun onCreateFailure(p0: String?) {}
                    override fun onSetFailure(p0: String?) {}
                }, sdp)
            }
            "candidate" -> {
                val candidate = Json.decodeFromString<IceCandidate>(message.data!!)
                peerConnection?.addIceCandidate(candidate)
            }
        }
    }

    private fun sendMessage(message: SignalingMessage) {
        serviceScope.launch {
            signalingSession?.send(Json.encodeToString(message))
        }
    }

    fun stopCasting() {
        if (!isCasting) return
        isCasting = false
        try {
            videoCapturer?.stopCapture()
            videoCapturer?.dispose()
            videoCapturer = null
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping capturer: ${e.message}")
        }
        try {
            peerConnection?.dispose()
            peerConnection = null
        } catch (e: Exception) {
            Log.w(TAG, "Error disposing peerConnection: ${e.message}")
        }
        webRtcManager.dispose()
        serviceScope.cancel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopCasting()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "GridCast Background Screen Streaming",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows persistent status while your screen is being cast in background."
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        private const val TAG = "ScreenCastService"
        const val CHANNEL_ID = "gridcast_streaming_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_STOP = "com.example.casting.ACTION_STOP"
    }
}
