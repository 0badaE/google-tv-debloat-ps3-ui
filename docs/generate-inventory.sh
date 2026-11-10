#!/usr/bin/env bash
# Regenerates docs/app-inventory.md from packages.csv. Run from anywhere.
cd "$(dirname "$0")/.." || exit 1
{
cat <<'EOF'
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

EOF
tail -n +2 packages.csv | tr -d '\r' | awk -F, '
  { if (!($2 in seen)) { seen[$2]=1; order[++n]=$2 } rows[$2]=rows[$2] "| `" $1 "` | **" $3 "** | " $4 " |\n"; cnt[$2]++ }
  END { for (i=1;i<=n;i++) { g=order[i]; printf "## %s (%d)\n\n| Package | Verdict | What it does |\n|---|---|---|\n%s\n", g, cnt[g], rows[g] } }'
} > docs/app-inventory.md
