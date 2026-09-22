# Rivals: Delivery Plan

This file breaks the milestones in `CLAUDE.md` into phases. Each phase has a goal, the tasks, anything only Kevin can do (🧑), and exit criteria. A phase is done when every exit criterion passes. When it is, tick the box here and the matching milestone in `CLAUDE.md`. After each green build (tests and lint passing), install on the phone and commit (see Conventions in `CLAUDE.md`).

Pinned toolchain (current stable as of 2026-09-21): Gradle 9.7.1, AGP 9.4.1 (built-in Kotlin), Kotlin 2.4.20, JDK 21, Compose BoM 2026.09.00, Firebase BoM 34.19.0, `compileSdk` 37.2 (current AndroidX needs it), `targetSdk` 36, `minSdk` 26. Emulator AVD `pool36` (API 36, x86_64).

---

## Phase 0: Environment and repo ✅
- [x] Local git repo, `CLAUDE.md` and this plan
- [x] JDK 21 (mise) and Android SDK (`~/Android/Sdk`: platform 36, build-tools, platform-tools)
- [x] 🧑 GitHub remote: `github.com/kbsharp/rivals` (private)
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
- [x] Rules tests with the Firestore emulator (`RulesTest`, added 2026-09-22 with the Phase 5 emulator tests): allowed account, another Google account, unverified email, signed out
- [x] 🧑 Log in with `firebase login` (the Firebase CLI), then run `firebase deploy --only firestore:rules`
- [x] App: catch `PERMISSION_DENIED`, show "This account isn't allowed", then sign out

**Exit:** a third Google account sees the message and is signed out, and both allowed accounts work.
_Verified 2026-09-22 on a Pixel 10a: `iambevan@` signs in and `players/{uid}` is created, and sign-out works. Rejecting an account that isn't on the list is written but untested._

## Phase 5: Session flow (Milestone 4), the core of the app ✅
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
- [x] Verify live updates on a second device: automated instead, in `SyncTest` (`scripts/emulator-tests.sh`), which plays through two separate Firebase clients, one per account, against the local emulators

**Exit:** a full night can be played in airplane mode, syncs on reconnect, and shows up live on the second phone.
_Done 2026-09-22. Airplane mode checked by hand on the Pixel; live two-phone sync, offline play with reconnect, and joining an already-active session are covered by `SyncTest` on the Firebase emulators. Decisions:_
- _No transaction for "one active session": transactions need the network, and a session must be startable with no signal. Start checks for an active session and joins it if there is one; if two phones both start one offline, everyone reads the oldest as the live one._
- _Commits aren't awaited (they only complete on server ack). Firestore applies them to the local cache at once; actions are serialised with a mutex and read state cache-first, so fast double taps plan against up-to-date state._
- _Added `ScoreRules.changeSettings` to switch game type or race on a match with no frames yet ("Change game"). "End match" only appears once a frame is played; after ending by hand the screen offers a next-match setup._
- _Doc-to-model mappers live in `data/FirestoreMappers.kt` as pure functions; `FakeStore` now uses them, so the tests cover them too._
- _Listener PERMISSION_DENIED signs out and the sign-in screen shows "this account isn't allowed"._

## Phase 6: History (Milestone 5) ✅
- [x] Ended sessions, newest first
- [x] Session detail: its matches, and the frames inside each match
- [x] ~~🧑 Create any composite index~~ Not needed: history filters and sorts the (small) sessions listener on the client, so there's no `where` + `orderBy` query
- [x] 🧑 Browse and drill into a past session on the phone (checked 2026-09-22)

**Exit:** you can browse and drill into every past session.
_Code done 2026-09-22 (32 unit tests, lint clean). History cards show date, times, venue and the match score. Detail lists each match with its score and winner, and each frame as a numbered dot in the winner's colour (the same colours as the Session screen buttons), since both test accounts are called Kevin and initials can't tell them apart._

## Phase 6½: Pre-release polish
**Goal:** everything that's cheaper to settle before real nights get recorded, so the first Play build is the one you keep using. Items marked ⭐ are recommended; the rest are optional.

