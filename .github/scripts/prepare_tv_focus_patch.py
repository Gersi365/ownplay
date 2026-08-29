from pathlib import Path

path = Path('.github/scripts/apply_tv_focus_patch.py')
text = path.read_text(encoding='utf-8')
start_token = 'bridge_test_path = Path("app/src/test/java/app/ownplay/player/ui/DownloadPlaybackBridgeTest.kt")\n'
end_token = '# -----------------------------------------------------------------------------\n# Narrow Downloads playback bridge: add a return-focus signal only.\n'
start = text.find(start_token)
end = text.find(end_token, start)
if start < 0 or end < 0:
    raise RuntimeError('Bridge-test staging block not found in TV focus patch script')
path.write_text(text[:start] + text[end:], encoding='utf-8')
print('Prepared TV focus patch script for existing bridge test')
