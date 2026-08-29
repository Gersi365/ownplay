from pathlib import Path

path = Path('app/src/test/java/app/ownplay/player/ui/DownloadPlaybackBridgeTest.kt')
text = path.read_text(encoding='utf-8')
marker = '    private fun sampleDownload(downloadId: String): OfflineDownload = OfflineDownload(\n'
if text.count(marker) != 1:
    raise RuntimeError('DownloadPlaybackBridgeTest insertion marker mismatch')
method = '''    @Test\n    fun playbackCloseReturnsFocusOnlyWhileOwnerIsRegistered() {\n        val owner = Any()\n        var restoredDownloadId: String? = null\n        try {\n            DownloadPlaybackBridge.registerFocusReturn(owner) { downloadId ->\n                restoredDownloadId = downloadId\n            }\n\n            DownloadPlaybackBridge.notifyPlaybackClosed("download-1")\n            assertEquals("download-1", restoredDownloadId)\n\n            DownloadPlaybackBridge.clearFocusReturn(owner)\n            DownloadPlaybackBridge.notifyPlaybackClosed("download-2")\n            assertEquals("download-1", restoredDownloadId)\n        } finally {\n            DownloadPlaybackBridge.clearFocusReturn(owner)\n        }\n    }\n\n'''
path.write_text(text.replace(marker, method + marker, 1), encoding='utf-8')
print('Extended DownloadPlaybackBridgeTest with focus-return coverage')
