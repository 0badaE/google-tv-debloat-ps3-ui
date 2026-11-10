# frameXTV for Google TV

The [frameXTV](https://framextv.cc/) website as a full-screen TV app you drive with the remote.
It's the site itself in a WebView. Nothing about the site changes except how you move around it.

## Remote

| Button | Does |
|---|---|
| D-pad | Moves the white focus ring between links, cards and buttons |
| OK | Clicks what the ring is on. On the video player, steps onto its controls |
| Left / Right on the player's seek bar | Skips back / forward 10 seconds (on the volume bar: volume) |
| Back | Closes a popup or the keyboard, leaves the player controls or fullscreen, then goes back a page. Press twice on the first page to exit |
| Hold OK | Pointer mode: an arrow you steer with the D-pad and click with OK, for anything the ring can't reach. Back or hold OK again to leave |
| Play/Pause, FF, Rewind (if your remote has them) | Control the video |

The ring starts on each page's main action (the player, Watch, Continue) and returns to the card
you came from when you go back.

Built in:
- Pop-ups and redirects to other sites are blocked.
- Ad scripts can't bounce you back through your history.
- The screen stays on while video plays.
- The page is laid out 1280px wide, so you get the desktop layout. That's `VIEWPORT_WIDTH` in
  `MainActivity`.

## Build and install

Same toolchain as the launcher (`..\tools`, see [launcher/README.md](../launcher/README.md)):

```powershell
.\framex\build.ps1
.\tools\platform-tools\adb.exe connect 192.168.1.50:5555
.\tools\platform-tools\adb.exe install -r .\framex\build\frameXTV.apk
```

It shows up in the TV's app list as **frameXTV**. Bump `-VersionCode` on rebuilds if you like;
the stable key in `signing\` lets updates install over the old version and keeps your sign-ins.

## Files

- `src/.../MainActivity.java`: the WebView, remote keys, fullscreen video, pop-up blocking
- `src/.../CursorView.java`: pointer mode
- `assets/tvnav.js`: focus ring and spatial navigation, injected into every page
