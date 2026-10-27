package com.example.casting

import android.content.Context
import android.util.Log
import com.example.network.SignalingMessage
import com.example.network.SignalingServer
import com.example.webrtc.WebRtcManager
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.webrtc.*

class StreamHub(private val context: Context) {

    private val signalingServer = SignalingServer()
    private val webRtcManager = WebRtcManager(context)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    val serverIp: String get() = signalingServer.getLocalIp()
    val port: Int = 8889

    private val _activeClients = MutableStateFlow<List<String>>(emptyList())
    val activeClients = _activeClients.asStateFlow()

    private val clientConnections = mutableMapOf<String, ClientWebRtcSession>()
    
    private val httpClient = HttpClient(CIO) {
        install(WebSockets)
    }
    private var signalingSession: DefaultClientWebSocketSession? = null

    fun start() {
        signalingServer.start()
        
        scope.launch {
            // Hub connects to its own signaling server to listen for messages
            delay(500) // Wait for server to start
            try {
                signalingSession = httpClient.webSocketSession(host = "127.0.0.1", port = 8889, path = "/signaling/HUB")
                signalingSession?.incoming?.consumeEach { frame ->
                    if (frame is Frame.Text) {
                        val message = Json.decodeFromString<SignalingMessage>(frame.readText())
                        handleSignalingMessage(message)
                    }
                }
            } catch (e: Exception) {
                Log.e("StreamHub", "Failed to connect to signaling", e)
            }
        }
    }

    private fun handleSignalingMessage(message: SignalingMessage) {
        val from = message.from
        when (message.type) {
            "join" -> {
                Log.d("StreamHub", "Client $from joined")
                // A new client joined, we wait for their offer
            }
            "offer" -> {
                val session = clientConnections.getOrPut(from) {
                    ClientWebRtcSession(from).also {
                        updateClientList()
                    }
                }
                session.handleOffer(message.data!!)
            }
            "candidate" -> {
                val candidate = Json.decodeFromString<IceCandidate>(message.data!!)
                clientConnections[from]?.addIceCandidate(candidate)
            }
        }
    }

    private fun updateClientList() {
        _activeClients.value = clientConnections.keys.toList()
    }

    fun initSurface(clientId: String, view: SurfaceViewRenderer) {
        clientConnections[clientId]?.setRenderer(view)
    }

    fun stop() {
        scope.cancel()
        signalingServer.stop()
        clientConnections.values.forEach { it.release() }
        clientConnections.clear()
        webRtcManager.dispose()
    }

    inner class ClientWebRtcSession(val id: String) {
        private var peerConnection: PeerConnection? = null
        private var renderer: SurfaceViewRenderer? = null
        private var remoteVideoTrack: VideoTrack? = null

        init {
            peerConnection = webRtcManager.createPeerConnection(object : PeerConnection.Observer {
                override fun onIceCandidate(candidate: IceCandidate) {
                    sendMessage(SignalingMessage("candidate", from = "HUB", to = id, data = Json.encodeToString(candidate)))
                }
                override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {
                    val track = receiver?.track()
                    if (track is VideoTrack) {
                        remoteVideoTrack = track
                        renderer?.let { track.addSink(it) }
                    }
                }
                override fun onDataChannel(p0: DataChannel?) {}
                override fun onIceConnectionChange(p0: PeerConnection.IceConnectionState?) {
                    if (p0 == PeerConnection.IceConnectionState.DISCONNECTED || p0 == PeerConnection.IceConnectionState.FAILED) {
                        removeSession()
                    }
                }
                override fun onIceConnectionReceivingChange(p0: Boolean) {}
                override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {}
                override fun onSignalingChange(p0: PeerConnection.SignalingState?) {}
                override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) {}
                override fun onAddStream(p0: MediaStream?) {}
                override fun onRemoveStream(p0: MediaStream?) {}
                override fun onRenegotiationNeeded() {}
            })
        }

        fun handleOffer(sdpStr: String) {
            val sdp = SessionDescription(SessionDescription.Type.OFFER, sdpStr)
            peerConnection?.setRemoteDescription(object : SdpObserver {
                override fun onSetSuccess() {
                    createAnswer()
                }
                override fun onCreateSuccess(p0: SessionDescription?) {}
                override fun onCreateFailure(p0: String?) {}
                override fun onSetFailure(p0: String?) {}
            }, sdp)
        }

        private fun createAnswer() {
            peerConnection?.createAnswer(object : SdpObserver {
                override fun onCreateSuccess(desc: SessionDescription) {
                    peerConnection?.setLocalDescription(this, desc)
                    sendMessage(SignalingMessage("answer", from = "HUB", to = id, data = desc.description))
                }
                override fun onSetSuccess() {}
                override fun onCreateFailure(p0: String?) {}
                override fun onSetFailure(p0: String?) {}
            }, MediaConstraints())
        }

        fun addIceCandidate(candidate: IceCandidate) {
            peerConnection?.addIceCandidate(candidate)
        }

        fun setRenderer(view: SurfaceViewRenderer) {
            renderer = view
            view.init(webRtcManager.eglBase.eglBaseContext, null)
            remoteVideoTrack?.addSink(view)
        }

        private fun sendMessage(message: SignalingMessage) {
            scope.launch {
                signalingSession?.send(Json.encodeToString(message))
            }
        }

        private fun removeSession() {
            clientConnections.remove(id)
            updateClientList()
            release()
        }

        fun release() {
            peerConnection?.dispose()
            peerConnection = null
            renderer?.release()
            renderer = null
        }
    }

    companion object {
        private const val TAG = "StreamHub"
    }
}
