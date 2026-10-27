package com.example.ui

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.casting.ScreenCastService
import com.example.casting.StreamHub
import com.example.firebase.FirebaseManager
import com.example.network.NetworkDiscovery
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class AppMode {
    UNSET, HUB, BROADCASTER, RECEIVER
}

enum class GridLayoutMode {
    AUTO, QUAD_2x2, STAGE_1x3, FOCUS
}

class MainViewModel(private val context: Context) : ViewModel() {

    private val _appMode = MutableStateFlow(AppMode.UNSET)
    val appMode = _appMode.asStateFlow()

    private val _isCasting = MutableStateFlow(false)
    val isCasting = _isCasting.asStateFlow()

    private val _availableHubs = MutableStateFlow<List<Pair<String, Int>>>(emptyList())
    val availableHubs = _availableHubs.asStateFlow()

    private val _activeStreams = MutableStateFlow<List<String>>(emptyList())
    val activeStreams = _activeStreams.asStateFlow()

    private val _showCastGuide = MutableStateFlow(false)
    val showCastGuide = _showCastGuide.asStateFlow()

    private val _selectedPlatform = MutableStateFlow("CHROMECAST")
    val selectedPlatform = _selectedPlatform.asStateFlow()

    // QR Code modal states
    private val _selectedStreamForQr = MutableStateFlow<String?>(null)
    val selectedStreamForQr = _selectedStreamForQr.asStateFlow()

    private val _showHostQr = MutableStateFlow(false)
    val showHostQr = _showHostQr.asStateFlow()

    private val _showAccountDialog = MutableStateFlow(false)
    val showAccountDialog = _showAccountDialog.asStateFlow()

    // TV-to-Phone receiver viewing URL
    private val _receiverViewingUrl = MutableStateFlow<String?>(null)
    val receiverViewingUrl = _receiverViewingUrl.asStateFlow()

    private val _currentLayoutMode = MutableStateFlow(GridLayoutMode.AUTO)
    val currentLayoutMode = _currentLayoutMode.asStateFlow()

    fun setLayoutMode(mode: GridLayoutMode) {
        _currentLayoutMode.value = mode
    }

    val streamHub = StreamHub(context)
    private val discovery = NetworkDiscovery(context)
    val firebaseManager = FirebaseManager(context)

    private var currentSessionId: String? = null

    val hubWebUrl: String
        get() = "http://${streamHub.serverIp}:${streamHub.port}"

    fun getStreamUrl(clientId: String): String {
        return "http://${streamHub.serverIp}:${streamHub.port}/?stream=$clientId"
    }

    init {
        // Detect if TV (Firestick)
        val isTv = context.packageManager.hasSystemFeature("android.software.leanback")
        if (isTv) {
            setMode(AppMode.HUB)
        }
    }

    fun setMode(mode: AppMode) {
        _appMode.value = mode
        when (mode) {
            AppMode.HUB -> {
                startHub()
                currentSessionId = firebaseManager.recordCastingSession(
                    title = "Firestick 4K Hub",
                    hostIp = streamHub.serverIp,
                    role = "HUB",
                    deviceCount = _activeStreams.value.size
                )
            }
            AppMode.BROADCASTER -> {
                startDiscovery()
            }
            AppMode.RECEIVER -> {
                startDiscovery()
            }
            AppMode.UNSET -> {}
        }
    }

    fun openStreamQr(streamId: String) {
        _selectedStreamForQr.value = streamId
    }

    fun closeStreamQr() {
        _selectedStreamForQr.value = null
    }

    fun openHostQr() {
        _showHostQr.value = true
    }

    fun closeHostQr() {
        _showHostQr.value = false
    }

    fun openAccountDialog() {
        _showAccountDialog.value = true
    }

    fun closeAccountDialog() {
        _showAccountDialog.value = false
    }

    fun openCastGuide(platform: String = "CHROMECAST") {
        _selectedPlatform.value = platform
        _showCastGuide.value = true
    }

    fun closeCastGuide() {
        _showCastGuide.value = false
    }

    fun selectPlatform(platform: String) {
        _selectedPlatform.value = platform
    }

    fun connectAsReceiver(host: String, port: Int) {
        _receiverViewingUrl.value = "http://$host:$port"
        _appMode.value = AppMode.RECEIVER
        firebaseManager.recordCastingSession(
            title = "TV-to-Phone Receiver",
            hostIp = host,
            role = "RECEIVER",
            deviceCount = 1
        )
    }

    private fun startHub() {
        streamHub.start()
        discovery.registerService(8889) // Signaling & Web Artisan port
        viewModelScope.launch {
            streamHub.activeClients.collect { clients ->
                _activeStreams.value = clients
            }
        }
    }

    private fun startDiscovery() {
        discovery.discoverServices { host, port ->
            val hubs = _availableHubs.value.toMutableList()
            if (!hubs.any { it.first == host }) {
                hubs.add(host to port)
                _availableHubs.value = hubs
            }
        }
    }

    fun markCastingStarted(hubHost: String) {
        _isCasting.value = true
        currentSessionId = firebaseManager.recordCastingSession(
            title = "Android Broadcaster",
            hostIp = hubHost,
            role = "BROADCASTER",
            deviceCount = 1
        )
    }

    fun stopCasting() {
        context.stopService(Intent(context, ScreenCastService::class.java))
        _isCasting.value = false
        currentSessionId?.let { firebaseManager.endCastingSession(it) }
    }

    override fun onCleared() {
        super.onCleared()
        streamHub.stop()
        discovery.stopDiscovery()
        currentSessionId?.let { firebaseManager.endCastingSession(it) }
    }
}
