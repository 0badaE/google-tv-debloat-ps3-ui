# Google TV Debloat + PS3 UI

![Crossbar, the PS3-style home screen, running on a Google TV Streamer](docs/crossbar-home.png)

Make a Google TV yours, from your computer, with nothing to root. Two parts, usable separately:

1. **Debloat**: turn off ads, tracking and bloat. Nothing gets deleted: apps are **disabled**
   (`pm disable-user`), and one command brings them all back.
2. **Crossbar**: an optional PS3-style home screen with a live, music-reactive wave wallpaper and a
   matching screensaver. See [launcher/README.md](launcher/README.md).

Tested on the **Google TV Streamer 4K** (Android 14). Every one of its 140 packages has a verdict in
[docs/app-inventory.md](docs/app-inventory.md).

### The wallpaper

Crossbar's background is the PS3's flowing wave, recreated natively in OpenGL ES 3 at 60 fps and
ported from [linkev/PlayStation-3-XMB](https://github.com/linkev/PlayStation-3-XMB) (MIT).

- **Reacts to music.** Play a song in any app and the wave speeds up, its glow breathes with the
  song, and sparkles burst in the loud parts. It reads what the TV outputs, never the microphone,
  and adapts to each song's own loudness.
- **PS3 month colors.** The default theme changes color every month like the console, and shifts from
  day to night. Purple, Classic Blue, Black and Pink are also built in.
- **Fully tweakable from the remote.** Wave Controls exposes every setting of the original as a
  slider: colors, speed, shape, glow, and sparkle count, size and speed. Changes show live and also
  apply to the screensaver.

Details: [launcher/README.md → Wallpaper](launcher/README.md#wallpaper).

## Not comfortable with the terminal? Let an AI do it

You don't need to understand ADB, PowerShell or anything else in this README. An **AI coding agent**
can do the whole setup for you. It's an assistant, such as [Claude Code](https://claude.com/claude-code),
that runs on your computer and can read this project and type the commands itself. You talk to it in
plain English, and it handles the technical part.

**What you do (the AI can't touch your TV):**

1. **Download this project.** On this page, click **Code → Download ZIP**, then unzip it.
2. **Turn on Developer options on the TV.** Settings → System → About, then click *Android TV OS build*
   7 times.
3. **Turn on Wireless debugging.** Settings → System → Developer options → Wireless debugging → On.
   Your TV and computer must be on the same Wi-Fi.
4. **Open the AI agent in the unzipped folder.** For Claude Code on Windows 11: right-click inside the
   folder, choose **Open in Terminal**, and type `claude`.
5. **Tell it what you want.** For example:

   > Read the README. I want to debloat my Google TV and install the Crossbar PS3 home screen.
   > Walk me through it step by step. Do a dry run first and explain what will change before you
   > change anything.

6. **Read it what the TV shows.** When it asks, open Wireless debugging → **Pair device with pairing
   code** on the TV, and type the 6-digit code and the `IP:port` into the chat.

**What the AI does:** it downloads ADB and the build tools, pairs with your TV, runs a dry run and
explains it, hides the bloat, builds and installs Crossbar, grants its permissions, and sets up the
screensaver. You'll still need to turn on **Crossbar Home button** yourself under Settings →
Accessibility on the TV.

**Good to know:**

- The agent asks before it runs commands. You can read each one and say no.
- Nothing here is permanent. If you don't like something, ask the AI to "restore everything" or
  "switch back to the normal Google TV home screen".
- The debloat works from Windows, macOS or Linux. Building Crossbar needs Windows for now.

## Contents

- [What gets hidden](#what-gets-hidden)
- [How it works](#how-it-works)
- [Requirements](#requirements)
- [Pair once](#pair-once)
- [Run](#run)
- [Customizing](#customizing)
- [Ads on the Home screen](#ads-on-the-home-screen)
- [Troubleshooting](#troubleshooting)
- [Project layout](#project-layout)
- [Notes](#notes)
- [Disclaimer](#disclaimer)

## What gets hidden

The default list, [`lists/debloat-list.txt`](lists/debloat-list.txt), hides 22 packages:

| Group | Packages |
|---|---|
| **Ads and tracking** (10) | Privacy Sandbox ad services (`adservices.api`, `ext.adservices.api`, `sdksandbox`, `ondevicepersonalization`), federated learning (`federatedcompute`), remote-press logging (`tv.remotecontrol.logging`), feedback and crash uploads (`feedback`, `tv.feedbackconsent`), RLZ partner pings (`partnersetup`), Play Auto Install promos (`android.autoinstalls.config.google.sabrina`) |
| **Unused Google apps** (2) | Play Games, Google TV / Play Movies store (`videos`) |
| **Not useful on a TV** (10) | Health Connect (2), Dynamic System Updates, camera extensions, USB file transfer (MTP), Calendar sync, wallpaper picker, basic screensaver, HTML viewer, old Backdrop stub |

Deliberately **kept**, and commented out at the bottom of the list so you can opt in: voice search /
Assistant (`katniss`), Chromecast built-in (`mediashell`), Ambient mode (`dreamx`), TalkBack and
Speech Services.

Never touched: Live TV, the remote, Bluetooth, picture and sound settings, network and core system
services. They're marked **NEVER** in the inventory. Device Lock (`devicelockcontroller`) can't be
hidden at all; Android protects it.

## How it works

- `debloat` disables every package in the list.
- `restore` re-enables them, either all of them or only the ones you name.
- Before every change, both scripts save a snapshot of every package's state to `backups/state-*.txt`,
  and they log each change to `backups/changes.log`.
- Packages the list doesn't name are never touched.

If a package refuses to be disabled, the script falls back to `pm uninstall -k --user 0`. That only
removes the app for your user profile and leaves the APK on the system partition, so `restore` can
bring it back with `cmd package install-existing`.

## Requirements

1. **ADB** (Android platform-tools). Download it from
   [developer.android.com/tools/releases/platform-tools](https://developer.android.com/tools/releases/platform-tools)
   and unzip it into `tools/` so you have `tools/platform-tools/adb`, or install it so `adb` is on your PATH.
2. **Developer options on the TV**: Settings → System → About → click *Android TV OS build* 7 times.
3. **Wireless debugging**: Settings → System → Developer options → Wireless debugging → On. Your TV
   and computer must be on the same network.

## Pair once

On the TV, open Wireless debugging → **Pair device with pairing code**. The popup shows a 6-digit
code and an `IP:port` that's only for pairing. Keep it open while you run:

```bash
adb pair 192.168.1.50:37123 123456
```

The main Wireless debugging screen shows a different `IP:port`. Use that one for every command below.
(`192.168.1.50` and the ports here are only examples. Replace them with what your TV shows.)

## Run

Always start with a dry run. It shows what would change and changes nothing.

**Windows (PowerShell)**

```powershell
powershell -ExecutionPolicy Bypass -File .\debloat.ps1 -Device 192.168.1.50:5555 -DryRun
powershell -ExecutionPolicy Bypass -File .\debloat.ps1 -Device 192.168.1.50:5555
powershell -ExecutionPolicy Bypass -File .\restore.ps1 -Device 192.168.1.50:5555
powershell -ExecutionPolicy Bypass -File .\restore.ps1 -Device 192.168.1.50:5555 -Package com.google.android.katniss
```

**macOS / Linux (bash)**

```bash
bash debloat.sh -d 192.168.1.50:5555 -n     # dry run
bash debloat.sh -d 192.168.1.50:5555
bash restore.sh -d 192.168.1.50:5555
bash restore.sh -d 192.168.1.50:5555 -p com.google.android.katniss
```

If only one device is connected, you can leave out `-Device` / `-d`. To use a different list, pass
`-List` / `-l <file>`.

## Customizing

Edit `lists/debloat-list.txt`, with one package per line (`#` starts a comment). Check
[docs/app-inventory.md](docs/app-inventory.md) for what each package does before you add it. If you
take a package out of the list, `restore` no longer touches it, so run restore *before* you remove a
package you want back.

The inventory is generated from [`packages.csv`](packages.csv). After editing the CSV, regenerate it:

```bash
bash docs/generate-inventory.sh
```

## Ads on the Home screen

Most ads come from Google TV's own home screen (`launcherx`), which can't be disabled without
breaking Home, its quick-settings panel and the setup wizard. Two ways around them:

- **Use Crossbar** ([launcher/README.md](launcher/README.md)): an ad-free home screen in front of
  Google TV, which keeps running underneath.
- **Or tone down Google TV** (menu names vary a little between software versions):
  - **Apps only mode**: Settings → Accounts & sign-in → your account → Apps only mode. This removes
    recommendations and promoted content from Home.
  - Settings → Privacy → Usage & diagnostics → Off.
  - Settings → Privacy → Ads → Delete advertising ID / Opt out of personalization.

## Troubleshooting

| Problem | Fix |
|---|---|
| `failed to connect` | Check the `IP:port` on the main Wireless debugging screen. The port changes whenever the TV restarts or Wireless debugging is turned off and on. |
| Device shows as `offline` | The TV no longer trusts this computer. Pair again (see [Pair once](#pair-once)), using the popup's own port. |
| `adb pair` hangs | You used the main screen's port. Pairing needs the port shown inside the pairing popup, while the popup is open. |
| PowerShell says scripts are disabled | Run them as shown, with `powershell -ExecutionPolicy Bypass -File …`. |
| An app came back after an update | System updates can re-enable apps. Run `debloat` again; already-hidden packages are skipped. |

## Project layout

| Path | What it is |
|---|---|
| `debloat.ps1`, `restore.ps1`, `common.ps1` | Windows scripts |
| `debloat.sh`, `restore.sh`, `common.sh` | macOS / Linux scripts |
| `lists/debloat-list.txt` | The packages to hide |
| `packages.csv` | Every package on the TV, with its group, verdict and description |
| `docs/app-inventory.md` | The same, as a readable table (generated) |
| `docs/generate-inventory.sh` | Regenerates the inventory from the CSV |
| `launcher/` | Crossbar, the PS3-style home screen |
| `tools/` | Your local ADB and build tools (git-ignored) |
| `backups/` | Per-device snapshots and change log (git-ignored) |

## Notes

- A factory reset re-enables everything. Re-run `debloat` afterwards.
- When you're done, turn Wireless debugging off on the TV. Nothing here needs it once it's set up.
- To use another Google TV / Android TV device, run `adb shell pm list packages -u`, compare it with
  the inventory, and open a PR with a list for that device.

## Disclaimer

Use at your own risk. Everything this tool does can be undone with `restore` or a factory reset.
Not affiliated with or endorsed by Google.
