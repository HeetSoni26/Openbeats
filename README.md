<div align="center">

<img src="app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml" alt="Openbeats" width="120" />

# OPENBEATS


### The YouTube Music app you always wanted

**No Ads** | **No Subscription** | **Full Control**

[![Release](https://img.shields.io/github/v/release/HeetSoni26/Openbeats?style=for-the-badge&logo=github&logoColor=white&labelColor=18181B&color=00D4FF)](https://github.com/HeetSoni26/Openbeats/releases/latest)
[![Downloads](https://img.shields.io/github/downloads/HeetSoni26/Openbeats/total?style=for-the-badge&logo=github&logoColor=white&labelColor=18181B&color=10B981)](https://github.com/HeetSoni26/Openbeats/releases)
[![Stars](https://img.shields.io/github/stars/HeetSoni26/Openbeats?style=for-the-badge&logo=github&logoColor=white&labelColor=18181B&color=F59E0B)](https://github.com/HeetSoni26/Openbeats/stargazers)
[![License](https://img.shields.io/github/license/HeetSoni26/Openbeats?style=for-the-badge&labelColor=18181B&color=EF4444)](https://github.com/HeetSoni26/Openbeats/blob/main/LICENSE)

![Kotlin](https://img.shields.io/badge/Kotlin-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/Compose-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white)
![Architecture](https://img.shields.io/badge/Architecture-MVVM-52525B?style=for-the-badge)

</div>

---

## Download

### Stable Release

| Source | Link |
|--------|------|
| **GitHub** | [![Get Openbeats on GitHub](https://img.shields.io/badge/Get_on-GitHub-181717?style=for-the-badge&logo=github&logoColor=white)](https://github.com/HeetSoni26/Openbeats/releases/latest) |

---

## Why Openbeats?

Openbeats is a **modern YouTube Music client** built with a beautiful glassmorphism UI.

- **Faster** than the official app
- **Ad-free** listening experience
- **Offline-first** with encrypted caching
- **Glassmorphism** design with dynamic mesh gradients
- Built for **audiophiles** and power users

> YouTube Music -- *but unlocked*

---

## Highlight Feature

### Real-Time Synced Lyrics

Experience lyrics like never before:

- Word-by-word sync
- Smooth animations
- Translation support
- Fully immersive playback

## Features

### Core Experience

- Ad-Free Playback
- Full Library Sync
- Offline Caching (Encrypted)
- Background Playback
- Sleep Timer

### Reliability & Self-Healing Playback

Songs keep playing even when YouTube tightens enforcement:

- **Self-healing client chain** — the app probes which stream clients YouTube currently accepts and reorders the fallback chain automatically
- **Real BotGuard PO tokens** — generated in-app through a private WebView, keeping web clients unblocked
- **Mid-song recovery** — a killed stream is re-minted and playback resumes instead of stopping
- **Graduated client bans** — a single hiccup only affects that song, never the whole app
- **One-tap engine reset** — the sync button beside search gives you a fresh identity, fresh tokens and fresh routes without reopening the app
- **Playback diagnostics** — see exactly which clients are healthy, truncated or bot-gated, and email a full report to the developer

### Backup & Your Data

- **Backups that include offline music** — songs you listened to travel inside the backup and play offline immediately after a restore
- **Weekly auto-backup** to a folder you pick once, keeping the newest 3
- **Safe restore** — device-session state is sanitized on the way in, so an old backup can never break a new install

### Audio Engine

- Gapless Playback
- Crossfade Engine
- Silence Skipping
- Loudness Normalization (EBU R128)
- Tempo & Pitch Control
- System EQ Integration

### UI & Discovery

- Glassmorphism UI with mesh gradients
- Material You (Dynamic Colors)
- Synced Lyrics + Translation
- Discord Rich Presence
- Personalized Home Feed
- Year in Review Stats

---

## Recent Updates

| Version | Highlights |
|---------|------------|
| **v1.4.1** | Reset button beside search (fresh identity + tokens in one tap), automatic token rotation after web-client kills |
| **v1.4.0** | Fixed the root cause of songs load-then-stop: one transient 403 no longer bans the healthy client for 10 minutes |
| **v1.3.x** | Backups can carry offline music, weekly auto-backup, email diagnostics reports |
| **v1.2.x** | Self-healing playback stack: real PO tokens, client probing, safe backup restores |

Full notes: [Releases](https://github.com/HeetSoni26/Openbeats/releases)

---

## Architecture

Built using **modern Android engineering principles**:

- MVVM + Clean Architecture
- Unidirectional Data Flow (UDF)
- Modular & scalable codebase

---

## Tech Stack

| Layer | Stack |
|-------|-------|
| **Language** | Kotlin |
| **UI** | Jetpack Compose + Material 3 |
| **Audio** | Media3 / ExoPlayer |
| **Database** | Room (Encrypted) |
| **Networking** | Ktor + OkHttp |
| **Async** | Coroutines + Flow |
| **Build** | Gradle KTS |

---

## Project Structure

```
openbeats/
├── app/
├── innertube/
├── lrclib/
├── kizzy/
├── canvas/
├── lastfm/
├── kugou/
├── betterlyrics/
└── simpmusic/
```

---

## Getting Started

### Requirements

- Android Studio Ladybug+
- JDK 17
- Android SDK 34+

### Run Locally

```bash
git clone https://github.com/HeetSoni26/Openbeats.git
cd Openbeats
```

Open in Android Studio -> Sync -> Run

---

## Community & Support

Found a bug or a song that stops? The fastest way to reach the developer:

- **Email:** [heetks2607@gmail.com](mailto:heetks2607@gmail.com) — in the app: **Settings → Playback diagnostics → Email report** sends a full diagnostic report automatically
- **GitHub Issues:** [Report a Bug or Request a Feature](https://github.com/HeetSoni26/Openbeats/issues)
- **GitHub Discussions:** [Open a Discussion](https://github.com/HeetSoni26/Openbeats/discussions)

---

## Credits

Openbeats is built upon the work of several open-source projects. Huge respect to:

- **InnerTune** - Base framework
- **Kizzy** - Discord Rich Presence
- **SimpMusic** - Lyrics integration
- **BetterLyrics** - Enhanced lyrics
- **LrcLib** - Synced lyrics API

---

## Legal

Openbeats is an independent client and is **not affiliated** with YouTube or Google.
Please support artists through official platforms.

Licensed under **GPL-3.0**

---

<div align="center">

**Built with passion by [Heet Soni](https://github.com/HeetSoni26)**

**Star the repo if Openbeats impressed you**

**[Star History](https://star-history.com/#HeetSoni26/Openbeats&Date)**

</div>
