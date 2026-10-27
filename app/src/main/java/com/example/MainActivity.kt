package com.example

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.casting.ScreenCastService
import com.example.casting.StreamHub
import com.example.firebase.CastingSessionRecord
import com.example.firebase.UserProfile
import com.example.ui.AppMode
import com.example.ui.GridLayoutMode
import com.example.ui.MainViewModel
import com.example.ui.theme.MyApplicationTheme
import com.example.util.QrCodeGenerator
import org.webrtc.SurfaceViewRenderer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {

    private var castService: ScreenCastService? = null
    private var isBound = false
    private var pendingHubAddr: String? = null
    private var pendingHubPort: Int? = null
    private var activeViewModel: MainViewModel? = null

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as ScreenCastService.LocalBinder
            castService = binder.getService()
            isBound = true

            pendingHubAddr?.let { addr ->
                pendingHubPort?.let { port ->
                    captureIntent?.let { intent ->
                        castService?.startCasting(Activity.RESULT_OK, intent, addr, port)
                        activeViewModel?.markCastingStarted(addr)
                    }
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            isBound = false
        }
    }

    private var captureIntent: Intent? = null

    private val captureLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            captureIntent = result.data
            val intent = Intent(this, ScreenCastService::class.java)
            startForegroundService(intent)
            bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                val vm: MainViewModel = viewModel { MainViewModel(this@MainActivity) }
                activeViewModel = vm
                MainScreen(
                    viewModel = vm,
                    onStartCapture = { addr, port ->
                        pendingHubAddr = addr
                        pendingHubPort = port
                        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as android.media.projection.MediaProjectionManager
                        captureLauncher.launch(projectionManager.createScreenCaptureIntent())
                    }
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isBound) {
            unbindService(connection)
            isBound = false
        }
    }
}

@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onStartCapture: (String, Int) -> Unit
) {
    val mode by viewModel.appMode.collectAsState()
    val showGuide by viewModel.showCastGuide.collectAsState()
    val selectedPlatform by viewModel.selectedPlatform.collectAsState()
    val selectedStreamForQr by viewModel.selectedStreamForQr.collectAsState()
    val showHostQr by viewModel.showHostQr.collectAsState()
    val showAccountDialog by viewModel.showAccountDialog.collectAsState()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        AnimatedContent(
            targetState = mode,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "ScreenTransition"
        ) { currentMode ->
            when (currentMode) {
                AppMode.UNSET -> ModeSelection(
                    onModeSelected = { viewModel.setMode(it) },
                    onOpenGuide = { viewModel.openCastGuide("CHROMECAST") },
                    onOpenHostQr = { viewModel.openHostQr() },
                    onOpenAccount = { viewModel.openAccountDialog() }
                )
                AppMode.HUB -> {
                    BackHandler { viewModel.setMode(AppMode.UNSET) }
                    HubScreen(
                        viewModel = viewModel,
                        hub = viewModel.streamHub,
                        activeStreams = viewModel.activeStreams.collectAsState().value,
                        layoutMode = viewModel.currentLayoutMode.collectAsState().value,
                        onBack = { viewModel.setMode(AppMode.UNSET) },
                        onOpenStreamQr = { viewModel.openStreamQr(it) },
                        onOpenHostQr = { viewModel.openHostQr() }
                    )
                }
                AppMode.BROADCASTER -> {
                    BackHandler { viewModel.setMode(AppMode.UNSET) }
                    BroadcasterScreen(
                        viewModel = viewModel,
                        onStartCapture = onStartCapture,
                        onBack = { viewModel.setMode(AppMode.UNSET) },
                        onWatchTvMode = { host, port -> viewModel.connectAsReceiver(host, port) }
                    )
                }
                AppMode.RECEIVER -> {
                    BackHandler { viewModel.setMode(AppMode.UNSET) }
                    val viewingUrl = viewModel.receiverViewingUrl.collectAsState().value ?: viewModel.hubWebUrl
                    ReceiverScreen(
                        viewingUrl = viewingUrl,
                        onBack = { viewModel.setMode(AppMode.UNSET) }
                    )
                }
            }
        }

        // Dialog: Stream Specific QR Code
        selectedStreamForQr?.let { streamId ->
            val streamUrl = viewModel.getStreamUrl(streamId)
            QrCodeDisplayDialog(
                title = "Guest Access: Stream #$streamId",
                subtitle = "Scan to watch or join this specific stream directly in your browser without the app!",
                url = streamUrl,
                badge = "STREAM $streamId",
                onDismiss = { viewModel.closeStreamQr() }
            )
        }

        // Dialog: Hub Host QR Code
        if (showHostQr) {
            QrCodeDisplayDialog(
                title = "Join GridCast TV Hub",
                subtitle = "Scan with any phone, tablet, or laptop camera to open the Web Wall and start casting!",
                url = viewModel.hubWebUrl,
                badge = "TV HUB PORTAL",
                onDismiss = { viewModel.closeHostQr() }
            )
        }

        // Dialog: Universal Cast Guide (Chromecast, Roku, Vizio, AirPlay, Browser)
        if (showGuide) {
            CastPlatformGuideDialog(
                webUrl = viewModel.hubWebUrl,
                selectedPlatform = selectedPlatform,
                onSelectPlatform = { viewModel.selectPlatform(it) },
                onDismiss = { viewModel.closeCastGuide() }
            )
        }

        // Dialog: Firebase Account & Cloud Persistence
        if (showAccountDialog) {
            AccountCloudDialog(
                viewModel = viewModel,
                onDismiss = { viewModel.closeAccountDialog() }
            )
        }
    }
}

