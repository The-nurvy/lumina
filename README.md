# lumina
Lumina is a synchronised, multi-device audio visualiser that turns nearby Android phones into a unified, pulsing light show driven by the beat of your music.

Lumina uses your phone's microphone to analyse the frequencies of the music playing in the room, translating bass, mids, and treble into dynamic, real-time light pulses. Connect with friends within a 10-metre radius, and Lumina will wirelessly synchronise everyone's screens with sub-millisecond precision, turning a group of phones into a coordinated, immersive visual experience—no internet required.

Lumina is a real-time audio visualisation and peer-to-peer synchronisation engine built for Android.

By running Fast Fourier Transform (FFT) analysis directly on the device, Lumina listens to the environment and splits ambient audio into distinct frequency bands. It maps heavy bass kicks to expanding strobe flashes, and treble hi-hats to floating particle trails.

Swarm Mode. Using the Google Nearby Connections API, a "host" device can connect to multiple "client" devices within a 10-metre radius (using Wi-Fi Direct and Bluetooth LE). To overcome network latency, Lumina implements a custom NTP (Network Time Protocol) handshake and a client-side jitter buffer. This ensures that every phone in the room flashes at the exact same microsecond, creating a perfectly synchronised, multi-screen light show.

Key Features

🎵 Real-Time FFT Audio Analysis: Separates live music into bass, mids, and treble for distinct visual reactions.

⚡ Zero-Latency Sync: A custom NTP 4-timestamp handshake algorithm ensures sub-millisecond visual sync across devices.

🛜 Off-Grid P2P Networking: Uses Google Nearby Connections—no router, Wi-Fi network, or cellular data required.

📱 Host & Join Modes: One phone listens to the music and dictates the rhythm; the others instantly follow the beat.

🎨 Jetpack Compose Canvas: High-performance, 60fps additive blending graphics, strobes, and particle physics.
