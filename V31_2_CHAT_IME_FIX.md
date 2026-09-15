# TOols 3.1.2 — Chat IME/Input Fix

- Chat input follows the Android software keyboard instead of behaving like a fixed bottom bar.
- Added Compose `imePadding()` and `navigationBarsPadding()` to the chat content.
- MainActivity already uses `windowSoftInputMode="adjustResize"`; Compose now consumes the IME inset correctly.
- The multiline input grows naturally up to 6 lines / 150dp and the message list keeps the remaining space.
- Added a compact rounded input container matching the PRO interface.
- No AI/agent/provider/storage functionality changed.
- Version 3.1.2 / code 33.
