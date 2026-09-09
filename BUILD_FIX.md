# ArzNotif v3 build fix

This package is based on ArzNotif-GitHub-APK-v2.

Fixes included:
- Fixed a Kotlin compilation issue in `MainActivity.kt` caused by the local `items` variable shadowing Compose's `LazyColumn.items` extension.
- Fixed the missing `AlertStore` import in `BootReceiver.kt`.
- Added a dedicated `:app:compileDebugKotlin` step to GitHub Actions so compiler diagnostics appear directly in the Actions log before APK assembly.
- Bumped app version to 3.0.0 / versionCode 3.

The GitHub workflow still produces an automatically signed, installable debug APK named `ArzNotif.apk`.
