# Releasing T-UI (GitHub Releases, APK only)

T-UI is distributed only as signed APKs on GitHub Releases (no app stores). Each release has two APKs with the same
package id and key: `t-ui-<ver>-lean.apk` (Android 5+, built-in map) and `t-ui-<ver>-maplibre.apk` (Android 6+,
live / offline maps), plus `SHA256SUMS` and `signing-certs.txt`.

## 1. Release key (once, by the maintainer, offline)
    keytool -genkeypair -keystore t-ui-release.jks -alias t-ui -keyalg RSA -keysize 4096 -validity 10000 \
      -dname "CN=T-UI release"
- Use a long random store password (password manager). Never commit the keystore, never reuse another app's key.
- **Back it up**: two offline copies (e.g. encrypted USB + password manager attachment) plus the password. If the key is
  lost, users cannot update in place: they would have to uninstall (losing settings and offline maps) and reinstall.
- Publish the certificate fingerprint in the README once:
  `keytool -list -v -keystore t-ui-release.jks -alias t-ui | grep SHA256` (or `apksigner verify --print-certs` on an APK).
- Key rotation later is possible with APK Signature Scheme v3 lineage (`apksigner rotate`), but plan as if the key is permanent.

## 2. GitHub secrets (repository Settings -> Secrets and variables -> Actions)
- `TUI_RELEASE_KEYSTORE_B64` = `base64 -w0 t-ui-release.jks`
- `TUI_KEYSTORE_PASS`, `TUI_KEY_ALIAS` (`t-ui`), `TUI_KEY_PASS` (optional, defaults to the store password)
Secrets are only readable by workflows of this repository; pull requests from forks do not get them.

## 3. Cut a release
1. Update `Brand.java` `VERSION_NAME` and increase `VERSION_CODE` (updates need a higher code).
2. Move the `[Unreleased]` notes in `CHANGELOG.md` to `## [<version>] - <date>`.
3. Commit, then `git tag v<version> && git push origin v<version>`.
4. The `release` workflow runs tests and privacy checks, builds and signs both APKs, verifies the signatures, writes
   `SHA256SUMS`, and creates a **draft** release. Check it, then publish it by hand.

## 4. Local signed build (optional)
    TUI_RELEASE=1 TUI_RELEASE_KEYSTORE=/path/t-ui-release.jks TUI_KEY_ALIAS=t-ui TUI_KEYSTORE_PASS=... tools/build.sh
    TUI_RELEASE=1 ... tools/gradle-build.sh
Passwords are passed through the environment (`apksigner --ks-pass env:...`), never on the command line.

## 5. Updates for users
No in-app updater yet: users check the Releases page and install the newer APK over the old one (same key, higher
version code; settings and offline maps are kept). A "new version available" notice is a later item.
