# OwnPlay QA6 Update-Compatible Build

Purpose: build a physical-QA APK that installs as an update over the established CI330/CI335 debug-signed installation.

Source base: PR #67 exact head `f5c96b8b12367c26fb7f19096ace8707afe3a722`.

Authorized versioning change:
- applicationId remains `app.ownplay.player`
- versionCode: 5 -> 6
- versionName: `0.1.0-dev-qa5-no-local` -> `0.1.0-dev-qa6-update`
- signing configuration is unchanged
- existing canonical debug signing identity must be reused

Update compatibility baseline:
- CI330/CI335 applicationId: `app.ownplay.player`
- CI330/CI335 versionCode: 5
- canonical signing cache key: `ownplay-pr16-debug-keystore-v1`
- canonical signing certificate SHA-256: `22:5C:FC:60:70:2F:67:B7:94:80:12:59:4E:73:CD:1A:0C:E9:2A:DF:B2:41:24:75:94:A0:CF:FE:67:52:B1:69`

The app remains a single universal APK for phone/tablet and Android TV/TV Box. Two Drive handoff filenames may point to byte-identical APK binaries for device-specific operator clarity.

No merge, release, publish, deployment, production signing, architecture, database, or authentication change is authorized by this build.