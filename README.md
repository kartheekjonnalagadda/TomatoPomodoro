# Tomato Pomodoro

Android app for a 5–55 minute tomato timer. Sessions step by 5 minutes. The tomato is the dial, the timeline, and the home-screen widget.

## What it does

- Drag or tap the ring around the tomato to set 5, 10, 15, 20, 25, 30, 35, 40, 45, 50, or 55 minutes.
- Each step plays a short tick and a light haptic.
- While a session runs, the ring drains and the active tick pulses yellow. Segment lines on the tomato fade as time passes.
- A remote on the main screen starts, pauses, resets, and nudges by 5 minutes.
- The timer keeps running in a foreground service. Finishing uses `AlarmManager.setAlarmClock`, so it shows up as a system alarm and still fires if the app is in the background.
- The only setting is the alarm sound. It opens the system alarm ringtone picker.
- Home-screen widget: tap the tomato to start or pause. While running the widget turns deep red and the tomato goes bright red. Plus and minus step the duration.

## Open and run

1. Install Android Studio (Ladybug or newer) with SDK 35.
2. Open this folder: `TomatoPomodoro`.
3. Let Gradle sync. If the wrapper jar is missing, use Android Studio’s Gradle sync or run `gradle wrapper` once.
4. Run on a phone or emulator (API 26+).
5. Allow notifications. On Android 12+, tap **Allow system alarms** if it appears, so the finish chime is an exact alarm.
6. Long-press the home screen, add the **Tomato timer** widget.

Package: `com.tomatopomodoro.app`

## Download page

The web project is in `web/`. Open `web/index.html` and tap **Download APK**. The button saves `web/tomato-pomodoro.apk`.

Published page: https://kartheekjonnalagadda.github.io/TomatoPomodoro/

