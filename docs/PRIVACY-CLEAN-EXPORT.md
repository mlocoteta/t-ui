# Privacy and clean export

This tree is meant to become public one day. It starts clean and must stay clean. Run
`tools/clean-export-check.sh` before every commit and before anything leaves this machine; it must print PASS.
The public repo will be created from a fresh export of this tree, never from another project's history.

## Never copy into this tree
- Signing keystores, key passwords, release keys, server signing keys (the debug key lives outside the tree).
- API keys and tokens of any kind (Mapbox, Google, relay tokens, Galaxy session cookies, phone link keys).
- Wi-Fi SSIDs and passphrases, hotspot credentials, Bluetooth addresses, device serials, dongle ids, VINs.
- Private endpoints: relay or tile-server URLs, personal domains, home-network or VPN (e.g. Tailscale 100.x) addresses.
- Real drives: replays, rlogs, black-box bundles, routes, GPS coordinates, screenshots showing addresses, contacts,
  caller names or calendar entries. Test data is synthetic (`tools/gen_synthetic_replay.py`) or fixtures written
  by hand with made-up values.
- Personal names, usernames, e-mail addresses, home or work locations.
- Vendor or decompiled code, firmware, AIDL files, car-maker artwork or logos.
- Car-maker or other trademarked product names in the app name, package id, icon or release text.

## What the check looks for (tools/clean-export-check.sh)
Private markers (owner names / handles, private hostnames, the home SSID names), 32+ hex-char strings that look
like keys, PEM / keystore files, `password=`-style assignments, Tailscale-range addresses, private LAN addresses
other than the documented comma hotspot default, latitude/longitude pairs, bundled `.jks` / `.keystore` / `.p12`,
recorded-drive file names, files over 512 KB, and symlinks (the tree must be self-contained).
The marker list itself is kept outside the tree (`$TUI_PRIVATE_MARKERS`, one regex per line) so the public repo does
not publish the very strings it guards against; a few generic patterns are built in.

## App behaviour
The app collects nothing: no analytics, no crash upload, no account. Logs stay on the device.