@Composable
fun ModeSelection(
    onModeSelected: (AppMode) -> Unit,
    onOpenGuide: () -> Unit,
    onOpenHostQr: () -> Unit,
    onOpenAccount: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(id = R.drawable.img_tv_quad_hero_1790072807956),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            alpha = 0.25f
        )
        Box(
            modifier = Modifier.fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xFF070B14).copy(alpha = 0.95f))))
        )

        // Top account button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 40.dp, start = 20.dp, end = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF131C2E),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E293B)),
                modifier = Modifier.clickable { onOpenHostQr() }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.QrCode2, contentDescription = null, tint = Color(0xFF00F2FE), modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Hub QR", style = MaterialTheme.typography.labelMedium, color = Color.White, fontWeight = FontWeight.Bold)
                }
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF131C2E),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E293B)),
                modifier = Modifier.clickable { onOpenAccount() }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Cloud, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Cloud Sync", style = MaterialTheme.typography.labelMedium, color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                "GridCast 4K",
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White
            )
            Text(
                "Multi-Screen Casting & Real-Time Collaborative Hub",
                style = MaterialTheme.typography.titleMedium,
                color = Color(0xFF00F2FE),
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "Cast Phones to TV • Cast TV to Phones • Stream in Background",
                style = MaterialTheme.typography.bodySmall,
                color = Color.LightGray,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(modifier = Modifier.height(30.dp))

            // Three core modes: TV Hub, Phone Broadcaster, Watch TV on Phone
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ModernModeCard(
                    title = "TV Hub",
                    subtitle = "Firestick Receiver",
                    badge = "Grid Wall",
                    icon = Icons.Default.Tv,
                    onClick = { onModeSelected(AppMode.HUB) }
                )
                ModernModeCard(
                    title = "Broadcaster",
                    subtitle = "Cast to TV",
                    badge = "Background OK",
                    icon = Icons.Default.Send,
                    onClick = { onModeSelected(AppMode.BROADCASTER) }
                )
                ModernModeCard(
                    title = "Watch TV",
                    subtitle = "Receive on Phone",
                    badge = "TV to Phone",
                    icon = Icons.Default.PhoneAndroid,
                    onClick = { onModeSelected(AppMode.RECEIVER) }
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Universal Browser & Cast button
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenGuide() },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF131C2E)),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E293B))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFF00F2FE).copy(alpha = 0.15f),
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            Icons.Default.Language,
                            contentDescription = null,
                            tint = Color(0xFF00F2FE),
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Universal Web & Cast Center",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            "Chromecast, Roku, Vizio, AirPlay & Browser QR Pairing",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.LightGray
                        )
                    }
                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.Gray)
                }
            }
        }
    }
}

