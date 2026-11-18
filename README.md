# Dimly

Dim the picture of your Android TV / Google TV with a smooth dark layer, from the remote or from your home automation.

Some TVs only get darker by lowering contrast or brightness settings, which costs picture quality. Dimly lays a black layer of adjustable opacity over everything on screen instead, video and HDR included, and leaves the TV's picture settings alone.

## Features

- **Dock on the remote:** open Dimly from the home screen, adjust with ◀ ▶ in 5 % steps, close with BACK.
- **Smooth fades** between levels.
- **Never fully black** (at most 95 % darkness), so the dock and the picture stay findable.
- **Keeps its level** across restarts and reboots.
- **Optional network control:** off by default. When switched on, your home automation (Home Assistant, Node-RED, …) or any script on your home network can set the level with an HTTP request that carries a key.
- **Doesn't get in the way:** the layer never takes focus or input, so the remote keeps working normally underneath it.

## Install

You need a computer with [adb](https://developer.android.com/tools/adb) and network debugging enabled on the TV device (Settings → System → Developer options).

1. Download `Dimly-<version>.apk` from the [latest release](../../releases/latest) and install it:

   ```bash
   adb connect <tv-ip>
   adb install Dimly-1.0.0.apk
   ```

2. Enable Dimly's accessibility service. It draws the layer and keeps Dimly running in the background. Google TV hides that settings page, so do it once over adb. **Append** Dimly to the services that are already enabled instead of replacing them:

   ```bash
   adb shell settings get secure enabled_accessibility_services
   ```

   Then put the old value, a `:`, and Dimly's service back (leave out `<old value>:` if the first command printed `null`):

   ```bash
   adb shell settings put secure enabled_accessibility_services "<old value>:dev.adrianjost.dimly/dev.adrianjost.dimly.DimService"
   adb shell settings put secure accessibility_enabled 1
   ```

3. Open **Dimly** from the home screen.

## Using the dock

| Focus | Key | Action |
| --- | --- | --- |
| Brightness | ◀ ▶ | Darker / brighter in 5 % steps |
| Brightness | ▼ | Network control |
| Brightness | BACK or OK | Close |
| Network control | ◀ ▶ | Choose a button |
| Network control | OK | Switch network control on/off, show or hide the key |
| Network control | ▲ or BACK | Back to brightness |

The dock closes by itself after 15 seconds without input. The key is hidden every time the dock opens.

## Network control

Switch it on in the dock (▼, then OK). The dock then shows the address and, after **Show key**, the key. Every request needs the key, either as an `X-Key` header or as a `key` query parameter.

| Request | What it does |
| --- | --- |
| `POST /dim?level=40` | Sets the darkness, 0 (off) to 95. Optional `fade` in milliseconds (default 800, 0 = instant, max 10000). |
| `GET /status` | Returns the current darkness. |

Both answer `{"level": <n>}`. A missing or wrong key gets HTTP 401.

```bash
curl -X POST -H "X-Key: <key>" "http://<tv-ip>:8099/dim?level=40&fade=2000"
curl "http://<tv-ip>:8099/status?key=<key>"
```

### Home Assistant

`secrets.yaml`:

```yaml
dimly_key: <key>
```

`configuration.yaml`:

```yaml
rest_command:
  dimly_set:
    url: "http://<tv-ip>:8099/dim?level={{ level | int(0) }}"
    method: POST
    headers:
      X-Key: !secret dimly_key
    timeout: 5
```

Restart Home Assistant after adding `rest_command` for the first time, then call `rest_command.dimly_set` with `level: 0–95` from a script or an automation. Give the TV device a fixed IP address in your router so the URL keeps working.

### Using your own key

Dimly creates a random key on first use. To set your own instead, for example to keep the key your automation already uses after a reinstall:

```bash
adb shell am broadcast -a dev.adrianjost.dimly.SET_KEY -n dev.adrianjost.dimly/.SetKeyReceiver --es key <your-key>
```

The key needs at least 12 characters and no spaces. Only the adb shell can send this; other apps on the device can't.

## Build

Needs JDK 17 and the Android SDK (platform 36).

```bash
./gradlew assembleRelease
```

The APK lands in `app/build/outputs/apk/release/`. It is signed with the debug key, so it installs without a keystore.

## Code map

Everything lives in `app/src/main/java/dev/adrianjost/dimly`:

- `DimService.kt`: accessibility service: the dim layer, fades, stored settings
- `DimServer.kt`: the optional HTTP API
- `ControlPanel.kt`: the dock
- `MainActivity.kt`: takes the remote's keys while the dock is open
- `SetKeyReceiver.kt`: setting your own key over adb

## License

[MIT](LICENSE)
