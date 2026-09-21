# Pool Score Tracker: Delivery Plan

This file breaks the milestones in `CLAUDE.md` into phases. Each phase has a goal, the tasks, anything only Kevin can do (🧑), and exit criteria. A phase is done when every exit criterion passes. When it is, tick the box here and the matching milestone in `CLAUDE.md`.

Pinned toolchain (current stable as of 2026-09-21): Gradle 9.7.1, AGP 9.4.1 (built-in Kotlin), Kotlin 2.4.20, JDK 21, Compose BoM 2026.09.00, Firebase BoM 34.19.0, `compileSdk`/`targetSdk` 36, `minSdk` 26.

---

## Phase 0: Environment and repo ✅
- [x] Local git repo, `CLAUDE.md` and this plan
- [x] JDK 21 (mise) and Android SDK (`~/Android/Sdk`: platform 36, build-tools, platform-tools)
- [ ] 🧑 Optional: create a GitHub remote (`gh repo create pool-score-tracker --private --source . --push`)
- [ ] 🧑 Optional: an emulator (`emulator` plus a system image) or a phone with USB debugging

## Phase 1: Scaffold (Milestone 1)
**Goal:** an empty Compose + M3 app that builds and runs.
- [ ] Gradle wrapper, `settings.gradle.kts`, root and app `build.gradle.kts`
- [ ] `gradle/libs.versions.toml` holding every version listed above
- [ ] Package layout: `ui/`, `data/`, `model/`, `auth/`
- [ ] `PoolScoreApp : Application` holding `AppContainer` (manual DI)
- [ ] `MainActivity` with an M3 theme and a `NavHost` with placeholder routes (SignIn, Home, Session, History, Stats)
- [ ] `.gitignore` covering `local.properties`, `keystore.properties`, `*.jks`, `*.keystore`, `build/`, `.gradle/`, `google-services.json`*
- [ ] A starter unit test so `./gradlew test` has something to run

**Exit:** `./gradlew assembleDebug test lint` pass, and the app launches on an emulator or device.

\* Decision: `google-services.json` isn't a secret, but because this is a private two-person app it stays gitignored by default. Revisit if CI is ever added.

## Phase 2: Pure domain logic (runs alongside Phase 1, with no Firebase)
Front-loaded because `CLAUDE.md` wants the tally, undo and match-end logic tested as pure Kotlin.
- [ ] `model/`: `Player`, `Session`, `Match`, `Frame`, `GameType`, `Status`
- [ ] `domain/ScoreRules` (pure functions):
  - `recordFrame(match, winnerId)`: new tallies, whether the match ended, the match winner
  - `undoLastFrame(match, lastFrame)`: reverse tallies, and reopen the match if needed
  - race-to handling (`null` means open-ended), and auto-starting the next match with the same settings
  - the session tally update when a match ends
- [ ] Output a "write plan" (a list of doc mutations) that the repository turns into one Firestore batch
- [ ] Thorough JUnit tests: normal wins, a race hit on the last frame, undo across a match boundary, open-ended matches

**Exit:** `./gradlew test` is green, with every rule covered.

## Phase 3: Auth (Milestone 2)
- [ ] 🧑 Create the Firebase project, add an Android app with the agreed `applicationId`, enable the Google provider, and put `google-services.json` in `app/`
- [ ] 🧑 Register the debug keystore's SHA-1 (I'll print it: `./gradlew signingReport`)
- [ ] 🧑 Confirm the `applicationId` (it's permanent once uploaded to Play)
- [ ] Add the Firebase BoM, Auth, Firestore, and the google-services plugin
- [ ] `auth/AuthRepository`: Credential Manager `GetGoogleIdOption` → `GoogleAuthProvider.getCredential` → `signInWithCredential`; expose `authState: Flow<FirebaseUser?>`
- [ ] Upsert `players/{uid}` on sign-in, and sign-out (Firebase plus `clearCredentialState`)
- [ ] Sign-in screen and ViewModel; route to Home or SignIn from the auth state

**Exit:** sign in and sign out work on a device, and `players/{uid}` shows up in the console.

## Phase 4: Security rules (Milestone 3)
- [ ] 🧑 Supply both Gmail addresses for the allow-list
- [ ] Add `firestore.rules`, `firebase.json` and `.firebaserc` to the repo
- [ ] Rules unit tests with the Firestore emulator (`@firebase/rules-unit-testing`), if Node is available
- [ ] 🧑 Log in with `firebase login` (the Firebase CLI), then run `firebase deploy --only firestore:rules`
- [ ] App: catch `PERMISSION_DENIED`, show "This account isn't allowed", then sign out

**Exit:** a third Google account sees the message and is signed out, and both allowed accounts work.

## Phase 5: Session flow (Milestone 4), the core of the app
- [ ] `data/SessionRepository`: the active-session Flow (a query on `status == "active"`), and start/end session (enforce one active session in a transaction)
- [ ] `data/MatchRepository`: the current-match Flow, and a frames Flow for undo
- [ ] Record and undo a frame as one `WriteBatch` built from the Phase 2 write plan, using `FieldValue.increment`
- [ ] Match end: at race-to, end the match, bump the session tally and create the next match, all in the same batch
- [ ] Home screen: all-time head-to-head (sum of `matchWins` across sessions) and a Resume or Start button
- [ ] Session screen: two large player tap targets in the bottom half (one-handed), the score, race-to, game type, undo, end match and end session (with a confirm dialog)
- [ ] Pending-write indicator using `SnapshotMetadata.hasPendingWrites`
- [ ] Optional: haptic feedback on each frame tap

**Exit:** a full night can be played in airplane mode, syncs on reconnect, and shows up live on the second phone.

## Phase 6: History (Milestone 5)
- [ ] Ended sessions, newest first (`orderBy startedAt desc`, which may need an index)
- [ ] Session detail: its matches, and the frames inside each match
- [ ] 🧑 Create any composite index Firestore asks for (the error links straight to it), or I'll add `firestore.indexes.json`

**Exit:** you can browse and drill into every past session.

## Phase 7: Release (Milestone 6)
- [ ] 🧑 Create the upload keystore and `keystore.properties` (gitignored)
- [ ] Release `signingConfig` read from `keystore.properties`, R8/minify enabled, ProGuard rules for Firebase models
- [ ] `./gradlew bundleRelease`
- [ ] 🧑 Set up the app in Play Console, create the internal testing track, add both testers, upload the AAB
- [ ] 🧑 Register the upload-key and **Play App Signing** SHA-1s in Firebase (otherwise sign-in fails for Play installs)
- [ ] Bump `versionCode` on every upload

**Exit:** both phones install from the opt-in link and signing in works.

## Phase 8: Stats (Milestone 7)
- [ ] Compute on the client from sessions, matches and frames: overall win %, win % by game type, current and longest streaks, and break-and-win rate (frames with `breakerId`)
- [ ] Pure calculators with unit tests, plus the Stats screen
- [ ] Optional: record `breakerId` on the Session screen (for example, a toggle for who broke)

**Exit:** the stats match a hand count on real data.

---

## Open questions
1. The `applicationId`: `com.CHANGEME.poolscore` needs a real value (for example `com.kevinbevan.poolscore`)
2. Both Gmail addresses for the rules allow-list
3. A GitHub remote: private repo, yes or no?
