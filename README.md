![BioKey](docs/banner.png)

**Unlock your PC with your phone.**

[![License: GPLv3](https://img.shields.io/badge/license-GPLv3-blue.svg)](LICENSE)
![Platforms: Windows | Linux | macOS](https://img.shields.io/badge/platform-Windows%20%7C%20Linux%20%7C%20macOS-lightgrey)
![C++23](https://img.shields.io/badge/C%2B%2B-23-00599C)
![Qt 6](https://img.shields.io/badge/Qt-6-41CD52)

<p align="center" style="text-align: center;">
  <a href="README.md"><img alt="English" src="https://img.shields.io/badge/English-1f6feb?style=flat-square"></a>
  <a href="README.fr.md"><img alt="Français" src="https://img.shields.io/badge/Fran%C3%A7ais-lightgrey?style=flat-square"></a>
</p>

> This is an independent fork of [PC Bio Unlock](https://github.com/MeisApps/pcbu-desktop) by MeisApps, licensed GPLv3. All credit for the original protocol, desktop app and security design goes to them — this fork builds on top of it with the changes described in [What's new in this fork](#whats-new-in-this-fork).

Instead of typing your password at the login screen, the lock screen or a permission prompt, just confirm it with your fingerprint or face on your Android phone.

This repository holds the desktop app. The companion Android app, **BioKey**, has been rewritten from scratch as its own independent client — it lives in a separate repository and isn't affiliated with or published under the original project's Google Play listing.

> [!WARNING]
> macOS support is considered experimental and not ready for end users.

<p align="center" style="text-align: center;">
  <a href="https://github.com/L-architec-T/BioKey/releases/latest"><img alt="Download for Windows" src="https://img.shields.io/badge/Download-Windows-0078D6?style=for-the-badge&logo=windows&logoColor=white"></a>
  <a href="https://github.com/L-architec-T/BioKey/releases/latest"><img alt="Download for Linux" src="https://img.shields.io/badge/Download-Linux-FCC624?style=for-the-badge&logo=linux&logoColor=black"></a>
  <a href="https://github.com/L-architec-T/BioKey/releases/latest"><img alt="Download for macOS" src="https://img.shields.io/badge/Download-macOS-000000?style=for-the-badge&logo=apple&logoColor=white"></a>
</p>

## Table of contents

- [What's new in this fork](#whats-new-in-this-fork)
- [Features](#features)
- [Screenshot](#screenshot)
- [Installation](#installation)
- [How it works](#how-it-works)
- [Security & privacy](#security--privacy)
- [Building from source](#building-from-source)
- [Troubleshooting](#troubleshooting)
- [Contributing](#contributing)
- [License](#license)
- [Links](#links)

## What's new in this fork

- **Dual UDP + FCM wake** — alongside the existing local UDP broadcast, wake requests can now also be mirrored over Firebase Cloud Messaging, so the unlock prompt still reaches the phone when Wi-Fi power-saving silently drops incoming UDP packets. Both channels carry the same wake ID so the phone can deduplicate the two instead of guessing from a time window.
- **`pcbu-lockd` daemon (Linux)** — a new background service that talks to AccountsService over D-Bus to auto-select the right user tile at the GDM login/lock screen, so the PAM conversation for the paired account actually starts without a physical click first.
- **Remote macros and commands** — paired phones can trigger custom keyboard macros on the PC, with keyboard-layout-aware key mapping including French AZERTY, plus remote lock and suspend commands, over a new lightweight command channel.
- **French localization** — the desktop app is now fully translated into French (`fr_FR`), alongside the existing languages.
- **Rebuilt Android client** — the companion phone app has been rewritten from scratch and rebranded as **BioKey**.
- Refreshed app icons and assorted UI polish across the desktop app.

## Features

- Unlock your PC with your Android phone
- Works over Wi-Fi, LAN or Bluetooth. No account and no internet connection required
- Finds your PC automatically, so there is nothing to configure
- Pair by scanning a QR code, or by entering a pairing code by hand
- Pair multiple phones, and different phones for different user accounts
- Wake your PC with Wake-on-LAN before unlocking it
- Windows and Linux on x64 and ARM, plus macOS\* on Apple Silicon

### Where it works

| | Works with |
|---|---|
| **Windows** | Login and lock screen, UAC prompts |
| **Linux** | Login and lock screen (GDM, SDDM, LightDM, KDE, Cinnamon, Hyprlock), `sudo` and `polkit` |
| **macOS**\* | `sudo` and system permission prompts |

\* experimental

## Screenshot

![BioKey desktop app on Linux](docs/screenshot-linux.png)

## Installation

Download the latest release for your system from the [releases page](https://github.com/L-architec-T/BioKey/releases), then follow the [installation guide](https://meis-apps.com/pc-bio-unlock/how-to-install), which walks through the system requirements, the setup and the pairing.

## How it works

When your PC needs your password, it asks your paired phone over your local network or Bluetooth. You confirm with your fingerprint or face, and the phone sends back the key that lets your PC unlock itself. Everything the two devices exchange is encrypted so that only they can read it.

While pairing you choose how the two devices talk to each other.

**Automatic** means your phone pops up the unlock prompt by itself as soon as your PC asks for it. You can pick the connection it uses:

- **UDP** *(recommended)*: works on any local network, and keeps working when your phone's IP address changes.
- **TCP**: a little faster, but best with a static IP for your phone.
- **Bluetooth**: no network needed, ideal for notebooks and tablets on the go, at the cost of some battery life.

**Manual** means nothing waits in the background on your phone. You simply open the app and start the unlock from there, if you prefer it that way.

If anything ever goes wrong, nothing is lost: hold <kbd>Left Ctrl</kbd> + <kbd>Left Alt</kbd> to cancel, and log in with your password as usual.

## Security & privacy

BioKey is designed so that using it does not make your PC easier to break into.

**Your password never leaves your PC.** It is stored encrypted, and the key to it lives only on your paired phone. Your phone never sees your password, it only holds the key, and the password is never sent over the network or to anyone else. Without your phone the stored copy is unreadable, so someone who takes the file off your PC gets nothing usable. On top of that, the file is locked down so that only the system can access it.

**Nothing is bypassed.** The unlock ends with your normal password being checked by Windows or PAM, exactly as if you had typed it. No security policy, account restriction or lockout is skipped.

**Everything is end-to-end encrypted.** Pairing and unlocking are protected with modern authenticated encryption (AES-256-GCM), with keys that are exchanged out of band through the QR code you scan. Every message is signed against tampering and carries a timestamp, so recorded traffic cannot be replayed later to unlock your PC. Each unlock also has to answer a one-time challenge, so an old response is worthless.

**Nothing leaves your network.** There is no account and no telemetry. Whatever your PC and your phone say to each other is encrypted with keys that only those two devices hold, so it is unreadable to anyone else, including us. The only requests that leave your network are the check for a new version and, if you decide to install one, the download itself.

**Your PC stays closed.** The app only accepts connections while you are pairing or while an unlock is actually running, and stops listening again as soon as it is done.

**It's auditable.** The full source of both the app and the login components is in this repository, under the GPL.

Found a security issue? Please report it privately through [GitHub security advisories](https://github.com/L-architec-T/BioKey/security/advisories) instead of a public issue.

## Building from source

You need CMake 3.22+, a C++23 compiler, Qt 6 and OpenSSL 3. Boost, spdlog, nlohmann/json and cpp-httplib are downloaded automatically during configuration.

<details>
<summary><strong>Windows</strong></summary>
<br>

Visual Studio 2026, the Windows SDK, [Inno Setup](https://jrsoftware.org/isinfo.php) for the installer, and [vcpkg](https://github.com/microsoft/vcpkg) with `VCPKG_ROOT` set:

```bash
vcpkg install --overlay-triplets=cmake/vcpkg-triplets --triplet x64-windows-static openssl
vcpkg install --overlay-triplets=cmake/vcpkg-triplets --triplet x64-windows-static-md openssl
```

For ARM builds, use the `arm64-windows-static` and `arm64-windows-static-md` triplets instead.

</details>

<details>
<summary><strong>Linux</strong></summary>
<br>

```bash
sudo apt install build-essential pkg-config cmake git \
     libssl-dev libpam-dev libcrypt-dev libbluetooth-dev \
     libgl1-mesa-dev libegl1-mesa-dev libxkbcommon-x11-dev libxcb-cursor-dev
```

</details>

<details>
<summary><strong>macOS</strong></summary>
<br>

Xcode command line tools and Qt for macOS.

</details>

### Build

```bash
cmake -B build -DCMAKE_BUILD_TYPE=Debug
cmake --build build
```

Qt is detected automatically; pass `-DQT_BASE_DIR=<path>` to pick a specific installation.

### Packaging

`pkg/build-desktop.sh` builds and packages a release: a setup executable on Windows, an AppImage on Linux, or a disk image on macOS. Platform, architecture and Qt path are detected automatically, or can be set through the `PLATFORM`, `ARCH` and `QT_BASE_DIR` environment variables.

## Troubleshooting

Connection problems, pairing issues and how to get back into a PC that won't let you in are covered in the [troubleshooting guide](https://meis-apps.com/pc-bio-unlock/troubleshooting).

For anything else, the app has two tools built in: a log viewer for both the app and the login component, with a *debug logging* switch in the settings, and an unlock test that lets you try a paired device without locking your screen.

## Contributing

Issues and pull requests are welcome. A few pointers:

- The code style is enforced by the checked-in `.clang-format` and `.clang-tidy`. Please run clang-format before submitting.
- **Translations**: copy `common/res/en_US.json`, translate the values, then register the new file in `common/CMakeLists.txt` (`embed_json`), `common/src/utils/I18n.cpp` and `LocaleHelper`.
- **Bug reports**: please use the issue template and attach the desktop and module logs; they are what make network and PAM problems diagnosable.

## License

[GNU General Public License v3.0](LICENSE)

## Links

- Repository: [L-architec-T/BioKey](https://github.com/L-architec-T/BioKey)
- Android app: not yet published — see the BioKey Android client repository
- Original project (upstream): [MeisApps/pcbu-desktop](https://github.com/MeisApps/pcbu-desktop)
