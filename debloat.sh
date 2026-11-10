#!/usr/bin/env bash
# Hides (disables) the apps in a debloat list on a Google TV over ADB. Nothing is deleted.
#
#   ./debloat.sh -d 192.168.1.50:5555 -n    # dry run
#   ./debloat.sh -d 192.168.1.50:5555
#   options: -d <ip:port>  -l <list file>  -n (dry run)

set -u
. "$(cd "$(dirname "$0")" && pwd)/common.sh"

DEVICE="" LIST="$ROOT/lists/debloat-list.txt" DRY=0
while getopts "d:l:n" o; do
  case $o in d) DEVICE="$OPTARG" ;; l) LIST="$OPTARG" ;; n) DRY=1 ;; *) exit 2 ;; esac
done

connect_tv "$DEVICE"
PACKAGES="$(read_list "$LIST")" || exit 1
echo "List: $LIST ($(printf '%s\n' "$PACKAGES" | grep -c .) packages)$([ $DRY = 1 ] && echo ' [dry run]')"

[ $DRY = 1 ] || save_state before-debloat
load_state
hidden=0 skipped=0 failed=0

for pkg in $PACKAGES; do
  if ! in_set "$pkg" "$ALL"; then echo "  skip    $pkg (not on this device)"; skipped=$((skipped+1)); continue; fi
  if ! in_set "$pkg" "$INSTALLED" || in_set "$pkg" "$DISABLED"; then echo "  skip    $pkg (already hidden)"; skipped=$((skipped+1)); continue; fi
  if [ $DRY = 1 ]; then echo "  would hide $pkg"; continue; fi

  # Prefer disable-user: the app and its data stay on the device, it just stops running and disappears.
  out="$(adb_s shell pm disable-user --user 0 "$pkg")"
  case "$out" in *"new state: disabled"*)
    echo "  hidden  $pkg (disabled)"; log_change hide "$pkg" disabled; hidden=$((hidden+1)); continue ;;
  esac

  # Some protected packages refuse disable; removing for user 0 keeps the APK on the system partition.
  out2="$(adb_s shell pm uninstall -k --user 0 "$pkg")"
  case "$out2" in *Success*)
    echo "  hidden  $pkg (removed for user, APK kept)"; log_change hide "$pkg" removed-for-user; hidden=$((hidden+1)) ;;
  *) echo "  FAILED  $pkg : $(printf '%s\n' "$out" | grep -v '^[[:space:]]*at ' | head -2 | tr '\n' ' ')| $out2"; failed=$((failed+1)) ;;
  esac
done

echo
echo "Done. hidden=$hidden skipped=$skipped failed=$failed"
[ $DRY = 1 ] || [ $hidden = 0 ] || echo "Undo any time with: ./restore.sh -d $SERIAL"
