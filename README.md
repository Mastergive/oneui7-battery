# One UI 7 Battery Pill  (Vector / LSPosed module for SM-A136B, One UI 6.1 Core)

Hooks `com.android.systemui.battery.BatteryMeterView` (class + field names confirmed from your
firmware A136BXXSDDYK2) and replaces the icon with a One UI 7 style pill:
percentage INSIDE the pill, fill by level, green + bolt while charging, yellow in power saving,
red at <=15%. If anything fails, SystemUI keeps the stock icon (all hooks are try/catch-wrapped).

## Build (no Android Studio needed)
1. Create a free GitHub account + a new empty repository.
2. Upload ALL files of this folder to it (keep the folder structure, incl. `.github/workflows/build.yml`).
3. Open the repo -> "Actions" tab -> "Build APK" -> wait ~3-5 min.
4. Open the finished run -> Artifacts -> download "OneUI7Pill-apk" -> unzip -> `app-debug.apk`.
(If the build fails, send me the red error text from the log.)

## Install
1. Install `app-debug.apk`.
2. Open the Vector manager -> Modules -> enable "One UI 7 Battery Pill" -> scope must show **System UI**.
3. Restart SystemUI (or reboot).
4. Vector manager -> Logs: look for `[OneUI7Pill] hooked N methods on BatteryMeterView`.

## If something looks wrong / to undo
Vector manager -> disable the module -> restart SystemUI. Nothing on /system is modified.

## Tweaks
`PillDrawable.java` top: WIDTH_DP / HEIGHT_DP / colours / LOW_LEVEL.