Data you can't backfill later:
- [x] ⭐ Record who broke each frame (`breakerId`), so break-and-win stats have data from night one. Proposal: a small "who broke" toggle on the Session screen that defaults to alternating, so it costs no taps in the usual case
- [x] ⭐ Venue: optional field when starting a session (already in the data model, with no UI)
- [x] 🧑 ⭐ The friend's real Gmail address, `julianjones56@gmail.com`: swapped in for `kbevan.dev@` in `firestore.rules`, the emulator tests and `CLAUDE.md` (2026-09-22). Deployed with the test-data wipe in Phase 7, so the stand-in account keeps working until then

Night-out usability:
- [x] ⭐ Keep the screen awake on the Session screen
- [x] ⭐ Ending a session with no frames played deletes it, rather than leaving a 0–0 night in History
- [x] ⭐ Lock to portrait (the Session layout is built for one-handed portrait use)
- [x] Tell the two players apart when their Google first names match (as the test accounts do): fall back to full name or email
- [x] Delete a past session from History (with a confirm), for the odd night recorded by mistake

Confidence:
- [x] ⭐ Compose UI tests for Home, Session, History and detail (16, in `app/src/androidTest/.../ui`), run by `scripts/emulator-tests.sh` and CI
- [x] ⭐ Check on the oldest supported Android: an API 26 emulator (`api26` AVD). All instrumented tests pass (`AVD=api26 scripts/emulator-tests.sh`). It found a real crash: when Credential Manager has no provider (outdated Play services), the sign-in error handler's sign-out threw again. Fixed: clearing credentials is best effort, and the user is told to update Play services
- [x] ⭐ Firebase Crashlytics, so a crash on the friend's phone reaches us with a stack trace. Collects from release builds only; the R8 mapping file uploads with `bundleRelease`
- [x] 🧑 GitHub remote: private, `github.com/kbsharp/rivals` (2026-09-22)
- [x] GitHub Actions (`.github/workflows/ci.yml`) running `test`, `lint`, `assembleDebug` and the emulator tests on every push and PR. `google-services.json` is the `GOOGLE_SERVICES_JSON` repository secret (base64)

**Exit:** every ⭐ item done or consciously dropped, and all tests green.
_2026-09-22: the data and usability items are done. The break alternates by itself from the last frame with a breaker recorded (across matches too); tapping a name overrides it until the next frame. Recent venues are offered as chips. `scripts/emulator-tests.sh` now boots a headless emulator when none is running._

## Phase 7: Release (Milestone 6)
- [ ] Before the first upload: deploy the rules with Julian's address, and (asking first) wipe the test sessions and the stand-in `kbevan.dev@` player from the live Firestore, so Julian comes up as the rival and the head-to-head starts at 0–0
- [ ] 🧑 Create the upload keystore and `keystore.properties` (gitignored)
- [x] Release `signingConfig` read from `keystore.properties` (the build still works without it, for CI), R8/minify enabled. No ProGuard rules needed so far: the app maps Firestore data by hand, so there are no model classes to keep
- [x] A `minified` build type: release code signed with the debug key, installable over the debug app (`./gradlew installMinified`)
- [ ] Smoke-test the minified build on the phone before uploading. R8 breakage only shows up in release builds. Installed 2026-09-22 and cold-starts cleanly; 🧑 a tap-through is still to do
- [x] 512×512 Play icon and 1024×500 feature graphic in `play/` (SVG sources plus PNGs). The launcher icon's 8 is now drawn as two rings rather than two dots
- [x] Drafts: `docs/privacy-policy.md` and `docs/play-console.md` (store listing text, Data safety table, content rating, target audience, account deletion)
- [ ] 🧑 Host the privacy policy at a public URL (it needs your contact email filled in), then fill in Play Console "App content" from `docs/play-console.md`
- [ ] `./gradlew bundleRelease`
- [ ] 🧑 Set up the app in Play Console, create the internal testing track, add both testers, upload the AAB
- [ ] 🧑 Register the upload-key and **Play App Signing** SHA-1s in Firebase (otherwise sign-in fails for Play installs)
- [ ] Bump `versionCode` on every upload

**Exit:** both phones install from the opt-in link and signing in works.

