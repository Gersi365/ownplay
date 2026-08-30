# OwnPlay device targets

OwnPlay ships two Android APK targets from one shared codebase.

## Targets

### Mobile
- Product flavor: `mobile`
- Devices: smartphones and tablets
- Primary input: touchscreen
- First-run choices: Smartphone or Tablet only
- Smartphone may use Portrait or Landscape; Tablet is Landscape
- Launcher: standard Android launcher

### TV
- Product flavor: `tv`
- Devices: Android TV and Android TV boxes
- Primary input: remote / D-pad
- No device-type chooser at startup; Android TV and TV Box share the TV presentation contract
- Orientation: Landscape
- Launchers: Leanback plus standard launcher for compatible generic TV boxes

## Shared core

The following remain shared in `src/main` and must not fork by target unless a concrete platform constraint requires it:
- playback engine and playback state
- Live/VOD/Series repositories and resolver logic
- EPG
- downloads/offline storage and playback
- Room persistence and migrations
- source/playlist handling and playlist refresh
- favorites, progress and playback provenance
- network/data/domain models

Target source sets own presentation/input entry policy. The shared core must not infer TV vs Mobile from runtime device detection.

## Cross-device sync scope

Cross-device Device Sync is deferred and is not part of the current Mobile or TV product scope.

- Each installation keeps playlist/source state and personalization local.
- There is no current pairing, device registration, cloud account, automatic cross-device replication or secure source-transfer workflow exposed by the product.
- `SourceSyncState` remains because it represents playlist refresh/import state on the current installation; it is not Device Sync.
- Backup/Restore remains the explicit portability mechanism.
- Database v6 sync tables and protocol primitives remain as dormant compatibility/history structures so update-compatible installs do not require a destructive database downgrade.
- Local product mutations must not create or advance Device Sync metadata while the feature is deferred.

Reintroducing cross-device sync requires a new explicit product-scope decision and a fresh security/UX validation pass.

## Change rule

Every consumer-facing change must be reviewed against both targets.

- Conceptual/product changes apply to both APKs with target-appropriate presentation.
- Touch gestures and touch-only layout behavior belong to Mobile.
- D-pad focus, remote key behavior and TV-only layout behavior belong to TV.
- Shared playback/data behavior is implemented once in the shared core.

A change is not complete merely because one target compiles when the same product behavior is relevant to both. CI validates Mobile and TV variants explicitly.

## Packaging continuity

Both flavors use the shared application ID `app.ownplay.player`, shared versionCode/versionName, and the same signing identity. They are alternative target APKs and are not intended to coexist on one Android device. Version codes advance together so an existing correctly signed installation can be updated without uninstalling.
