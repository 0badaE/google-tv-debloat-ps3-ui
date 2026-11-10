# App inventory: Google TV Streamer 4K

Every package on a stock Google TV Streamer 4K (codename `kirkwood`, Android 14, build `UTTK.260317.003`): 140 in total.
This file is generated from [`packages.csv`](../packages.csv) by `docs/generate-inventory.sh`. Edit the CSV, not this file.

| Verdict | Meaning |
|---|---|
| **BLOAT** | Ads, tracking, or unused apps. Safe to hide. |
| **OPTIONAL** | Safe to hide, but it turns off a feature. Hide it only if you don't use that feature. |
| **YOURS** | An app you installed. Keep it or hide it, your choice. |
| **NEVER** | Live TV, the remote, Bluetooth, picture and sound, network, or core system. Hiding these can break the device. |

"Hide" means `pm disable-user`: the app stays on the device and comes back with `restore`.

## Streaming apps (installed by you) (5)

| Package | Verdict | What it does |
|---|---|---|
| `com.amazon.amazonvideo.livingroom` | **YOURS** | Prime Video |
| `com.disney.disneyplus` | **YOURS** | Disney+ |
| `com.netflix.ninja` | **YOURS** | Netflix |
| `com.google.android.youtube.tv` | **YOURS** | YouTube for TV |
| `com.spotify.tv.android` | **YOURS** | Spotify |

## Ads tracking and telemetry (11)

| Package | Verdict | What it does |
|---|---|---|
| `com.google.android.adservices.api` | **BLOAT** | Privacy Sandbox ad services: ad topics and ad-conversion measurement |
| `com.google.android.ext.adservices.api` | **BLOAT** | Second copy of Privacy Sandbox ad services (ExtServices module) |
| `com.google.android.sdksandbox` | **BLOAT** | Sandbox that runs third-party ad SDKs for Privacy Sandbox |
| `com.google.android.ondevicepersonalization.services` | **BLOAT** | On-device ad and content personalization |
| `com.google.android.federatedcompute` | **BLOAT** | Federated learning: trains Google models on your usage and uploads the results |
| `com.google.android.tv.remotecontrol.logging` | **BLOAT** | Logs how you use the remote and sends it to Google |
| `com.google.android.feedback` | **BLOAT** | Uploads 'Send feedback' and crash reports to Google |
| `com.android.tv.feedbackconsent` | **BLOAT** | Consent prompt for sending feedback and bug reports |
| `com.google.android.partnersetup` | **BLOAT** | RLZ partner pings that tell Google which partner sold the device |
| `android.autoinstalls.config.google.sabrina` | **BLOAT** | Play Auto Install config: lets Google push promoted apps to the device |
| `com.google.android.tvrecommendations` | **BLOAT** | Old recommendation feed stub (already disabled at the factory) |

## Unused Google apps (3)

| Package | Verdict | What it does |
|---|---|---|
| `com.google.android.play.games` | **BLOAT** | Google Play Games |
| `com.google.android.apps.education.cast2class` | **BLOAT** | Cast for Education (already removed for your user at the factory) |
| `com.android.gallery3d` | **BLOAT** | Gallery app (already disabled at the factory) |

## Not useful on a TV (11)

| Package | Verdict | What it does |
|---|---|---|
| `com.google.android.healthconnect.controller` | **OPTIONAL** | Health Connect (fitness data hub for phones) |
| `com.google.android.health.connect.backuprestore` | **OPTIONAL** | Health Connect backup |
| `com.google.android.devicelockcontroller` | **NEVER** | Device Lock for devices bought on credit (inert here; Android refuses to disable it) |
| `com.android.dynsystem` | **OPTIONAL** | Dynamic System Updates (developers booting test images) |
| `com.android.cameraextensions` | **OPTIONAL** | Camera extensions (the device has no camera) |
| `com.android.mtp` | **OPTIONAL** | USB file transfer (MTP) to a computer |
| `com.google.android.syncadapters.calendar` | **OPTIONAL** | Google Calendar sync |
| `com.android.wallpaperpicker` | **OPTIONAL** | Wallpaper picker (Google TV has no wallpaper) |
| `com.android.dreams.basic` | **OPTIONAL** | Basic color screensaver (not your active screensaver) |
| `com.android.htmlviewer` | **OPTIONAL** | Shows legal and license pages in Settings |
| `com.google.android.backdrop` | **OPTIONAL** | Old Backdrop screensaver stub (Ambient mode replaced it) |