## Phase 8: Stats (Milestone 7) ✅
_Decision 2026-09-22: built before the first Play upload. The Play Console steps only you can do (account, app content, testers) take a while, so Stats gets built alongside them rather than holding up the release, and Julian's first install arrives complete._
- [x] Compute on the client from sessions, matches and frames: overall win %, win % by game type, current and longest streaks, and break-and-win rate (frames with `breakerId`). Matches and frames come from collection-group listeners
- [x] Pure calculator (`domain/Stats.kt`) with unit tests that play whole nights and check against a hand count, plus the Stats screen
- [x] Record `breakerId` on the Session screen (done in Phase 6½)

**Exit:** the stats match a hand count on real data.
_Built 2026-09-22 and tested against hand counts in `StatsCalculatorTest`; 🧑 compare against a real night once there is one. The players' colours (green for you, blue for your rival) were validated for colour blindness and contrast in both themes, and the app now has a full green Material colour scheme rather than falling back to the default purple. `Screenshots` renders every screen, light and dark, into `app/build/screenshots`._

## Phase 9: Scoreboard redesign
_2026-09-22._
- [x] The game screen goes landscape (either way up) with system bars hidden, and each player's half of the screen is their tap target. A small pill shows the match clock, the match and race, and tonight's score. Everything else sits behind a floating menu: tag the last frame (break & run, golden break), undo, change game or end match, end session, back to home
- [x] Dropped the "who broke" picker; Stats swaps break-and-win for a Specials table of tagged events (credited to the frame winner), and History rings tagged frames and lists them
- [ ] 🧑 Try it on the phone during a real game

## Phase 10: Guests and rivalries
Decided 2026-09-22: no forced sign-in. Guests get the scoreboard with typed names, saved on the phone and attachable to a rivalry after signing in. Signed-in players find a rival by **exact email** (no browsing the player base), or share an invite link if the rival isn't on Rivals yet. Everything played inside a rivalry is recorded to it. The email allow-list in the rules gives way to per-rivalry membership rules.
- [x] Home works signed out: Quick game with typed names, kept on the phone (`LocalSessionStore`, same `SessionRepository` and scoreboard). Finished guest games are listed and can be opened, deleted, or saved to a rivalry once signed in ("which one was you?")
- [x] Rivalries: invite by exact email (pending until accepted), or a one-off share link / invite code (active straight away). Accept, decline, cancel, remove. The rivalry screen holds what Home used to: head to head, start/resume, History, Stats, all scoped to that rival
- [x] Sessions carry `rivalryId`; matches and frames carry `playerIds` so stats' collection-group queries pass the rules
- [x] Membership rules replace the allow-list; `RulesTest` rewritten (11 cases), `SyncTest` plays inside a rivalry and covers saving a guest game and accepting a link
- [x] Invite link landing page and `assetlinks.json` (debug key) in `hosting/public`
- [ ] 🧑 Deploy: `firebase deploy --only firestore:rules,hosting`. Until then the live rules are still the allow-list (the app works for you, not for Julian or anyone new), and invite links open a 404 in the browser (typing the code in the app works)
- [ ] 🧑 Before Play: add the Play App Signing key's SHA-256 to `hosting/public/.well-known/assetlinks.json` so links open the app for Play installs
- [ ] Old live test sessions have no `rivalryId`, so they no longer show anywhere. Wipe them (see Open questions)

