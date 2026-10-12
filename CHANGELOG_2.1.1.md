# MCODE 2.1.1

- Fixed Java compilation failure in `TextMenuScreen`: renamed the private screen-refresh helper from `clearAndInit()` to `rebuildScreenUi()` so it no longer collides with Minecraft `Screen.clearAndInit()`, a protected method.
- Kept the tab-switching and tab-close refresh behavior unchanged.
- Includes the 2.1.0 tabs, session persistence, `SetPATH`, workspace and concurrent-run features.

Build with JDK 21 using `./gradlew clean build` (Linux/macOS) or `gradlew.bat clean build` (Windows).