@Composable
fun ModernModeCard(
    title: String,
    subtitle: String,
    badge: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .width(108.dp)
            .height(165.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF161F33)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E293B))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = Color(0xFF00F2FE).copy(alpha = 0.15f)
            ) {
                Text(
                    badge,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF00F2FE),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Icon(icon, contentDescription = null, modifier = Modifier.size(36.dp), tint = Color.White)
            Spacer(modifier = Modifier.height(8.dp))
            Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = Color.White)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = Color.LightGray)
        }
    }
}

@Composable
fun HubScreen(
    viewModel: MainViewModel,
    hub: StreamHub,
    activeStreams: List<String>,
    layoutMode: GridLayoutMode,
    onBack: () -> Unit,
    onOpenStreamQr: (String) -> Unit,
    onOpenHostQr: () -> Unit
) {
    val context = LocalContext.current
    val webUrl = viewModel.hubWebUrl

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF070B14))
    ) {
        // Top Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Spacer(modifier = Modifier.width(6.dp))
                Column {
                    Text(
                        "GridCast Live Wall",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF10B981))
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "${activeStreams.size} ACTIVE STREAM(S)",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF10B981),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                // QR Code for guest devices
                Button(
                    onClick = onOpenHostQr,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF131C2E)),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00F2FE).copy(alpha = 0.5f))
                ) {
                    Icon(Icons.Default.QrCode2, contentDescription = null, tint = Color(0xFF00F2FE), modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Join QR", style = MaterialTheme.typography.labelMedium, color = Color.White, fontWeight = FontWeight.Bold)
                }

                // Universal Cast Action Button
                Button(
                    onClick = { viewModel.openCastGuide("CHROMECAST") },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1C2742)),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.Cast, contentDescription = null, tint = Color(0xFF00F2FE), modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Cast TV", style = MaterialTheme.typography.labelMedium, color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Live Web & QR Banner
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 4.dp),
            color = Color(0xFF111827),
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00F2FE).copy(alpha = 0.3f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Language, contentDescription = null, tint = Color(0xFF00F2FE), modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text("Browser & Guest Access Portal:", style = MaterialTheme.typography.labelSmall, color = Color.LightGray)
                        Text(webUrl, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = Color(0xFF00F2FE))
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(
                        onClick = onOpenHostQr,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.QrCode, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Show QR", style = MaterialTheme.typography.labelSmall)
                    }

                    FilledTonalButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("GridCast URL", webUrl))
                            Toast.makeText(context, "URL Copied to Clipboard!", Toast.LENGTH_SHORT).show()
                        },
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text("Copy", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Main Grid Video Surface Area
        Box(modifier = Modifier.fillMaxSize().weight(1f)) {
            if (activeStreams.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(32.dp)
                    ) {
                        // QR Code directly in the empty state so guests can immediately connect!
                        val qrBitmap = remember(webUrl) {
                            try {
                                QrCodeGenerator.generateQrImageBitmap(webUrl, 320)
                            } catch (e: Exception) {
                                null
                            }
                        }

                        if (qrBitmap != null) {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = Color.White,
                                modifier = Modifier.size(180.dp),
                                shadowElevation = 8.dp
                            ) {
                                Image(
                                    bitmap = qrBitmap,
                                    contentDescription = "Hub QR Code",
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(8.dp)
                                )
                            }
                        } else {
                            CircularProgressIndicator(color = Color(0xFF00F2FE), strokeWidth = 3.dp, modifier = Modifier.size(48.dp))
                        }

                        Spacer(modifier = Modifier.height(18.dp))
                        Text(
                            "SCAN QR TO JOIN OR CAST",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "Guests can scan with any phone camera to view or cast their screen without installing an app.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.LightGray,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.widthIn(max = 480.dp)
                        )
                    }
                }
            } else {
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    val columns = when (layoutMode) {
                        GridLayoutMode.QUAD_2x2 -> 2
                        GridLayoutMode.STAGE_1x3 -> 2 // Simplification: 1 large, 2 small
                        GridLayoutMode.FOCUS -> 1
                        else -> when {
                            activeStreams.size == 1 -> 1
                            activeStreams.size <= 4 -> 2
                            activeStreams.size <= 9 -> 3
                            else -> 4
                        }
                    }

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(columns),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(activeStreams) { clientId ->
                            WebRtcStreamPlayer(
                                clientId = clientId,
                                hub = hub,
                                onOpenQr = { onOpenStreamQr(clientId) }
                            )
                        }
                    }
                }
            }

            // Global Overlay
            GridOverlay(
                layoutMode = layoutMode,
                onLayoutChange = { viewModel.setLayoutMode(it) }
            )
        }
    }
}

