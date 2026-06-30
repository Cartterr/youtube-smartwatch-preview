# V3 Auto YouTube Watch Player Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the V2 manual share/paste handoff with automatic phone YouTube playback detection and a native watch button that opens a cropped/muted watch player at the current phone timestamp.

**Architecture:** The phone publishes current YouTube playback snapshots through Wear OS Data Layer when notification-listener access is enabled. The watch stores the latest snapshot, shows it in a native screen, and opens an in-app WebView/IFrame player when the user taps the main button. If the exact video id is not available from the phone metadata, the UI says so instead of guessing.

**Tech Stack:** Java 17, Android SDK command-line build, Wear OS Data Layer, Android MediaSession APIs, Android WebView with YouTube IFrame API, JUnit shared tests.

---

### Task 1: Shared Playback Snapshot Protocol

**Files:**
- Create: `shared/src/main/java/cl/dily/youtubepreview/shared/YouTubePlaybackSnapshot.java`
- Create: `shared/src/test/java/cl/dily/youtubepreview/shared/YouTubePlaybackSnapshotTest.java`
- Modify: `shared/src/main/java/cl/dily/youtubepreview/shared/WatchOpenMessage.java`

- [ ] **Step 1: Write failing tests**

Add tests that construct a snapshot with video id `dQw4w9WgXcQ`, title `Demo`, playing state, position `62000`, update time `1000`, and speed `1.0`; assert JSON round-trip, projected start seconds, and stable Data Layer path `/yswp/youtube-state`.

- [ ] **Step 2: Run RED**

Run: `.\gradlew.bat :shared:test`

Expected: compile failure because `YouTubePlaybackSnapshot` does not exist.

- [ ] **Step 3: Implement minimal shared snapshot**

Implement immutable fields, `toJson()`, `fromJson(String)`, `currentSeconds(long nowElapsedMs)`, `hasVideoId()`, and `toWatchUrl()`. Keep JSON simple and generated with `org.json.JSONObject`.

- [ ] **Step 4: Run GREEN**

Run: `.\gradlew.bat :shared:test`

Expected: tests pass.

### Task 2: Phone Auto Playback Publisher

**Files:**
- Modify: `phone/src/main/java/cl/dily/youtubepreview/phone/PlaybackPositionProbe.java`
- Modify: `phone/src/main/java/cl/dily/youtubepreview/phone/PlaybackNotificationListener.java`
- Modify: `phone/src/main/java/cl/dily/youtubepreview/phone/MainActivity.java`

- [ ] **Step 1: Extract snapshot from YouTube MediaSession**

Add `PlaybackPositionProbe.findYouTubeSnapshot(Context)` that reads active YouTube sessions, title, playback state, package name, position, speed, and tries to extract an exact YouTube video id from metadata/extras strings or URI-like values.

- [ ] **Step 2: Publish state automatically**

Make `PlaybackNotificationListener` register a `MediaSessionManager.OnActiveSessionsChangedListener`, poll every two seconds while connected, and send `YouTubePlaybackSnapshot` to every connected node on `/yswp/youtube-state`.

- [ ] **Step 3: Keep V2 manual debug path**

Update `MainActivity` to use `findYouTubeSnapshot` when available, while retaining the explicit ADB/share path as a fallback.

### Task 3: Watch Native State Screen and Player

**Files:**
- Modify: `wear/src/main/AndroidManifest.xml`
- Modify: `wear/src/main/java/cl/dily/youtubepreview/wear/WatchCommandListenerService.java`
- Modify: `wear/src/main/java/cl/dily/youtubepreview/wear/MainActivity.java`
- Create: `wear/src/main/java/cl/dily/youtubepreview/wear/YouTubePlayerActivity.java`

- [ ] **Step 1: Receive automatic state**

Handle `/yswp/youtube-state` messages, persist the latest snapshot JSON in shared preferences, and broadcast an in-app update.

- [ ] **Step 2: Show native watch button**

When launched normally, `MainActivity` shows latest YouTube title, current seconds, and one large `Open current video` button. If no exact video id is available, the button is disabled and the status explains that the phone has not exposed the id yet.

- [ ] **Step 3: Add cropped in-app player**

`YouTubePlayerActivity` hosts a fullscreen `WebView` with a YouTube IFrame page, muted autoplay, `playsinline=1`, `controls=0`, and CSS center-crop so the video fills the circular display even when edges are lost.

### Task 4: Build, Install, and Smoke Test

**Files:**
- Modify: `README.md`

- [ ] **Step 1: Build**

Run: `.\gradlew.bat test :phone:assembleDebug :wear:assembleDebug`

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 2: Install**

Install `phone/build/outputs/apk/debug/phone-debug.apk` on `R5CR107NLSV` and `wear/build/outputs/apk/debug/wear-debug.apk` on the Watch6 over ADB or scrcpy MCP.

- [ ] **Step 3: Test automatic path**

Enable `YSW Preview sync` notification access, play a YouTube video on the phone, launch the watch app, and verify it shows current playback. Tap `Open current video` and verify the watch player opens when a video id is available.

- [ ] **Step 4: Commit**

Commit with message `Add automatic YouTube watch player spike`.
