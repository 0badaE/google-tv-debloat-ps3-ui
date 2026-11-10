# Shared helpers for debloat.sh and restore.sh. Source, don't run directly.

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKUP_DIR="$ROOT/backups"
LOG_FILE="$BACKUP_DIR/changes.log"
SERIAL=""

if [ -x "$ROOT/tools/platform-tools/adb" ]; then ADB="$ROOT/tools/platform-tools/adb"
elif [ -x "$ROOT/tools/platform-tools/adb.exe" ]; then ADB="$ROOT/tools/platform-tools/adb.exe"
elif command -v adb >/dev/null 2>&1; then ADB="$(command -v adb)"
else echo "adb not found. Put Android platform-tools in ./tools/platform-tools or on your PATH (see README)." >&2; exit 1
fi

# Runs adb against the selected device, with Windows line endings stripped.
adb_s() { "$ADB" ${SERIAL:+-s "$SERIAL"} "$@" 2>&1 | tr -d '\r'; }

connect_tv() {
  if [ -n "$1" ]; then
    local res; res="$("$ADB" connect "$1" 2>&1)"
    case "$res" in
      *"connected to"*) SERIAL="$1" ;;
      *) echo "Could not connect to $1: $res" >&2
         echo "Pair first: adb pair <ip>:<pairing-port> <code> (see README)." >&2; exit 1 ;;
    esac
  else
    local devs; devs="$("$ADB" devices | tr -d '\r' | awk -F'\t' 'NR>1 && $2=="device" {print $1}')"
    if [ "$(printf '%s\n' "$devs" | grep -c .)" -ne 1 ]; then
      echo "Expected exactly one connected device. Pass -d <ip:port>." >&2; exit 1
    fi
    SERIAL="$devs"
  fi
  echo "Connected: $SERIAL ($(adb_s shell getprop ro.product.model), build $(adb_s shell getprop ro.build.id))"
}

# Prints package names, one per line. Pass pm flags such as -u or -d.
package_set() { adb_s shell pm list packages "$@" | sed -n 's/^package://p' | sort; }

load_state() {
  ALL="$(package_set -u)"
  INSTALLED="$(package_set)"
  DISABLED="$(package_set -d)"
}

in_set() { printf '%s\n' "$2" | grep -qxF "$1"; }

# Snapshot of every package and its state, so any run can be compared or undone by hand.
save_state() {
  mkdir -p "$BACKUP_DIR"
  load_state
  local file; file="$BACKUP_DIR/state-$(date +%Y%m%d-%H%M%S)-$1.txt"
  for p in $ALL; do
    if ! in_set "$p" "$INSTALLED"; then s=removed-for-user
    elif in_set "$p" "$DISABLED"; then s=disabled
    else s=enabled; fi
    printf '%s\t%s\n' "$p" "$s"
  done > "$file"
  echo "Saved package state to $file"
}

log_change() {
  mkdir -p "$BACKUP_DIR"
  printf '%s\t%s\t%s\t%s\t%s\n' "$(date +%Y-%m-%dT%H:%M:%S)" "$SERIAL" "$1" "$3" "$2" >> "$LOG_FILE"
}

read_list() {
  [ -f "$1" ] || { echo "List not found: $1" >&2; exit 1; }
  sed -e 's/#.*$//' -e 's/[[:space:]]*$//' -e 's/^[[:space:]]*//' "$1" | tr -d '\r' | grep -v '^$'
}