## Features you may use (6)

| Package | Verdict | What it does |
|---|---|---|
| `com.google.android.katniss` | **OPTIONAL** | Google app for TV: voice search and Assistant (mic button and the search bar on Home) |
| `com.google.android.apps.mediashell` | **OPTIONAL** | Chromecast built-in: casting from your phone or laptop |
| `com.google.android.apps.tv.dreamx` | **OPTIONAL** | Ambient mode screensaver (photos and art plus suggestions); this is your active screensaver |
| `com.google.android.videos` | **OPTIONAL** | Google TV / Play Movies: the Shop tab and the movies you bought or rented from Google |
| `com.google.android.marvin.talkback` | **OPTIONAL** | TalkBack screen reader (accessibility) |
| `com.google.android.tts` | **OPTIONAL** | Speech Services: text-to-speech for TalkBack and Assistant replies |

## Live TV (3)

| Package | Verdict | What it does |
|---|---|---|
| `com.android.providers.tv` | **NEVER** | TV Provider: the channel and program database behind the Live tab |
| `com.google.android.apps.tv.launcherx` | **NEVER** | Google TV Home screen (includes the Live tab and the quick-settings panel) |
| `com.google.android.tvlauncher` | **NEVER** | Old launcher stub that the Home screen falls back on |

## Remote Bluetooth and input (12)

| Package | Verdict | What it does |
|---|---|---|
| `com.google.android.bluetooth` | **NEVER** | Bluetooth stack (the remote connects over Bluetooth) |
| `com.google.android.bluetooth.kirkwood` | **NEVER** | Bluetooth settings for this device |
| `com.android.bluetooth.chromecast` | **NEVER** | Bluetooth settings for Chromecast hardware |
| `com.google.android.tv.axel` | **NEVER** | Remote setup: pairing and programming the remote to control your TV's power and volume |
| `com.google.android.tv.axel.overlay.kirkwood` | **NEVER** | Remote setup settings for this device |
| `com.google.android.tv.dfuservice` | **NEVER** | Remote firmware updates and Find My Remote |
| `com.google.android.tv.overlay.dfuservice` | **NEVER** | Remote firmware update settings |
| `com.google.android.tv.remote.service` | **NEVER** | Android TV Remote Service: lets you use your phone as a remote |
| `com.android.inputdevices` | **NEVER** | Keyboard and remote layouts |
| `com.google.android.inputmethod.latin` | **NEVER** | Gboard: the on-screen keyboard |
| `com.android.companiondevicemanager` | **NEVER** | Pairs phones and accessories |
| `com.mediatek.hdmicecframeworkresoverlay` | **NEVER** | HDMI-CEC: lets the remote control your TV's power and volume over HDMI |

## Picture sound and settings (11)

| Package | Verdict | What it does |
|---|---|---|
| `com.android.tv.settings` | **NEVER** | The Settings app (picture and sound settings included) |
| `com.android.tv.settings.gms.resoverlay` | **NEVER** | Settings resources |
| `com.android.tv.settings.google.resoverlay` | **NEVER** | Settings resources |
| `com.android.tv.settings.kirkwood.resoverlay` | **NEVER** | Settings resources for this device |
| `com.android.tv.settings.kirkwood.securesettingsoverlay` | **NEVER** | Settings secure defaults for this device |
| `com.google.android.tv.settings.energymodes.resoverlay` | **NEVER** | Settings: energy modes |
| `com.google.tv.settings.chromecast` | **NEVER** | Settings resources for Chromecast hardware |
| `com.android.settings.intelligence` | **NEVER** | Settings search and suggestions |
| `com.dolby.android.audio.service` | **NEVER** | Dolby Audio and Atmos |
| `com.google.android.audioproxy` | **NEVER** | Audio output: spatial audio and audio sync (latency) settings |
| `com.google.android.audioproxy.overlay` | **NEVER** | Audio output settings |

