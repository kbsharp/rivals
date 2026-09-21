# Rivals: Delivery Plan

This file breaks the milestones in `CLAUDE.md` into phases. Each phase has a goal, the tasks, anything only Kevin can do (🧑), and exit criteria. A phase is done when every exit criterion passes. When it is, tick the box here and the matching milestone in `CLAUDE.md`. After each green build (tests and lint passing), install on the phone and commit (see Conventions in `CLAUDE.md`).

Pinned toolchain (current stable as of 2026-09-21): Gradle 9.7.1, AGP 9.4.1 (built-in Kotlin), Kotlin 2.4.20, JDK 21, Compose BoM 2026.09.00, Firebase BoM 34.19.0, `compileSdk` 37.2 (current AndroidX needs it), `targetSdk` 36, `minSdk` 26. Emulator AVD `pool36` (API 36, x86_64).

---

## Phase 0: Environment and repo ✅
- [x] Local git repo, `CLAUDE.md` and this plan
- [x] JDK 21 (mise) and Android SDK (`~/Android/Sdk`: platform 36, build-tools, platform-tools)
- [ ] 🧑 Optional: create a GitHub remote (`gh repo create pool-score-tracker --private --source . --push`)
- [x] Optional: an emulator (`emulator` plus a system image) or a phone with USB debugging

## Phase 1: Scaffold (Milestone 1) ✅
**Goal:** an empty Compose + M3 app that builds and runs.
- [x] Gradle wrapper, `settings.gradle.kts`, root and app `build.gradle.kts`
- [x] `gradle/libs.versions.toml` holding every version listed above
- [x] Package layout: `ui/`, `data/`, `model/`, `auth/`
- [x] `RivalsApp : Application` holding `AppContainer` (manual DI)
- [x] `MainActivity` with an M3 theme and a `NavHost` with placeholder routes (SignIn, Home, Session, History, Stats)
- [x] `.gitignore` covering `local.properties`, `keystore.properties`, `*.jks`, `*.keystore`, `build/`, `.gradle/`, `google-services.json`*
- [x] A starter unit test so `./gradlew test` has something to run

**Exit:** `./gradlew assembleDebug test lint` pass, and the app launches on an emulator or device.

\* Decision: `google-services.json` isn't a secret, but because this is a private two-person app it stays gitignored by default. Revisit if CI is ever added.

## Phase 2: Pure domain logic (runs alongside Phase 1, with no Firebase) ✅
Front-loaded because `CLAUDE.md` wants the tally, undo and match-end logic tested as pure Kotlin.
- [x] `model/`: `Player`, `Session`, `Match`, `Frame`, `GameType`, `Status`
- [x] `domain/ScoreRules` (pure functions):
  - `recordFrame(match, winnerId)`: new tallies, whether the match ended, the match winner
  - `undoLastFrame(match, lastFrame)`: reverse tallies, and reopen the match if needed
  - race-to handling (`null` means open-ended), and auto-starting the next match with the same settings
  - the session tally update when a match ends
- [x] Output a "write plan" (a list of doc mutations) that the repository turns into one Firestore batch
- [x] Thorough JUnit tests: normal wins, a race hit on the last frame, undo across a match boundary, open-ended matches

**Exit:** `./gradlew test` is green, with every rule covered.
_Done 2026-09-22: 27 tests in `ScoreRulesTest`, run against `FakeStore` (an in-memory batch applier) so whole nights can be played and undone. Decisions made along the way:_
- _Matches carry a `number` (1, 2, 3…) so "latest match" doesn't depend on server timestamps, which are null while offline._
- _Tallies are written as increments, so two phones recording at once merge rather than overwrite._
- _Ending a match by hand: an open-ended match goes to the leader (no winner if level); a race abandoned early has no winner. Only a match with a winner counts in the session tally._
- _Undo takes back the session's last frame. If the current match is the empty automatic follow-on, it's deleted and the previous match is reopened with its tally reversed. Undo also reopens a match ended by hand._
- _Ending a session deletes an empty follow-on match and ends a match in progress by hand._