## Phase 11: Design foundations
_Planned 2026-09-22. The app works, but it looks like stock Material 3. This phase settles the taste once, so every later screen pass works from the same brief, references and screenshot loop. Critique of the current screens, most important first:_
1. _The screenshot loop can't see the most important screen. `Screenshots` renders Session in portrait, so the 4 spills into Julian's half and the match clock is missing. Fix the loop before judging anything._
2. _No type identity. It's default Roboto throughout, the scores aren't tabular (digits change width as they tick), and the giant score is regular weight._
3. _Colour competes. The app's primary green is the same as Kevin's player green, and the mint buttons in dark mode fight both player colours. There's no single accent._
4. _Dark mode is an inversion, not a design: generic grey cards on near-black. It's the theme a dim pub needs most._
5. _The Rivalry screen has a dead top half, three stacked full-width buttons, and names repeated in the title and under the scores._
6. _Home hierarchy: Quick game is the loudest thing even for a signed-in player whose real action is their rival. Rival cards should be the hero._
7. _Stats: every section is the same card, with "Kevin 12 … 9 Julian" repeated. Nights and Streaks are sentences, and there's no visual hook such as a form guide (last 10 results as dots)._
8. _Session detail: heavy name chips, grey cards with low contrast on the light background, and Delete styled as the top bar's primary action._
9. _Scoreboard feel: a tap gives a haptic and nothing else. There's no number roll, no winner moment when a race is won (just a snackbar), the info pill is small and low-contrast, and the menu button sits on the divide._
10. _Empty and loading states are bare spinners and one-line copy, and the launcher icon is still a placeholder._

- [ ] 🧑 2–4 reference screenshots in `design/refs/` (for example Apple Workout, a broadcast sports scoreboard, Things 3), with a line each in `design/refs/README.md` on what you like
- [ ] 🧑 Approve or edit the draft brief below, then it moves into `CLAUDE.md` as "Design brief"
- [ ] Screenshot loop: render Session in landscape, render Home, Rivalry, Invite and Add a rival states (empty, loading, error), use fixed clocks and data so renders are stable, and add `scripts/phone-shot.sh` (adb screencap of the real phone)
- [ ] A project skill (`.claude/skills/ui-pass/`) holding the brief, the refs and the loop: change → render → look → critique against brief and refs → fix
- [ ] Theme tokens from the brief: dark-first palette, a bundled display face with tabular figures, a 5-step type scale, 4/8dp spacing, shapes. One place in `ui/theme`
- [ ] Three directions for Home and Rivalry, mocked up as a shareable page first; pick one before any Compose changes

**Exit:** the brief is in `CLAUDE.md`, the refs are in the repo, and `Screenshots` shows every screen and state the way the phone does.

Draft brief (for you to edit):
- **Mood:** a night at the pool hall; a broadcast scoreboard, not a settings app. Calm until something happens.
- **Dark first:** designed for a dim pub, with a near-black base tinted towards baize green. Light mode is derived from it.
- **Colour:** the two player colours plus one accent (chalk white or amber) for actions. Everything else is neutral. No colour for decoration.
- **Numbers are the hero:** one display face with tabular figures for every score. Text stays in the system face.
- **Type scale:** five steps only (display, headline, title, body, label).
- **Spacing:** 4/8dp grid, 16dp gutters, generous gaps rather than lines.
- **Hard rules:** no dividers, no grey cards on grey backgrounds, one primary action per screen, no destructive action styled as primary.
- **Motion:** 150–250ms and purposeful: a number roll on each score change, a winner moment at match end, no bounce.
- **Feel:** a crisp haptic on every score change; nothing on the scoreboard moves unless the score does.
- **States:** every screen has a written empty state and a loading skeleton, never a bare spinner.

## Phase 12: Screen passes
_Each pass runs the loop until the screen passes the brief. The passes are separate so each change can be judged on its own._
- [ ] Scoreboard: tabular display numerals, a number roll and tap flash, a winner moment, a legible pill, the menu button moved off the divide
- [ ] Home and Rivalry in the chosen direction (rivals as hero cards, a form guide, Quick game secondary once you have a rival)
- [ ] Stats and History: a form guide, less repetition, a hierarchy of cards rather than a stack of equal ones
- [ ] Empty, loading and error states, with written copy for each
- [ ] Motion and haptics pass across the app
- [ ] A real launcher icon and Play graphics to match
- [ ] Final critique as a senior product designer, light and dark, on the real phone

**Exit:** every screen passes the brief in both themes, checked on the phone in a dim room.

---

## Open questions
- What happens to the test sessions in the live Firestore when the rivalry model lands: wipe them (Phase 7 already planned to), or migrate them into a rivalry?
None. Settled 2026-09-22: the friend is `julianjones56@gmail.com`; the repo is private at `github.com/kbsharp/rivals`; Stats goes before the first upload (see Phase 8).
