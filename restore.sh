#!/usr/bin/env bash
# Brings back apps hidden by debloat.sh (or any package you name).
#
#   ./restore.sh -d 192.168.1.50:5555                                  # everything in the debloat list
#   ./restore.sh -d 192.168.1.50:5555 -p com.google.android.katniss    # just one app (repeat -p for more)
#   options: -d <ip:port>  -l <list file>  -p <package>  -n (dry run)

set -u
. "$(cd "$(dirname "$0")" && pwd)/common.sh"

DEVICE="" LIST="$ROOT/lists/debloat-list.txt" DRY=0 ONLY=""
while getopts "d:l:p:n" o; do
  case $o in d) DEVICE="$OPTARG" ;; l) LIST="$OPTARG" ;; p) ONLY="$ONLY $OPTARG" ;; n) DRY=1 ;; *) exit 2 ;; esac
done

connect_tv "$DEVICE"
if [ -n "$ONLY" ]; then PACKAGES="$ONLY"; else PACKAGES="$(read_list "$LIST")" || exit 1; fi
echo "Restoring $(printf '%s\n' $PACKAGES | grep -c .) packages$([ $DRY = 1 ] && echo ' [dry run]')"

[ $DRY = 1 ] || save_state before-restore
load_state
restored=0 skipped=0 failed=0

for pkg in $PACKAGES; do
  if ! in_set "$pkg" "$ALL"; then echo "  skip      $pkg (not on this device)"; skipped=$((skipped+1)); continue; fi
  if in_set "$pkg" "$INSTALLED" && ! in_set "$pkg" "$DISABLED"; then echo "  skip      $pkg (already enabled)"; skipped=$((skipped+1)); continue; fi
  if [ $DRY = 1 ]; then echo "  would restore $pkg"; continue; fi

  ok=1
  if ! in_set "$pkg" "$INSTALLED"; then
    out="$(adb_s shell cmd package install-existing --user 0 "$pkg")"
    case "$out" in *"installed for user"*) ;; *) ok=0; echo "  FAILED    $pkg : $out" ;; esac
  fi
  if [ $ok = 1 ]; then
    out="$(adb_s shell pm enable --user 0 "$pkg")"
    case "$out" in *"new state: enabled"*|*"new state: default"*) ;; *) ok=0; echo "  FAILED    $pkg : $out" ;; esac
  fi
  if [ $ok = 1 ]; then echo "  restored  $pkg"; log_change restore "$pkg" enabled; restored=$((restored+1))
  else failed=$((failed+1)); fi
done

echo
echo "Done. restored=$restored skipped=$skipped failed=$failed"
