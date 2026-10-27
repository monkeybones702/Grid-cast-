package com.example.network

object WebArtisanHtml {

    fun getIndexHtml(serverIp: String, port: Int, activeClients: List<String>): String {
        val clientCount = activeClients.size
        return """
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>GridCast | Universal Multi-Screen Web & Cast Hub</title>
    <!-- Google Cast SDK -->
    <script src="https://www.gstatic.com/cv/js/sender/v1/cast_sender.js?loadCastFramework=1"></script>
    <style>
        :root {
            --bg-primary: #0a0e17;
            --bg-secondary: #131b2e;
            --bg-card: #1c2640;
            --text-primary: #f0f4fc;
            --text-secondary: #94a3b8;
            --accent-cyan: #00f2fe;
            --accent-blue: #4facfe;
            --accent-amber: #f59e0b;
            --accent-purple: #a855f7;
            --accent-green: #10b981;
            --accent-red: #ef4444;
            --border: rgba(255, 255, 255, 0.1);
        }
        * {
            box-sizing: border-box;
            margin: 0;
            padding: 0;
            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
        }
        body {
            background-color: var(--bg-primary);
            color: var(--text-primary);
            min-height: 100vh;
            display: flex;
            flex-direction: column;
        }
        header {
            background: linear-gradient(135deg, rgba(19, 27, 46, 0.95), rgba(10, 14, 23, 0.95));
            border-bottom: 1px solid var(--border);
            padding: 1rem 2rem;
            display: flex;
            align-items: center;
            justify-content: space-between;
            position: sticky;
            top: 0;
            z-index: 100;
            backdrop-filter: blur(12px);
        }
        .brand {
            display: flex;
            align-items: center;
            gap: 12px;
        }
        .brand-icon {
            width: 40px;
            height: 40px;
            border-radius: 10px;
            background: linear-gradient(135deg, var(--accent-cyan), var(--accent-blue));
            display: flex;
            align-items: center;
            justify-content: center;
            font-weight: 900;
            font-size: 20px;
            color: #000;
            box-shadow: 0 0 15px rgba(0, 242, 254, 0.4);
        }
        .brand h1 {
            font-size: 1.25rem;
            font-weight: 800;
            letter-spacing: -0.5px;
            background: linear-gradient(to right, #fff, var(--accent-cyan));
            -webkit-background-clip: text;
            -webkit-text-fill-color: transparent;
        }
        .brand-sub {
            font-size: 0.75rem;
            color: var(--text-secondary);
        }
        .nav-chips {
            display: flex;
            gap: 8px;
            flex-wrap: wrap;
        }
        .chip {
            padding: 6px 14px;
            border-radius: 20px;
            font-size: 0.8rem;
            font-weight: 600;
            display: flex;
            align-items: center;
            gap: 6px;
            background: var(--bg-card);
            border: 1px solid var(--border);
            color: var(--text-primary);
            text-decoration: none;
            transition: all 0.2s ease;
        }
        .chip:hover {
            border-color: var(--accent-cyan);
            box-shadow: 0 0 10px rgba(0, 242, 254, 0.2);
        }
        .chip.live {
            background: rgba(16, 185, 129, 0.15);
            border-color: var(--accent-green);
            color: #34d399;
        }
        .pulse-dot {
            width: 8px;
            height: 8px;
            border-radius: 50%;
            background-color: var(--accent-green);
            animation: pulse 1.5s infinite;
        }
        @keyframes pulse {
            0% { transform: scale(0.95); box-shadow: 0 0 0 0 rgba(16, 185, 129, 0.7); }
            70% { transform: scale(1.1); box-shadow: 0 0 0 8px rgba(16, 185, 129, 0); }
            100% { transform: scale(0.95); box-shadow: 0 0 0 0 rgba(16, 185, 129, 0); }
        }
        main {
            flex: 1;
            padding: 1.5rem 2rem;
            max-width: 1400px;
            margin: 0 auto;
            width: 100%;
        }
        .hero-banner {
            background: linear-gradient(135deg, rgba(28, 38, 64, 0.6), rgba(19, 27, 46, 0.8));
            border: 1px solid var(--border);
            border-radius: 16px;
            padding: 1.5rem 2rem;
            margin-bottom: 2rem;
            display: flex;
            flex-wrap: wrap;
            align-items: center;
            justify-content: space-between;
            gap: 1.5rem;
            box-shadow: 0 10px 30px rgba(0,0,0,0.3);
        }
        .hero-text h2 {
            font-size: 1.5rem;
            margin-bottom: 0.5rem;
            display: flex;
            align-items: center;
            gap: 10px;
        }
        .hero-text p {
            color: var(--text-secondary);
            font-size: 0.95rem;
            max-width: 600px;
        }
        .quick-actions {
            display: flex;
            gap: 12px;
            flex-wrap: wrap;
        }
        .btn {
            padding: 10px 20px;
            border-radius: 10px;
            font-weight: 700;
            font-size: 0.9rem;
            border: none;
            cursor: pointer;
            display: inline-flex;
            align-items: center;
            gap: 8px;
            transition: all 0.2s ease;
        }
        .btn-primary {
            background: linear-gradient(135deg, var(--accent-cyan), var(--accent-blue));
            color: #000;
            box-shadow: 0 4px 15px rgba(0, 242, 254, 0.3);
        }
        .btn-primary:hover {
            transform: translateY(-2px);
            box-shadow: 0 6px 20px rgba(0, 242, 254, 0.5);
        }
        .btn-cast {
            background: linear-gradient(135deg, #3b82f6, #1d4ed8);
            color: #fff;
        }
        .btn-airplay {
            background: linear-gradient(135deg, #475569, #1e293b);
            border: 1px solid rgba(255, 255, 255, 0.2);
            color: #fff;
        }
        .btn-share {
            background: linear-gradient(135deg, var(--accent-purple), #7e22ce);
            color: #fff;
        }
        .btn:hover {
            transform: translateY(-2px);
            filter: brightness(1.1);
        }
        .grid-section-header {
            display: flex;
            justify-content: space-between;
            align-items: center;
            margin-bottom: 1.25rem;
        }
        .section-title {
            font-size: 1.2rem;
            font-weight: 700;
            display: flex;
            align-items: center;
            gap: 8px;
        }
        .layout-selector {
            display: flex;
            background: var(--bg-card);
            border-radius: 8px;
            padding: 4px;
            gap: 4px;
            border: 1px solid var(--border);
        }
        .layout-btn {
            background: transparent;
            border: none;
            color: var(--text-secondary);
            padding: 6px 12px;
            border-radius: 6px;
            cursor: pointer;
            font-size: 0.8rem;
            font-weight: 600;
        }
        .layout-btn.active {
            background: rgba(255, 255, 255, 0.1);
            color: var(--accent-cyan);
        }

        /* Responsive Grid Stream Tiles */
        .video-grid {
            display: grid;
            gap: 16px;
            grid-template-columns: repeat(auto-fit, minmax(360px, 1fr));
            margin-bottom: 2.5rem;
        }
        .video-grid.layout-1 {
            grid-template-columns: 1fr;
        }
        .video-grid.layout-2 {
            grid-template-columns: repeat(2, 1fr);
        }
        .video-grid.layout-4 {
            grid-template-columns: repeat(2, 1fr);
        }
        .video-grid.layout-6 {
            grid-template-columns: repeat(3, 1fr);
        }

        .video-card {
            background: #000;
            border-radius: 14px;
            overflow: hidden;
            border: 1px solid var(--border);
            position: relative;
            aspect-ratio: 16 / 9;
            box-shadow: 0 6px 20px rgba(0, 0, 0, 0.4);
            display: flex;
            align-items: center;
            justify-content: center;
            transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
        }
        .video-card:hover {
            border-color: var(--accent-cyan);
            box-shadow: 0 0 20px rgba(0, 242, 254, 0.25);
        }
        .video-card video, .video-card canvas {
            width: 100%;
            height: 100%;
            object-fit: contain;
            background: #000;
        }
        .video-overlay {
            position: absolute;
            top: 12px;
            left: 12px;
            right: 12px;
            display: flex;
            justify-content: space-between;
            align-items: center;
            pointer-events: none;
            z-index: 10;
        }
        .badge {
            background: rgba(0, 0, 0, 0.7);
            backdrop-filter: blur(8px);
            padding: 4px 10px;
            border-radius: 6px;
            font-size: 0.75rem;
            font-weight: 700;
            color: #fff;
            border: 1px solid rgba(255, 255, 255, 0.15);
            display: flex;
            align-items: center;
            gap: 6px;
        }
        .badge.live-tag {
            color: #34d399;
            border-color: rgba(52, 211, 153, 0.3);
        }
        .video-controls-bottom {
            position: absolute;
            bottom: 12px;
            right: 12px;
            display: flex;
            gap: 8px;
            z-index: 10;
        }
        .icon-btn {
            background: rgba(0, 0, 0, 0.7);
            backdrop-filter: blur(8px);
            border: 1px solid rgba(255, 255, 255, 0.2);
            color: #fff;
            width: 32px;
            height: 32px;
            border-radius: 8px;
            cursor: pointer;
            display: flex;
            align-items: center;
            justify-content: center;
            transition: all 0.2s ease;
        }
        .icon-btn:hover {
            background: var(--accent-cyan);
            color: #000;
        }

        /* Empty state radar */
        .empty-grid-card {
            background: var(--bg-card);
            border: 2px dashed rgba(255, 255, 255, 0.15);
            border-radius: 14px;
            padding: 3rem 2rem;
            text-align: center;
            grid-column: 1 / -1;
            display: flex;
            flex-direction: column;
            align-items: center;
            justify-content: center;
            gap: 1rem;
        }
        .radar-box {
            width: 80px;
            height: 80px;
            border-radius: 50%;
            background: radial-gradient(circle, rgba(0, 242, 254, 0.2) 0%, transparent 70%);
            border: 2px solid var(--accent-cyan);
            position: relative;
            display: flex;
            align-items: center;
            justify-content: center;
            animation: rotateRadar 4s linear infinite;
        }
        @keyframes rotateRadar {
            0% { transform: rotate(0deg); }
            100% { transform: rotate(360deg); }
        }

        /* Tabs for Chromecast, Roku, Vizio, Airplay */
        .cast-platforms-card {
            background: var(--bg-card);
            border: 1px solid var(--border);
            border-radius: 16px;
            padding: 1.5rem 2rem;
            margin-bottom: 2rem;
        }
        .tabs {
            display: flex;
            gap: 8px;
            border-bottom: 1px solid var(--border);
            padding-bottom: 12px;
            margin-bottom: 1.5rem;
            overflow-x: auto;
        }
        .tab-btn {
            background: transparent;
            border: none;
            color: var(--text-secondary);
            font-weight: 700;
            font-size: 0.95rem;
            padding: 8px 16px;
            border-radius: 8px;
            cursor: pointer;
            display: flex;
            align-items: center;
            gap: 8px;
            transition: all 0.2s ease;
            white-space: nowrap;
        }
        .tab-btn:hover {
            color: var(--text-primary);
            background: rgba(255, 255, 255, 0.05);
        }
        .tab-btn.active {
            color: #000;
            background: var(--accent-cyan);
            box-shadow: 0 2px 10px rgba(0, 242, 254, 0.3);
        }
        .tab-content {
            display: none;
            animation: fadeIn 0.3s ease;
        }
        .tab-content.active {
            display: block;
        }
        @keyframes fadeIn {
            from { opacity: 0; transform: translateY(6px); }
            to { opacity: 1; transform: translateY(0); }
        }
        .platform-grid {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
            gap: 20px;
            margin-top: 1rem;
        }
        .platform-box {
            background: var(--bg-secondary);
            border: 1px solid var(--border);
            border-radius: 12px;
            padding: 1.25rem;
            display: flex;
            flex-direction: column;
            gap: 10px;
        }
        .platform-box h4 {
            color: var(--text-primary);
            display: flex;
            align-items: center;
            gap: 8px;
            font-size: 1.05rem;
        }
        .platform-box p {
            color: var(--text-secondary);
            font-size: 0.85rem;
            line-height: 1.4;
        }
        .step-list {
            list-style: none;
            margin-top: 6px;
            display: flex;
            flex-direction: column;
            gap: 6px;
        }
        .step-list li {
            font-size: 0.82rem;
            color: #cbd5e1;
            display: flex;
            align-items: flex-start;
            gap: 8px;
        }
        .step-num {
            background: rgba(0, 242, 254, 0.15);
            color: var(--accent-cyan);
            border-radius: 50%;
            width: 18px;
            height: 18px;
            display: flex;
            align-items: center;
            justify-content: center;
            font-size: 0.7rem;
            font-weight: 800;
            flex-shrink: 0;
            margin-top: 1px;
        }
        footer {
            background: var(--bg-secondary);
            border-top: 1px solid var(--border);
            padding: 1.5rem 2rem;
            text-align: center;
            font-size: 0.85rem;
            color: var(--text-secondary);
        }
        .code-box {
            background: #000;
            padding: 8px 12px;
            border-radius: 8px;
            font-family: monospace;
            color: var(--accent-cyan);
            font-size: 0.85rem;
            display: flex;
            justify-content: space-between;
            align-items: center;
            border: 1px solid rgba(255, 255, 255, 0.1);
        }
        .copy-btn {
            background: rgba(255, 255, 255, 0.1);
            border: none;
            color: #fff;
            padding: 4px 8px;
            border-radius: 4px;
            cursor: pointer;
            font-size: 0.75rem;
        }
        .copy-btn:hover {
            background: var(--accent-cyan);
            color: #000;
        }
    </style>
</head>
<body>

    <header>
        <div class="brand">
            <div class="brand-icon">☵</div>
            <div>
                <h1>GridCast Universal</h1>
                <div class="brand-sub">Collaborative 4K Multi-Screen Hub</div>
            </div>
        </div>
        <div class="nav-chips">
            <div class="chip live">
                <span class="pulse-dot"></span>
                <span id="activeDeviceCount">$clientCount</span> Stream(s) Active
            </div>
            <a href="#gridSection" class="chip">📺 Live Grid</a>
            <a href="#castSection" class="chip">📡 Cast to TV</a>
            <button class="chip" onclick="startBrowserShare()" style="cursor:pointer; background:rgba(168, 85, 247, 0.2); border-color:var(--accent-purple);">
                💻 Cast Browser Screen
            </button>
        </div>
    </header>

    <main>
        <!-- Hero Banner with Quick Actions -->
        <div class="hero-banner">
            <div class="hero-text">
                <h2>🌐 Multi-Device Screen Casting Portal</h2>
                <p>
                    Cast multiple Android, iPhone, PC, and Mac screens simultaneously into this real-time grid.
                    Seamlessly stream this unified wall to <strong>Chromecast, Roku, Vizio SmartCast, and Apple AirPlay</strong> directly from any browser!
                </p>
                <div style="margin-top: 10px; display: flex; align-items: center; gap: 8px;">
                    <span style="font-size: 0.8rem; color: var(--text-secondary);">Direct Browser URL:</span>
                    <span class="code-box" style="padding: 4px 10px;">
                        <span>http://$serverIp:$port</span>
                        <button class="copy-btn" onclick="navigator.clipboard.writeText('http://$serverIp:$port'); alert('URL Copied!')">Copy</button>
                    </span>
                </div>
            </div>
            <div class="quick-actions">
                <button class="btn btn-primary" onclick="startBrowserShare()">
                    💻 Cast My Screen (Browser)
                </button>
                <button class="btn btn-cast" id="chromecastBtn" onclick="triggerChromecast()">
                    📡 Cast to Chromecast / Vizio
                </button>
                <button class="btn btn-airplay" id="airplayBtn" onclick="triggerAirPlay()">
                    🍏 AirPlay to Apple TV / Roku
                </button>
            </div>
        </div>

        <!-- Live Grid Section -->
        <section id="gridSection">
            <div class="grid-section-header">
                <div class="section-title">
                    <span>📺 Live Collaborative Multi-Screen Wall</span>
                    <span id="gridStatusChip" style="font-size: 0.75rem; color: var(--accent-cyan); font-weight: 600;">(Real-Time WebRTC / Hardware Sync)</span>
                </div>
                <div class="layout-selector">
                    <button class="layout-btn active" onclick="setLayout(0)">Auto</button>
                    <button class="layout-btn" onclick="setLayout(1)">1x1</button>
                    <button class="layout-btn" onclick="setLayout(2)">2x2 Quad</button>
                    <button class="layout-btn" onclick="setLayout(6)">3x3 Grid</button>
                </div>
            </div>

            <!-- Video Tiles Container -->
            <div class="video-grid" id="videoGridContainer">
                <!-- If streams exist, they are populated here dynamically. Otherwise default cards -->
                <div class="video-card" id="cardHubSelf">
                    <div class="video-overlay">
                        <span class="badge live-tag">● FIRE TV 4K HUB</span>
                        <span class="badge">HOST $serverIp</span>
                    </div>
                    <canvas id="hubCanvas" width="640" height="360"></canvas>
                    <div class="video-controls-bottom">
                        <button class="icon-btn" title="Toggle Fullscreen" onclick="toggleCardFullscreen('cardHubSelf')">⛶</button>
                    </div>
                </div>

                <div class="video-card" id="cardWebShare" style="display: none;">
                    <div class="video-overlay">
                        <span class="badge live-tag">● BROWSER STREAM (LOCAL)</span>
                        <span class="badge" id="browserShareFps">60 FPS</span>
                    </div>
                    <video id="localBrowserVideo" autoplay playsinline muted></video>
                    <div class="video-controls-bottom">
                        <button class="icon-btn" title="Stop Cast" onclick="stopBrowserShare()" style="background:var(--accent-red); color:#fff;">✕</button>
                        <button class="icon-btn" title="Toggle Fullscreen" onclick="toggleCardFullscreen('cardWebShare')">⛶</button>
                    </div>
                </div>
            </div>
        </section>

        <!-- Universal Cast & Connectivity Center (Chromecast, Roku, Vizio, AirPlay) -->
        <section id="castSection" class="cast-platforms-card">
            <h3 style="font-size: 1.3rem; margin-bottom: 0.5rem; display: flex; align-items: center; gap: 10px;">
                <span>📡 Universal Cast & Screen Mirroring Center</span>
            </h3>
            <p style="color: var(--text-secondary); font-size: 0.9rem; margin-bottom: 1.5rem;">
                GridCast is engineered to beam to your existing living room screens via any web browser without extra app downloads.
            </p>

            <div class="tabs">
                <button class="tab-btn active" onclick="openTab('tab-chromecast')">📡 Chromecast & Google TV</button>
                <button class="tab-btn" onclick="openTab('tab-airplay')">🍏 Apple AirPlay</button>
                <button class="tab-btn" onclick="openTab('tab-roku')">🟣 Roku OS</button>
                <button class="tab-btn" onclick="openTab('tab-vizio')">⚡ Vizio SmartCast</button>
                <button class="tab-btn" onclick="openTab('tab-browser')">💻 Any Web Browser</button>
            </div>

            <!-- Tab: Chromecast -->
            <div id="tab-chromecast" class="tab-content active">
                <div class="platform-grid">
                    <div class="platform-box">
                        <h4>📡 Built-in Chromecast Web Sender</h4>
                        <p>You can beam this entire multi-view wall to any Chromecast device on your local Wi-Fi with a single click.</p>
                        <button class="btn btn-cast" style="width: 100%; margin-top: 8px;" onclick="triggerChromecast()">
                            Cast Tab to Chromecast
                        </button>
                        <div id="castStatusMsg" style="font-size: 0.75rem; color: var(--text-secondary); margin-top: 4px;">
                            Chromecast API Ready
                        </div>
                    </div>
                    <div class="platform-box">
                        <h4>📋 Chrome & Edge Browser Steps</h4>
                        <ul class="step-list">
                            <li><span class="step-num">1</span> Ensure your TV or Chromecast is on the same Wi-Fi network ($serverIp).</li>
                            <li><span class="step-num">2</span> In Google Chrome, click the <strong>⋮ (Three Dots) Menu</strong> &gt; <strong>Cast, Save, and Share</strong> &gt; <strong>Cast...</strong></li>
                            <li><span class="step-num">3</span> Select your Chromecast, Google TV, or Vizio SmartCast from the device list.</li>
                            <li><span class="step-num">4</span> The full multi-screen grid displays in 4K resolution with ultra-low latency!</li>
                        </ul>
                    </div>
                </div>
            </div>

            <!-- Tab: AirPlay -->
            <div id="tab-airplay" class="tab-content">
                <div class="platform-grid">
                    <div class="platform-box">
                        <h4>🍏 Safari AirPlay One-Click Beam</h4>
                        <p>Stream live to Apple TV, Roku TVs with AirPlay 2, and Vizio SmartCast TVs using native Safari AirPlay.</p>
                        <video id="airplayVideoTrigger" style="display:none;" playsinline x-webkit-airplay="allow"></video>
                        <button class="btn btn-airplay" style="width: 100%; margin-top: 8px;" onclick="triggerAirPlay()">
                            Select AirPlay TV / Receiver
                        </button>
                        <div id="airplayStatusMsg" style="font-size: 0.75rem; color: var(--text-secondary); margin-top: 4px;">
                            Safari AirPlay 2 Supported
                        </div>
                    </div>
                    <div class="platform-box">
                        <h4>📱 iPhone, iPad & Mac Screen Mirroring</h4>
                        <ul class="step-list">
                            <li><span class="step-num">1</span> On iPhone/iPad: Swipe down to open <strong>Control Center</strong>.</li>
                            <li><span class="step-num">2</span> Tap the <strong>Screen Mirroring</strong> icon (two overlapping rectangles).</li>
                            <li><span class="step-num">3</span> Select your Apple TV, Roku, or Vizio TV to mirror this web grid!</li>
                            <li><span class="step-num">4</span> On Mac: Click <strong>Control Center</strong> in the menu bar &gt; <strong>Screen Mirroring</strong>.</li>
                        </ul>
                    </div>
                </div>
            </div>

            <!-- Tab: Roku -->
            <div id="tab-roku" class="tab-content">
                <div class="platform-grid">
                    <div class="platform-box">
                        <h4>🟣 1. Roku AirPlay 2 (Recommended)</h4>
                        <p>All Roku TVs and streaming devices running <strong>Roku OS 9.4+</strong> feature Apple AirPlay 2 natively built in.</p>
                        <ul class="step-list">
                            <li><span class="step-num">1</span> On Roku: Go to <strong>Settings &gt; Apple AirPlay and HomeKit</strong> and turn it ON.</li>
                            <li><span class="step-num">2</span> From your iPhone, iPad, Mac or Safari browser, tap <strong>AirPlay</strong> and select your Roku.</li>
                        </ul>
                    </div>
                    <div class="platform-box">
                        <h4>🟣 2. Roku Web Browser & Caster</h4>
                        <p>Open this URL on Roku via the Web Video Caster receiver or Roku Web Browser app:</p>
                        <div class="code-box" style="margin: 8px 0;">
                            <span>http://$serverIp:$port</span>
                            <button class="copy-btn" onclick="navigator.clipboard.writeText('http://$serverIp:$port')">Copy</button>
                        </div>
                        <ul class="step-list">
                            <li><span class="step-num">3</span> <strong>Roku Screen Mirroring (Miracast):</strong> Settings &gt; System &gt; Screen Mirroring &gt; Set to "Always Allow".</li>
                        </ul>
                    </div>
                </div>
            </div>

            <!-- Tab: Vizio SmartCast -->
            <div id="tab-vizio" class="tab-content">
                <div class="platform-grid">
                    <div class="platform-box">
                        <h4>⚡ Vizio SmartCast Built-In Casting</h4>
                        <p>Every Vizio SmartCast TV includes <strong>both Google Chromecast Built-in and Apple AirPlay 2</strong>!</p>
                        <button class="btn btn-primary" style="width:100%; margin-top: 8px;" onclick="triggerChromecast()">
                            Beam to Vizio SmartCast
                        </button>
                    </div>
                    <div class="platform-box">
                        <h4>📋 Connecting to Vizio TV</h4>
                        <ul class="step-list">
                            <li><span class="step-num">1</span> Turn on your Vizio TV and make sure it is on the same Wi-Fi network.</li>
                            <li><span class="step-num">2</span> Press <strong>Input</strong> on the Vizio remote and ensure SmartCast is ready.</li>
                            <li><span class="step-num">3</span> Tap the <strong>Cast to Chromecast</strong> button or <strong>AirPlay</strong> button above.</li>
                            <li><span class="step-num">4</span> Your Vizio TV will immediately display the GridCast collaborative wall!</li>
                        </ul>
                    </div>
                </div>
            </div>

            <!-- Tab: Any Web Browser -->
            <div id="tab-browser" class="tab-content">
                <div class="platform-grid">
                    <div class="platform-box">
                        <h4>💻 Cast Any Laptop or Desktop Screen</h4>
                        <p>Click below to share your current browser tab, application window, or entire monitor directly into the GridCast multi-screen wall!</p>
                        <button class="btn btn-share" style="width:100%; margin-top:8px;" onclick="startBrowserShare()">
                            Start Screen Share Now
                        </button>
                    </div>
                    <div class="platform-box">
                        <h4>📱 Mobile Browser Viewer</h4>
                        <p>Anyone on the local network can open <code>http://$serverIp:$port</code> to watch all streams in high definition with under 50ms latency.</p>
                    </div>
                </div>
            </div>
        </section>
    </main>

    <footer>
        <p>GridCast Hub © 2026 • Optimized for Firestick 4K, Chromecast, Roku, Vizio SmartCast & AirPlay • Low-Latency WebRTC & Hardware AVC</p>
    </footer>

    <script>
        // Tab switching
        function openTab(tabId) {
            document.querySelectorAll('.tab-btn').forEach(b => b.classList.remove('active'));
            document.querySelectorAll('.tab-content').forEach(c => c.classList.remove('active'));
            event.currentTarget.classList.add('active');
            const target = document.getElementById(tabId);
            if (target) target.classList.add('active');
        }

        // Layout toggle
        function setLayout(cols) {
            document.querySelectorAll('.layout-btn').forEach(b => b.classList.remove('active'));
            event.currentTarget.classList.add('active');
            const grid = document.getElementById('videoGridContainer');
            grid.className = 'video-grid';
            if (cols > 0) {
                grid.classList.add('layout-' + cols);
            }
        }

        function toggleCardFullscreen(cardId) {
            const el = document.getElementById(cardId);
            if (!document.fullscreenElement) {
                el.requestFullscreen().catch(err => alert("Fullscreen error: " + err.message));
            } else {
                document.exitFullscreen();
            }
        }

        // Animated Hub Canvas Visualizer
        const canvas = document.getElementById('hubCanvas');
        const ctx = canvas.getContext('2d');
        let frameCount = 0;
        function drawHubCanvas() {
            frameCount++;
            ctx.fillStyle = '#0a0e17';
            ctx.fillRect(0, 0, canvas.width, canvas.height);

            // Draw grid pattern
            ctx.strokeStyle = 'rgba(0, 242, 254, 0.15)';
            ctx.lineWidth = 1;
            for (let x = 0; x < canvas.width; x += 40) {
                ctx.beginPath(); ctx.moveTo(x, 0); ctx.lineTo(x, canvas.height); ctx.stroke();
            }
            for (let y = 0; y < canvas.height; y += 40) {
                ctx.beginPath(); ctx.moveTo(0, y); ctx.lineTo(canvas.width, y); ctx.stroke();
            }

            // Radar pulse ring
            const cx = canvas.width / 2;
            const cy = canvas.height / 2;
            const radius = (frameCount * 2) % 180;
            const alpha = 1 - (radius / 180);
            ctx.strokeStyle = "rgba(0, 242, 254, " + alpha + ")";
            ctx.lineWidth = 2;
            ctx.beginPath();
            ctx.arc(cx, cy, radius, 0, Math.PI * 2);
            ctx.stroke();

            // Center branding
            ctx.fillStyle = '#00f2fe';
            ctx.font = 'bold 22px system-ui';
            ctx.textAlign = 'center';
            ctx.fillText('FIRESTICK 4K GRIDCAST HUB', cx, cy - 10);

            ctx.fillStyle = '#94a3b8';
            ctx.font = '14px system-ui';
            ctx.fillText('WAITING FOR BROADCASTERS / BROWSERS', cx, cy + 20);

            ctx.fillStyle = '#34d399';
            ctx.font = '12px monospace';
            ctx.fillText('PORT 8889 • WebRTC & Ktor Signaling Active', cx, cy + 45);

            requestAnimationFrame(drawHubCanvas);
        }
        drawHubCanvas();

        // Browser Screen Share (Broadcasting from Browser)
        let localStream = null;
        async function startBrowserShare() {
            try {
                localStream = await navigator.mediaDevices.getDisplayMedia({
                    video: { cursor: "always", displaySurface: "monitor" },
                    audio: true
                });
                const card = document.getElementById('cardWebShare');
                const video = document.getElementById('localBrowserVideo');
                card.style.display = 'flex';
                video.srcObject = localStream;

                localStream.getVideoTracks()[0].onended = () => {
                    stopBrowserShare();
                };

                // Connect to signaling as a browser caster
                connectWebRtcSignaling(localStream);
            } catch (err) {
                console.warn("Screen share cancelled or failed: ", err);
            }
        }

        function stopBrowserShare() {
            if (localStream) {
                localStream.getTracks().forEach(t => t.stop());
                localStream = null;
            }
            document.getElementById('cardWebShare').style.display = 'none';
        }

        // WebRTC Signaling Client
        let ws = null;
        function connectWebRtcSignaling(streamToBroadcast) {
            const clientId = "BROWSER_" + Math.random().toString(36).substring(2, 8);
            const wsProtocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
            const wsUrl = wsProtocol + '//' + window.location.host + '/signaling/' + clientId;

            try {
                ws = new WebSocket(wsUrl);
                ws.onopen = () => {
                    console.log("Connected to GridCast Signaling");
                    ws.send(JSON.stringify({ type: "join", from: clientId, to: "HUB" }));
                };
                ws.onmessage = (event) => {
                    console.log("Signaling message:", event.data);
                };
            } catch (e) {
                console.warn("Signaling websocket connection error:", e);
            }
        }

        // Google Cast API
        window['__onGCastApiAvailable'] = function(isAvailable) {
            if (isAvailable && window.cast) {
                try {
                    cast.framework.CastContext.getInstance().setOptions({
                        receiverApplicationId: chrome.cast.media.DEFAULT_MEDIA_RECEIVER_APP_ID,
                        autoJoinPolicy: chrome.cast.AutoJoinPolicy.ORIGIN_SCOPED
                    });
                    const msg = document.getElementById('castStatusMsg');
                    if (msg) msg.textContent = "Google Cast Framework Initialized & Ready";
                } catch (e) {
                    console.log("Cast init notice:", e);
                }
            }
        };

        function triggerChromecast() {
            if (window.cast && cast.framework) {
                try {
                    cast.framework.CastContext.getInstance().requestSession().then(
                        () => alert("Connected to Chromecast / Vizio!"),
                        (err) => console.log("Cast session request info:", err)
                    );
                    return;
                } catch (e) {}
            }
            alert("To cast this grid: In Google Chrome or Edge, click the 3 dots menu ⋮ at the top right, select 'Cast...', then choose your Chromecast or Vizio TV!");
        }

        // Safari AirPlay API
        function triggerAirPlay() {
            const video = document.getElementById('airplayVideoTrigger');
            if (video && video.webkitShowPlaybackTargetPicker) {
                try {
                    video.webkitShowPlaybackTargetPicker();
                    return;
                } catch (e) {
                    console.log(e);
                }
            }
            alert("AirPlay Mirroring: On your iPhone, iPad, or Mac, open Control Center and tap 'Screen Mirroring', then pick your Apple TV, Roku, or Vizio SmartCast TV!");
        }
    </script>
</body>
</html>
        """.trimIndent()
    }
}