## Phase 3: Auth (Milestone 2) ✅
- [x] 🧑 Create the Firebase project, add an Android app with the agreed `applicationId`, enable the Google provider, and put `google-services.json` in `app/`
- [x] 🧑 Register the debug keystore's SHA-1 (I'll print it: `./gradlew signingReport`)
- [x] 🧑 Confirm the `applicationId`: `com.kevinbevan.rivals`
- [x] Add the Firebase BoM, Auth, Firestore, and the google-services plugin
- [x] `auth/AuthRepository`: Credential Manager `GetGoogleIdOption` → `GoogleAuthProvider.getCredential` → `signInWithCredential`; expose `authState: Flow<FirebaseUser?>`
- [x] Upsert `players/{uid}` on sign-in, and sign-out (Firebase plus `clearCredentialState`)
- [x] Sign-in screen and ViewModel; route to Home or SignIn from the auth state

**Exit:** sign in and sign out work on a device, and `players/{uid}` shows up in the console.

## Phase 4: Security rules (Milestone 3) ✅
- [x] 🧑 Supply both Gmail addresses for the allow-list: `iambevan@gmail.com` and `kbevan.dev@gmail.com` (the second stands in for the friend's account for now)
- [x] Add `firestore.rules`, `firebase.json` and `.firebaserc` to the repo
- [ ] Rules unit tests with the Firestore emulator (deferred; the rules are a single allow-list)
- [x] 🧑 Log in with `firebase login` (the Firebase CLI), then run `firebase deploy --only firestore:rules`
- [x] App: catch `PERMISSION_DENIED`, show "This account isn't allowed", then sign out

**Exit:** a third Google account sees the message and is signed out, and both allowed accounts work.
_Verified 2026-09-22 on a Pixel 10a: `iambevan@` signs in and `players/{uid}` is created, and sign-out works. Rejecting an account that isn't on the list is written but untested._

## Phase 5: Session flow (Milestone 4), the core of the app
- [x] `data/SessionRepository`: the active-session Flow, and start/end session. One active session is enforced by a check rather than a transaction (see below)
- [x] Matches and frames Flows (folded into `SessionRepository` rather than a separate `MatchRepository`)
- [x] Record and undo a frame as one `WriteBatch` built from the Phase 2 write plan, using `FieldValue.increment`
- [x] Match end: at race-to, end the match, bump the session tally and create the next match, all in the same batch
- [x] Home screen: all-time head-to-head (sum of `matchWins` across sessions) and a Resume or Start button
- [x] Session screen: two large player tap targets in the bottom half (one-handed), the score, race-to, game type, undo, end match and end session (with a confirm dialog)
- [x] Pending-write indicator using `SnapshotMetadata.hasPendingWrites`
- [x] Optional: haptic feedback on each frame tap
- [x] 🧑 Sign in once with the second account (`kbevan.dev@`) so it has a `players` doc; Start stays disabled until the rival exists
- [x] Verify airplane-mode play and sync on reconnect (2026-09-22, Pixel 10a)
- [ ] Verify live updates on a second device (the `pool36` emulator signed in as the other account stands in for the second phone)

**Exit:** a full night can be played in airplane mode, syncs on reconnect, and shows up live on the second phone.
_Code done 2026-09-22 (29 unit tests, lint clean); device verification outstanding. Decisions:_
- _No transaction for "one active session": transactions need the network, and a session must be startable with no signal. Start checks for an active session and joins it if there is one; if two phones both start one offline, everyone reads the oldest as the live one._
- _Commits aren't awaited (they only complete on server ack). Firestore applies them to the local cache at once; actions are serialised with a mutex and read state cache-first, so fast double taps plan against up-to-date state._
- _Added `ScoreRules.changeSettings` to switch game type or race on a match with no frames yet ("Change game"). "End match" only appears once a frame is played; after ending by hand the screen offers a next-match setup._
- _Doc-to-model mappers live in `data/FirestoreMappers.kt` as pure functions; `FakeStore` now uses them, so the tests cover them too._
- _Listener PERMISSION_DENIED signs out and the sign-in screen shows "this account isn't allowed"._

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
1. The friend's Gmail address: swap it for `kbevan.dev@gmail.com` in `firestore.rules`, then redeploy
2. A GitHub remote: private repo, yes or no?