@Composable
fun WebRtcStreamPlayer(
    clientId: String,
    hub: StreamHub,
    onOpenQr: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16 / 9f),
        elevation = CardDefaults.cardElevation(8.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Black),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00F2FE).copy(alpha = 0.4f))
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AndroidView(
                factory = { context ->
                    SurfaceViewRenderer(context).apply {
                        hub.initSurface(clientId, this)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // Remote Control Overlay
            Box(modifier = Modifier.align(Alignment.BottomEnd)) {
                RemoteControlOverlay(
                    onPause = { /* TODO: Implement pause command */ },
                    onRotate = { /* TODO: Implement rotate command */ }
                )
            }

            // Top overlay with client info & live tag
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.7f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(Color(0xFF10B981)))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "Stream: $clientId",
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Dedicated QR Code button for this specific stream!
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color.Black.copy(alpha = 0.75f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00F2FE)),
                    modifier = Modifier.clickable { onOpenQr() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.QrCode, contentDescription = null, tint = Color(0xFF00F2FE), modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Stream QR", color = Color(0xFF00F2FE), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun BroadcasterScreen(
    viewModel: MainViewModel,
    onStartCapture: (String, Int) -> Unit,
    onBack: () -> Unit,
    onWatchTvMode: (String, Int) -> Unit
) {
    val context = LocalContext.current
    val hubs by viewModel.availableHubs.collectAsState()
    val isCasting by viewModel.isCasting.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF070B14))
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Spacer(modifier = Modifier.width(6.dp))
            Column {
                Text(
                    "Broadcaster Mode",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    "Stream your screen (Foreground & Background supported)",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.LightGray
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        if (isCasting) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF131C2E)),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00F2FE))
            ) {
                Column(
                    modifier = Modifier.padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.CloudUpload,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = Color(0xFF00F2FE)
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        "SCREEN BROADCASTING LIVE",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        "Active Low-Latency Hardware Stream (30 FPS)",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF10B981),
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // Stream in Background feature button
                    FilledTonalButton(
                        onClick = {
                            (context as? Activity)?.moveTaskToBack(true)
                            Toast.makeText(context, "Streaming in background. Tap notification anytime to return.", Toast.LENGTH_LONG).show()
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color(0xFF00F2FE).copy(alpha = 0.2f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.PictureInPicture, contentDescription = null, tint = Color(0xFF00F2FE))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Stream in Background (Minimize App)", color = Color(0xFF00F2FE), fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "You can safely minimize GridCast or switch apps. Your screen will keep streaming smoothly while you play games, present slides, or browse.",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.LightGray,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(20.dp))
                    Button(
                        onClick = { viewModel.stopCasting() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("STOP BROADCAST", fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            Text(
                "DISCOVERED HUBS ON NETWORK",
                style = MaterialTheme.typography.labelMedium,
                color = Color(0xFF00F2FE),
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(10.dp))

            if (hubs.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF111827))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(strokeWidth = 2.5.dp, color = Color(0xFF00F2FE), modifier = Modifier.size(36.dp))
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            "Scanning for CastGrid Hub via NSD...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White
                        )
                        Text(
                            "Ensure the Firestick 4K or Hub app is open on the same Wi-Fi",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.LightGray
                        )
                    }
                }
            } else {
                hubs.forEach { (host, port) ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF131C2E)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E293B))
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                modifier = Modifier.size(44.dp),
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xFF00F2FE).copy(alpha = 0.15f)
                            ) {
                                Icon(Icons.Default.Tv, contentDescription = null, tint = Color(0xFF00F2FE), modifier = Modifier.padding(10.dp))
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Firestick 4K Hub", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
                                Text("$host:$port", style = MaterialTheme.typography.bodySmall, color = Color.LightGray)
                            }
                            // Action 1: Cast phone to TV
                            FilledTonalButton(
                                onClick = { onStartCapture(host, port) },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text("Cast Screen", style = MaterialTheme.typography.labelSmall)
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            // Action 2: Watch TV on Phone
                            OutlinedButton(
                                onClick = { onWatchTvMode(host, port) },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text("Watch TV", style = MaterialTheme.typography.labelSmall, color = Color(0xFF00F2FE))
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Background streaming info card
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFF111827),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E293B)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.SecurityUpdateGood, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(28.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("Background Streaming Enabled", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Color.White)
                        Text(
                            "Foreground service keeps casting alive when you play games, use camera, or open presentations.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.LightGray
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ReceiverScreen(
    viewingUrl: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Receiver Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF0D1424))
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Spacer(modifier = Modifier.width(6.dp))
                Column {
                    Text("TV-to-Phone Stream Receiver", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
                    Text("Receiving live TV Hub broadcast", style = MaterialTheme.typography.labelSmall, color = Color(0xFF10B981))
                }
            }

            Row {
                IconButton(onClick = {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(viewingUrl))
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        Toast.makeText(context, "Could not launch external browser", Toast.LENGTH_SHORT).show()
                    }
                }) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "External Browser", tint = Color(0xFF00F2FE))
                }
            }
        }

        // Live WebView Receiver displaying the TV stream
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.mediaPlaybackRequiresUserGesture = false
                    webViewClient = WebViewClient()
                    loadUrl(viewingUrl)
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrCodeDisplayDialog(
    title: String,
    subtitle: String,
    url: String,
    badge: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val qrBitmap = remember(url) {
        try {
            QrCodeGenerator.generateQrImageBitmap(url, 480)
        } catch (e: Exception) {
            null
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF0D1424),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00F2FE).copy(alpha = 0.4f)),
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .wrapContentHeight()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFF00F2FE).copy(alpha = 0.15f)
                    ) {
                        Text(
                            badge,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF00F2FE),
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.LightGray)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.LightGray,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )

                Spacer(modifier = Modifier.height(20.dp))

                // QR Code Image
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White,
                    modifier = Modifier.size(240.dp),
                    shadowElevation = 10.dp
                ) {
                    if (qrBitmap != null) {
                        Image(
                            bitmap = qrBitmap,
                            contentDescription = "QR Code",
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp)
                        )
                    } else {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = Color.Black)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // URL Display with Copy
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFF131C2E),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            url,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF00F2FE),
                            maxLines = 1
                        )
                        Row {
                            TextButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("GridCast URL", url))
                                    Toast.makeText(context, "URL Copied!", Toast.LENGTH_SHORT).show()
                                }
                            ) {
                                Text("Copy", color = Color.White)
                            }
                            TextButton(
                                onClick = {
                                    try {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Error opening browser", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            ) {
                                Text("Open", color = Color(0xFF00F2FE))
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B))
                ) {
                    Text("Close", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountCloudDialog(
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    val firebaseManager = viewModel.firebaseManager
    val currentUser by firebaseManager.currentUser.collectAsState()
    val sessions by firebaseManager.sessions.collectAsState()
    val isAvailable by firebaseManager.isFirebaseAvailable.collectAsState()

    var emailInput by remember { mutableStateOf("") }
    var passwordInput by remember { mutableStateOf("") }
    var isSigningIn by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF0D1424),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E293B)),
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.85f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(22.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Cloud Sync & Account", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
                        Text(
                            if (isAvailable) "Firebase Auth & Firestore Connected" else "Local Storage Mode",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isAvailable) Color(0xFF10B981) else Color(0xFFF59E0B)
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.LightGray)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Current User Card
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF131C2E),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(shape = CircleShape, color = Color(0xFF00F2FE).copy(alpha = 0.2f), modifier = Modifier.size(44.dp)) {
                            Icon(Icons.Default.AccountCircle, contentDescription = null, tint = Color(0xFF00F2FE), modifier = Modifier.padding(8.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(currentUser?.displayName ?: "Guest User", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Color.White)
                            Text(currentUser?.email ?: "UID: ${currentUser?.uid?.take(10)}...", style = MaterialTheme.typography.bodySmall, color = Color.LightGray)
                        }

                        if (currentUser != null && !currentUser!!.isAnonymous) {
                            TextButton(onClick = { firebaseManager.signOut() }) {
                                Text("Sign Out", color = Color(0xFFEF4444))
                            }
                        }
                    }
                }

                // If user is anonymous or guest, provide quick sign-in
                if (currentUser == null || currentUser!!.isAnonymous) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Text("SIGN IN TO SYNC SESSIONS", style = MaterialTheme.typography.labelSmall, color = Color(0xFF00F2FE), fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = emailInput,
                        onValueChange = { emailInput = it },
                        label = { Text("Email") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF00F2FE),
                            unfocusedBorderColor = Color(0xFF1E293B),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = passwordInput,
                        onValueChange = { passwordInput = it },
                        label = { Text("Password") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF00F2FE),
                            unfocusedBorderColor = Color(0xFF1E293B),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = {
                            if (emailInput.isNotBlank() && passwordInput.isNotBlank()) {
                                isSigningIn = true
                                firebaseManager.signInWithEmail(emailInput, passwordInput) { success, _ ->
                                    isSigningIn = false
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00F2FE)),
                        enabled = !isSigningIn
                    ) {
                        Text("Sign In or Register", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                Text(
                    "CASTING SESSION HISTORY (FIRESTORE)",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.LightGray,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))

                if (sessions.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        Text("No casting sessions recorded yet.", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(sessions) { session ->
                            CastingSessionItem(session)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CastingSessionItem(session: CastingSessionRecord) {
    val dateStr = remember(session.timestamp) {
        val sdf = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
        sdf.format(Date(session.timestamp))
    }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = Color(0xFF131C2E),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E293B)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(session.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = Color.White)
                Text("${session.role} • $dateStr", style = MaterialTheme.typography.labelSmall, color = Color.LightGray)
            }
            if (session.isLive) {
                Surface(shape = RoundedCornerShape(6.dp), color = Color(0xFF10B981).copy(alpha = 0.2f)) {
                    Text("LIVE", color = Color(0xFF10B981), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                }
            } else {
                Surface(shape = RoundedCornerShape(6.dp), color = Color.Gray.copy(alpha = 0.2f)) {
                    Text("COMPLETED", color = Color.Gray, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CastPlatformGuideDialog(
    webUrl: String,
    selectedPlatform: String,
    onSelectPlatform: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val platforms = listOf(
        "CHROMECAST" to "Chromecast",
        "AIRPLAY" to "AirPlay",
        "ROKU" to "Roku",
        "VIZIO" to "Vizio SmartCast",
        "BROWSER" to "Web Browser"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF0D1424),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E293B)),
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.88f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "Universal Cast & Web Center",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            "Supports Chromecast, Roku, Vizio, AirPlay & Web",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF00F2FE)
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.LightGray)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Web URL bar
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFF131C2E),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            webUrl,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF00F2FE)
                        )
                        Row {
                            TextButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("GridCast URL", webUrl))
                                    Toast.makeText(context, "URL Copied!", Toast.LENGTH_SHORT).show()
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) {
                                Text("Copy", color = Color.White)
                            }
                            TextButton(
                                onClick = {
                                    try {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(webUrl))
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Error opening browser", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) {
                                Text("Launch", color = Color(0xFF00F2FE))
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Platform Selector Tabs
                ScrollableTabRow(
                    selectedTabIndex = platforms.indexOfFirst { it.first == selectedPlatform }.coerceAtLeast(0),
                    containerColor = Color.Transparent,
                    contentColor = Color(0xFF00F2FE),
                    edgePadding = 0.dp,
                    divider = {}
                ) {
                    platforms.forEach { (id, title) ->
                        Tab(
                            selected = selectedPlatform == id,
                            onClick = { onSelectPlatform(id) },
                            text = {
                                Text(
                                    title,
                                    fontWeight = if (selectedPlatform == id) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Platform Specific Content
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    when (selectedPlatform) {
                        "CHROMECAST" -> {
                            item {
                                PlatformGuideCard(
                                    title = "📡 Google Chromecast & Google TV",
                                    description = "Works on all Chromecast dongles, Chromecast with Google TV (4K / HD), and TVs with Chromecast built-in (Vizio, Sony, Philips, TCL).",
                                    steps = listOf(
                                        "Open $webUrl in Google Chrome or Microsoft Edge on any phone, laptop, or tablet.",
                                        "In Chrome: Click the three dots menu (⋮) > 'Cast, Save, and Share' > 'Cast...'",
                                        "Select your Chromecast or Google TV from the receiver list.",
                                        "Choose 'Cast tab' or 'Cast screen' — the full collaborative grid immediately mirrors to your TV in high definition!"
                                    )
                                )
                            }
                        }
                        "AIRPLAY" -> {
                            item {
                                PlatformGuideCard(
                                    title = "🍏 Apple AirPlay (iPhone, iPad, Mac & Apple TV)",
                                    description = "Stream to Apple TV, Roku TVs (with AirPlay 2), and Vizio SmartCast TVs (with AirPlay 2) natively.",
                                    steps = listOf(
                                        "Open $webUrl in Safari on your iPhone, iPad, or Mac.",
                                        "On iPhone/iPad: Open Control Center (swipe down from top-right) and tap 'Screen Mirroring'.",
                                        "On Mac: Click Control Center in the menu bar > Screen Mirroring.",
                                        "Select your Apple TV, Roku, or Vizio SmartCast TV.",
                                        "The live multi-screen grid appears on your TV screen with ultra-low latency."
                                    )
                                )
                            }
                        }
                        "ROKU" -> {
                            item {
                                PlatformGuideCard(
                                    title = "🟣 Roku (AirPlay 2 & Roku Web Browser)",
                                    description = "All modern Roku streaming players (Roku Express 4K, Streaming Stick 4K, Roku Ultra) and Roku TVs (TCL, Hisense, Onn) support casting.",
                                    steps = listOf(
                                        "Method 1 (Roku AirPlay 2): On your Roku remote, go to Settings > Apple AirPlay and HomeKit, and ensure AirPlay is ON. Then stream from Safari or iPhone/iPad Screen Mirroring directly to your Roku!",
                                        "Method 2 (Roku Web Browser): Open $webUrl using the Roku Web Browser or Web Video Caster app on your Roku device.",
                                        "Method 3 (Screen Mirroring): On Roku, go to Settings > System > Screen Mirroring > Set to 'Always Allow' to mirror Android or Windows screens."
                                    )
                                )
                            }
                        }
                        "VIZIO" -> {
                            item {
                                PlatformGuideCard(
                                    title = "⚡ Vizio SmartCast (Dual Chromecast & AirPlay 2)",
                                    description = "Vizio SmartCast TVs come equipped with both Google Chromecast Built-in and Apple AirPlay 2 out of the box!",
                                    steps = listOf(
                                        "Ensure your Vizio TV is on the same local Wi-Fi network ($webUrl).",
                                        "Via Chromecast: Open $webUrl in Chrome/Edge, click Cast menu, and tap your Vizio SmartCast TV.",
                                        "Via AirPlay: From an Apple device or Safari, tap Screen Mirroring or the AirPlay icon and select your Vizio TV.",
                                        "The full multi-screen grid will instantly display on your Vizio TV without installing any third-party apps!"
                                    )
                                )
                            }
                        }
                        "BROWSER" -> {
                            item {
                                PlatformGuideCard(
                                    title = "💻 Web Browser Caster & Multi-Viewer",
                                    description = "Any laptop, desktop, Chromebook, or mobile device on the network can view or cast screens using pure HTML5 and WebRTC.",
                                    steps = listOf(
                                        "Open $webUrl on any device with a modern browser (Chrome, Safari, Firefox, Edge, Silk).",
                                        "Multi-Screen Wall: Watch all active Android and computer screens updating dynamically in the responsive grid.",
                                        "Cast Browser Screen: Click 'Cast My Screen (Browser)' to share your laptop window, desktop, or tab into the grid.",
                                        "No downloads required — runs completely on your local network!"
                                    )
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B))
                ) {
                    Text("Done", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun PlatformGuideCard(
    title: String,
    description: String,
    steps: List<String>
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF111827)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E293B)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
            Spacer(modifier = Modifier.height(6.dp))
            Text(description, style = MaterialTheme.typography.bodySmall, color = Color.LightGray)
            Spacer(modifier = Modifier.height(12.dp))

            steps.forEachIndexed { index, step ->
                Row(
                    modifier = Modifier.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFF00F2FE).copy(alpha = 0.2f),
                        modifier = Modifier.size(20.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                "${index + 1}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF00F2FE)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        step,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFE2E8F0),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
fun GridOverlay(
    layoutMode: GridLayoutMode,
    onLayoutChange: (GridLayoutMode) -> Unit
) {
    Surface(
        modifier = Modifier.padding(16.dp),
        shape = RoundedCornerShape(12.dp),
        color = Color.Black.copy(alpha = 0.8f),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00F2FE))
    ) {
        Row(modifier = Modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GridLayoutMode.values().forEach { mode ->
                IconButton(onClick = { onLayoutChange(mode) }) {
                    Icon(
                        when (mode) {
                            GridLayoutMode.AUTO -> Icons.Default.AutoAwesome
                            GridLayoutMode.QUAD_2x2 -> Icons.Default.GridView
                            GridLayoutMode.STAGE_1x3 -> Icons.Default.ViewSidebar
                            GridLayoutMode.FOCUS -> Icons.Default.Fullscreen
                        },
                        contentDescription = mode.name,
                        tint = if (layoutMode == mode) Color(0xFF00F2FE) else Color.White
                    )
                }
            }
        }
    }
}

@Composable
fun RemoteControlOverlay(
    onPause: () -> Unit,
    onRotate: () -> Unit
) {
    Surface(
        modifier = Modifier.padding(10.dp),
        shape = RoundedCornerShape(8.dp),
        color = Color.Black.copy(alpha = 0.7f),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White)
    ) {
        Row(modifier = Modifier.padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            IconButton(onClick = onPause) {
                Icon(Icons.Default.Pause, contentDescription = "Pause", tint = Color.White)
            }
            IconButton(onClick = onRotate) {
                Icon(Icons.Default.ScreenRotation, contentDescription = "Rotate", tint = Color.White)
            }
        }
    }
}
