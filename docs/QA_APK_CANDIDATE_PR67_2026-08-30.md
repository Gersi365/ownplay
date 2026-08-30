# QA APK Candidate — PR #67 — 2026-08-30

Purpose: trigger the existing Android CI debug-APK lane for physical smartphone and Android TV / TV Box QA only.

Product source base:
`f5c96b8b12367c26fb7f19096ace8707afe3a722`

This branch adds no app/source/playback changes. The only difference from PR #67 is this documentation marker used to create a pull-request event.

Authorized scope:
- create a debug QA APK
- install/test on physical smartphone and physical Android TV / TV Box

Not authorized:
- release/final APK
- signing changes
- versioning changes
- merge
- publish/deploy/release

P1 TV regression gate:
- repeat Categories -> Channels -> Preview -> Fullscreen -> Preview multiple times
- zero app/device restart
- zero freeze
- zero black/stuck Surface
- verify Movie/Series Play-first focus and hierarchical Back behavior