## Google core services (22)

| Package | Verdict | What it does |
|---|---|---|
| `com.google.android.gms` | **NEVER** | Google Play services |
| `com.google.android.gsf` | **NEVER** | Google Services Framework |
| `com.android.vending` | **NEVER** | Play Store (needed to install and update apps) |
| `com.google.android.webview` | **NEVER** | Android System WebView (apps use it to show web pages and sign-in screens) |
| `com.google.android.overlay.googlewebview` | **NEVER** | WebView settings |
| `com.google.android.configupdater` | **NEVER** | Updates security certificates and system config |
| `com.google.android.modulemetadata` | **NEVER** | Info about system update modules |
| `com.google.android.ext.services` | **NEVER** | Core system extension services |
| `com.google.android.ext.shared` | **NEVER** | Shared system library |
| `com.google.android.rkpdapp` | **NEVER** | Remote key provisioning (DRM keys that Netflix and others need for HD playback) |
| `com.google.android.packageinstaller` | **NEVER** | Installs and uninstalls apps |
| `com.google.android.permissioncontroller` | **NEVER** | App permissions |
| `com.google.android.safetycenter.resources` | **NEVER** | Permission and safety resources |
| `com.google.android.tungsten.setupwraith` | **NEVER** | Setup wizard (also handles account linking and factory reset) |
| `com.google.android.chromecast.setupcustomization` | **NEVER** | Setup wizard settings for this device |
| `com.google.android.chromecast.appstate` | **NEVER** | Tracks which app is in front (always-running system service) |
| `com.google.android.chromecast.chromecastservice` | **NEVER** | Chromecast system service (MediaTek chip) |
| `com.google.android.chromecast.chromecastservice.kirkwood` | **NEVER** | Chromecast system service settings |
| `com.google.android.chromecast.chromecastservice.kirkwoodaudio` | **NEVER** | Chromecast system service audio settings |
| `com.google.android.chromecast.chromecastserviceoverlay` | **NEVER** | Chromecast system service settings |
| `com.netflix.tokenmanager` | **NEVER** | Netflix device token (Netflix sign-in and the remote's Netflix button) |
| `com.mediatek.nfxservice` | **NEVER** | MediaTek Netflix service (always-running system service) |

## Android system (41)

| Package | Verdict | What it does |
|---|---|---|
| `android` | **NEVER** | Android framework |
| `android.auto_generated_rro_product__` | **NEVER** | Framework settings |
| `android.auto_generated_rro_vendor__` | **NEVER** | Framework settings |
| `android.chromecast` | **NEVER** | Framework settings for Chromecast hardware |
| `com.android.systemui` | **NEVER** | System UI (notifications and dialogs) |
| `com.android.systemui.auto_generated_rro_product__` | **NEVER** | System UI settings |
| `com.google.android.systemui.gtvoverlayx` | **NEVER** | System UI settings for Google TV |
| `com.android.providers.settings` | **NEVER** | Stores every system setting |
| `com.android.providers.settings.auto_generated_rro_product__` | **NEVER** | Default system settings |
| `com.android.tv.overlay.settingsprovider` | **NEVER** | Default system settings for TV |
| `com.google.android.tv.overlay.settingsprovider` | **NEVER** | Default system settings for Chromecast |
| `com.google.android.overlay.gtvssettingsprovider` | **NEVER** | Default system settings for Google TV |
| `com.google.android.overlay.tvconfigx` | **NEVER** | Google TV config |
| `com.android.tv.overlay.framework` | **NEVER** | TV framework settings |
| `com.android.tv.overlay.framework.kirkwood` | **NEVER** | TV framework settings for this device |
| `com.android.tv.overlay.lps.disabled` | **NEVER** | Low-power standby setting |
| `com.android.tv.frameworkpackagestubs` | **NEVER** | Placeholders that stop missing-app crashes |
| `com.android.providers.calendar` | **NEVER** | Calendar storage (Google services expect it) |
| `com.android.providers.contacts` | **NEVER** | Contacts storage (Google account sign-in uses it) |
| `com.android.providers.downloads` | **NEVER** | Download manager (Play Store and system updates) |
| `com.android.providers.media` | **NEVER** | Media storage |
| `com.google.android.providers.media.module` | **NEVER** | Media storage module |
| `com.android.providers.userdictionary` | **NEVER** | Keyboard dictionary |
| `com.android.externalstorage` | **NEVER** | USB storage access |
| `com.android.shell` | **NEVER** | ADB shell (this tool needs it) |
| `com.android.intentresolver` | **NEVER** | 'Open with' app chooser |
| `com.android.keychain` | **NEVER** | Certificate storage |
| `com.android.certinstaller` | **NEVER** | Certificate installer |
| `com.android.se` | **NEVER** | Secure element (hardware security) |
| `com.android.pacprocessor` | **NEVER** | Proxy auto-config |
| `com.android.proxyhandler` | **NEVER** | Proxy support |
| `com.android.vpndialogs` | **NEVER** | VPN connection prompts |
| `com.android.location.fused` | **NEVER** | Location (time zone and regional content) |
| `com.android.managedprovisioning` | **NEVER** | Device setup and provisioning |
| `com.android.backupconfirm` | **NEVER** | Backup and restore prompts |
| `com.android.localtransport` | **NEVER** | Local backup |
| `com.android.sharedstoragebackup` | **NEVER** | Shared storage backup |
| `com.android.wallpaperbackup` | **NEVER** | Wallpaper backup |
| `com.android.cts.ctsshim` | **NEVER** | System update test stub (updates check for it) |
| `com.android.cts.priv.ctsshim` | **NEVER** | System update test stub (updates check for it) |
| `com.android.virtualmachine.res` | **NEVER** | Virtualization resources |

## Network (15)

| Package | Verdict | What it does |
|---|---|---|
| `com.google.android.networkstack` | **NEVER** | Network stack (Wi-Fi and Ethernet connections) |
| `com.google.android.tv.overlay.networkstack` | **NEVER** | Network stack settings |
| `com.android.networkstack.chromecast` | **NEVER** | Network stack settings for Chromecast hardware |
| `com.google.android.networkstack.tethering` | **NEVER** | Tethering and network module |
| `com.google.android.networkstack.tethering.kirkwood` | **NEVER** | Tethering settings |
| `com.android.networkstack.tethering.chromecast` | **NEVER** | Tethering settings for Chromecast hardware |
| `com.google.android.connectivity.resources` | **NEVER** | Connectivity resources |
| `com.google.android.connectivity.resources.kirkwood` | **NEVER** | Connectivity resources for this device |
| `com.google.android.wifi.resources` | **NEVER** | Wi-Fi resources |
| `com.google.android.wifi.resources.kirkwood` | **NEVER** | Wi-Fi resources for this device |
| `com.android.tv.overlay.wifi.resources` | **NEVER** | Wi-Fi settings for TV |
| `com.google.android.wifi.dialog` | **NEVER** | Wi-Fi prompts |
| `com.google.android.captiveportallogin` | **NEVER** | Sign-in page for hotel and public Wi-Fi |
| `com.google.android.hotspot2.osulogin` | **NEVER** | Passpoint Wi-Fi sign-in |
| `com.google.android.uwb.resources` | **NEVER** | Ultra-wideband resources |

