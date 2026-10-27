package com.example.network

import android.graphics.Bitmap
import android.util.Log
import com.example.util.QrCodeGenerator
import io.ktor.http.ContentType
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.channels.consumeEach
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

@Serializable
data class SignalingMessage(
    val type: String, // "offer", "answer", "candidate", "join", "join_viewer"
    val from: String,
    val to: String? = null,
    val data: String? = null
)

class SignalingServer(private val port: Int = 8889) {
    private var engine: EmbeddedServer<*, *>? = null
    private val sessions = ConcurrentHashMap<String, DefaultWebSocketServerSession>()

    fun getActiveClientIds(): List<String> {
        return sessions.keys.filter { it != "HUB" }
    }

    fun getLocalIp(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                if (networkInterface.isLoopback || !networkInterface.isUp) continue
                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    if (!address.isLoopbackAddress && address is Inet4Address) {
                        val host = address.hostAddress ?: ""
                        if (host.isNotEmpty() && !host.startsWith("127.")) {
                            return host
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w("SignalingServer", "Could not determine local IP", e)
        }
        return "127.0.0.1"
    }

    fun start() {
        val serverIp = getLocalIp()
        engine = embeddedServer(Netty, port = port) {
            install(WebSockets)
            install(ContentNegotiation) {
                json()
            }
            routing {
                // Universal Web Artisan portal for Browser, Chromecast, Roku, Vizio, and AirPlay
                get("/") {
                    val html = WebArtisanHtml.getIndexHtml(serverIp, port, getActiveClientIds())
                    call.respondText(html, ContentType.Text.Html)
                }

                get("/cast") {
                    val html = WebArtisanHtml.getIndexHtml(serverIp, port, getActiveClientIds())
                    call.respondText(html, ContentType.Text.Html)
                }

                get("/chromecast") {
                    val html = WebArtisanHtml.getIndexHtml(serverIp, port, getActiveClientIds())
                    call.respondText(html, ContentType.Text.Html)
                }

                get("/airplay") {
                    val html = WebArtisanHtml.getIndexHtml(serverIp, port, getActiveClientIds())
                    call.respondText(html, ContentType.Text.Html)
                }

                get("/roku") {
                    val html = WebArtisanHtml.getIndexHtml(serverIp, port, getActiveClientIds())
                    call.respondText(html, ContentType.Text.Html)
                }

                get("/vizio") {
                    val html = WebArtisanHtml.getIndexHtml(serverIp, port, getActiveClientIds())
                    call.respondText(html, ContentType.Text.Html)
                }

                // Dynamic high-resolution QR code generator endpoint
                get("/api/qr") {
                    val content = call.request.queryParameters["content"] ?: "http://$serverIp:$port"
                    val bitmap = QrCodeGenerator.generateQrBitmap(content, 450)
                    val stream = ByteArrayOutputStream()
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                    call.respondBytes(stream.toByteArray(), ContentType.Image.PNG)
                }

                get("/api/streams") {
                    val clientIds = getActiveClientIds()
                    val streams = clientIds.map { id ->
                        val targetUrl = "http://$serverIp:$port/?stream=$id"
                        val encoded = URLEncoder.encode(targetUrl, "UTF-8")
                        """{"id":"$id","url":"$targetUrl","qrUrl":"http://$serverIp:$port/api/qr?content=$encoded"}"""
                    }.joinToString(",")
                    val json = """{"streams":[$streams],"count":${clientIds.size},"hub":"http://$serverIp:$port"}"""
                    call.respondText(json, ContentType.Application.Json)
                }

                get("/api/status") {
                    val json = """
                        {
                            "status": "online",
                            "hub": "GridCast-Firestick-4K",
                            "ip": "$serverIp",
                            "port": $port,
                            "activeClients": ${getActiveClientIds().size},
                            "supportedProtocols": ["webrtc", "websocket", "chromecast", "airplay", "roku", "vizio"],
                            "qrEndpoint": "http://$serverIp:$port/api/qr?content=http://$serverIp:$port"
                        }
                    """.trimIndent()
                    call.respondText(json, ContentType.Application.Json)
                }

                webSocket("/signaling/{clientId}") {
                    val clientId = call.parameters["clientId"] ?: return@webSocket
                    sessions[clientId] = this
                    Log.d("SignalingServer", "Client $clientId connected")

                    try {
                        incoming.consumeEach { frame ->
                            if (frame is Frame.Text) {
                                val messageText = frame.readText()
                                val message = Json.decodeFromString<SignalingMessage>(messageText)
                                handleMessage(message)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("SignalingServer", "Error in websocket for $clientId", e)
                    } finally {
                        sessions.remove(clientId)
                        Log.d("SignalingServer", "Client $clientId disconnected")
                    }
                }
            }
        }.start(wait = false)
    }

    private suspend fun handleMessage(message: SignalingMessage) {
        if (message.to != null) {
            // Target message
            sessions[message.to]?.send(Json.encodeToString(message))
        } else if (message.type == "join" || message.type == "join_viewer") {
            // Hub specific: notify hub of a new joiner or viewer
            sessions["HUB"]?.send(Json.encodeToString(message))
        }
    }

    fun stop() {
        engine?.stop(1000, 2000)
    }
}
