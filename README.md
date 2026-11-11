# Ride YTD

Android app (Kotlin, Jetpack Compose) that shows your **year-to-date Garmin cycling distance against
the previous year, or any earlier year with data**, plus graphs, averages, projections, a form score
and a home-screen widget. English and German; the app follows the phone's language, or the per-app
language setting on Android 13+.

## What's on the dashboard

| Section | What it shows |
|---|---|
| **Compare with** | Year picker (last year by default). Every comparison on the dashboard and in the widget follows it |
| **Hero** | YTD distance vs the same calendar date of the comparison year, difference in distance and %, and how many days ahead or behind last year's curve you are |
| **Cumulative chart** | This year vs the comparison year, day by day, with a dashed projection to Dec 31 and every other year as faint lines. Drag to scrub any date |
| **Every year** | All years ranked by distance at today's date, with full-year totals; where this year and its projection rank. Tap a year to compare against it |
| **Form score (0–100)** | Volume (40 pts, YTD vs last year; 125 % = full marks) + Consistency (30 pts, share of weeks with a ride) + Momentum (30 pts, last 28 days vs the same 28 days last year) |
| **Projection** | *Seasonal* year-end forecast: last year's remaining distance × this year's ratio. It accounts for winter dips, unlike a straight-line pace. Shows when you'll pass last year's total, or the km/week you'd need |
| **Month by month** | Grouped bars per month, tap for exact values |
| **By the numbers** | Rides, avg/ride, avg speed, climbing, saddle time, longest ride, km/week, active days, each vs the same period last year |
| **Streaks & records** | Current and all-time longest weekly streak, best week, active weeks, last 4 weeks vs last year |
| **Year heatmap** | GitHub-style calendar of every ride day |
| **When you ride** | Distance per weekday |
| **Put in perspective** | Around-the-world %, Tour de France ×, Everests climbed, pizzas burned |

Filters: All / Outdoor / Indoor (indoor = `indoor_cycling`, `virtual_ride`, …). Units: km / mi.

**Widget** (“Year-to-date chart”, 4×3, resizable): YTD, difference and the cumulative chart, drawn as a
bitmap at the widget's exact size. A WorkManager job refreshes the current year every 6 hours while
online and while a widget is placed; the app also redraws it after every sync or settings change.

## Garmin connection: how it works and why

Garmin has **no public API for individual users**. The official Connect developer program is for
approved businesses. This app uses the same login the Garmin Connect mobile app uses, following the
open-source [python-garminconnect](https://github.com/cyberjunky/python-garminconnect) 0.3.x. That
library adopted this flow after Garmin retired the old OAuth1 login in March 2026, which broke `garth`.

1. **Login in a WebView** on Garmin's own mobile sign-in page
   (`sso.garmin.com/mobile/sso/en_US/sign-in`, or `de_DE` for German phones). You type your password and MFA code into Garmin's page;
   the app never sees them. A real WebView also uses Chromium's TLS stack, which Garmin's Cloudflare
   bot protection accepts. Hand-rolled HTTP logins get 429/427 responses.
2. The app captures the CAS **service ticket** (`ST-…`) on three independent channels: the redirect URL,
   a hook on the login page's JSON response (`serviceTicketId`), and the final page's HTML. The first
   one to fire wins. See `auth/GarminLoginWebView.kt`.
3. The ticket is exchanged at `diauth.garmin.com/di-oauth2-service/oauth/token` for a **DI OAuth2 token**
   (access token about 25 h, refresh token about 30 days, rotated on every refresh).
4. The token is stored **encrypted with an Android Keystore AES-GCM key** and never leaves the device.
   Backups are disabled for this reason.
5. Activities come from `connectapi.garmin.com/activitylist-service/activities/search/activities`. The app
   fetches all types and filters cycling locally, so gravel/MTB/virtual/e-bike are never missed. The
   first sync downloads the whole history (since 2000); after that a refresh only re-downloads the
   current year.

If Garmin changes the flow again, **`auth/GarminConfig.kt`** holds every URL, client ID and header.
Debug builds log the login navigation (tickets redacted) under the `GarminLogin` tag and enable
`chrome://inspect` for the login WebView.

> This is an unofficial client of a private Garmin API. It can break whenever Garmin changes things.
> Don't hammer refresh: Garmin answers repeated logins with 429/427 blocks.

## Building

Toolchain (all current stable as of Nov 2026): Gradle 9.9.1 · AGP 9.5.1 (built-in Kotlin) · Kotlin 2.4.21 ·
Compose BOM 2026.09.00 · Glance 1.2.0 · WorkManager 2.12.0 · compileSdk/targetSdk 37 · minSdk 26 · runs on JDK 27.

```sh
./gradlew :app:testDebugUnitTest   # stats engine tests (JUnit 6)
./gradlew :app:assembleDebug       # app/build/outputs/apk/debug/app-debug.apk ("Ride YTD (debug)", app.garminytd.debug)
./gradlew :app:assembleRelease     # app/build/outputs/apk/release/app-release.apk (minified, signed)
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Release signing

The release key is kept **outside the repository**. `app/build.gradle.kts` reads it from
`~/.gradle/gradle.properties`:

```properties
rideYtd.storeFile=/home/you/.android/garmin-ytd-release.jks
rideYtd.storePassword=…
rideYtd.keyAlias=rideytd
rideYtd.keyPassword=…
```

Back up the `.jks` file and its password together. Android only installs an update when it is signed
with the same key as the installed app. Without these properties, release builds fall back to the debug
key and print a warning.

### CI and releases

`.github/workflows/build.yml` runs on every PR and every push to `main`. It runs the unit tests, lint, and a
**release build signed with the real key**. The key comes from the repository secrets
`RIDEYTD_KEYSTORE_BASE64`, `RIDEYTD_STORE_PASSWORD`, `RIDEYTD_KEY_ALIAS` and `RIDEYTD_KEY_PASSWORD`. The
APK is attached to the run as the `apk` artifact.

To publish a release, push a semver tag:

```sh
git switch main && git pull
git tag -a v1.1.0 -m "Ride YTD 1.1.0"
git push origin v1.1.0
```

The tag sets the version: `v1.1.0` becomes versionName `1.1.0` and versionCode `10100`, i.e.
major×10000 + minor×100 + patch. Every release therefore installs as an update over the previous one.
The workflow builds and verifies the signed APK, then creates a GitHub Release with auto-generated notes
and `RideYTD-1.1.0.apk` attached. Tag builds fail rather than publish if the signing secrets are missing.

The build runs on JDK 27, set in `gradle/gradle-daemon-jvm.properties`. Gradle finds an installed JDK 27
by itself, or downloads one through the foojay resolver. `local.properties` (not committed) needs
`sdk.dir=…/Android/Sdk`, or set `ANDROID_HOME`.

Debug builds have a **"preview with sample data"** button on the welcome screen, so the UI can be
explored without a Garmin account. Release builds always require a Garmin connection.

## Project layout

```
app/src/main/java/app/garminytd/
├── auth/    GarminConfig (endpoints), GarminLoginWebView (ticket capture), GarminAuth (token exchange/refresh), TokenStore (Keystore)
├── data/    GarminApi (connectapi client), RideRepository (disk cache + incremental sync), Ride
├── stats/   YtdStats (all calculations, pure Kotlin), DemoData
├── ui/      MainViewModel, ConnectScreens, DashboardScreen, Format (units, locale-aware dates), charts/ (Canvas-drawn), theme/
└── widget/  RideWidget (Glance), ChartBitmap (chart → bitmap), WidgetSyncWorker (background refresh)
app/src/main/res/values{,-de}/strings.xml   all UI text; keep both files in the same order
```
