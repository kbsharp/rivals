# Rivals

Android app for keeping score between friends; pool only for now. Quick games need no account (stored on the phone). Google sign-in lets you invite a rival; the rivalry's sessions, matches and frames live in Firestore and sync live between both phones. Golf etc. may follow: keep pool concepts (frames, game types, breaks) in the model and domain logic, not the app's structure, but don't build for other sports yet.

## Status

Only milestone 6 (Release) is left, on hold since 2026-09-22: it needs Kevin's decisions and console work, not code. Don't start it unless asked. PLAN.md Phase 12 is built and on the phone, awaiting Kevin's review.

## Working efficiently

Build/test time is the main cost. Plan the whole change, make every edit first (grep for all occurrences, including strings/tags in tests; script mechanical renames), then verify once.
- One Gradle command for all tasks (`./gradlew testDebugUnitTest lint assembleDebug`).
- Run only the narrowest checks the change can break:
  - text/UI/ViewModel: the Gradle line above + look at changed renders. No emulator.
  - `data/`, repositories, sync, `firestore.rules`: also `rules-test` and `scripts/emulator-tests.sh`.
- On failure, rerun only what failed (`--tests '*SessionUiTest'`), fix everything it shows in one go, then one full run.
- Never rerun a green check, or locally rerun what CI runs on push.
- Background long runs and do other work meanwhile. If a workflow stays slow, fix the workflow.
- When a chunk passes, without asking: commit, push, and install debug on the phone (`./gradlew installDebug`, or `adb -s <serial> install -r app/build/outputs/apk/debug/app-debug.apk` if the emulator is also attached; `adb` is in `platform-tools` under `sdk.dir` from `local.properties`). Revisit once on Play.

## Words

American pool, in all player-facing text: **rack** (not frame), **session** (not night/tonight), **tied** (not level/drawn), **final** (not full time), "percent", "toward". Dates via the locale (`formatDay`), never a hard-coded order. Code/Firestore keep `frame`/`night` (`frames`, `frameWins`, `NightsRecord`); renaming needs a migration, so leave them.

## Stack

Kotlin, Compose, Material 3; Gradle Kotlin DSL + version catalog (`gradle/libs.versions.toml`); `compileSdk` 37.2 (needed by current AndroidX), `targetSdk` 36 (Play minimum), `minSdk` 26; Firebase Auth (Google) + Firestore via BoM; sign-in via Credential Manager (`androidx.credentials` + `googleid`), not legacy Google Sign-In; Navigation Compose, ViewModel + StateFlow, coroutines. Use current stable dependency versions: check, don't guess.

## Architecture

- Single `app` module: `ui/` (screens + ViewModels, a package per feature), `data/` (repositories, Firestore mappers), `model/` (data classes), `auth/`.
- Repositories expose `Flow`s from snapshot listeners; ViewModels map to `StateFlow<UiState>`.
- Manual DI via `AppContainer` in `Application`; no Hilt unless it hurts.
- No Room: Firestore offline persistence covers bad signal.
- Never block UI on the network; show pending (unsynced) writes sensibly.

## Data model (Firestore)

```
players/{uid}                                 // get by uid only, never listed
  displayName, email, photoUrl, createdAt
emails/{lower-cased email}                    // find a rival by exact address
  uid
rivalries/{uidA_uidB}                         // sorted uids: one per pair
  playerIds: [uidA, uidB]                     // sorted
  status: "pending" | "active"                // pending = email invite not accepted
  invitedBy, createdAt, acceptedAt?, inviteCode?
invites/{code}                                // link: rivals-15bd9.web.app/invite/{code}
  from, fromName, createdAt                   // single use, 30 days; readable signed out
sessions/{sessionId}
  playerIds, rivalryId, status: "active" | "ended"
  startedAt, endedAt?, venue?, createdBy
  matchWins: { uidA: n, uidB: n }             // denormalised tally
sessions/{id}/matches/{matchId}               // race to N frames
  number                                      // 1-based within session
  gameType?: "9-ball" | "10-ball"             // absent = no game named
  raceTo: Int?                                // null = open-ended
  status: "active" | "ended"
  frameWins: { uidA: n, uidB: n }             // denormalised tally
  winnerId?, startedAt, endedAt?, playerIds   // playerIds copied for stats queries
sessions/{id}/matches/{id}/frames/{frameId}
  number, winnerId, breakerId? (legacy), recordedBy, recordedAt, playerIds
  events?: ["break-and-run" | "golden-break" | "three-fouls"]   // tagged later, credited to winner
```

- Guest games: same documents, stored in `guest-games.json` by `LocalSessionStore`, played through `SessionRepository`; players `guest-a`/`guest-b` with typed `names`. `GuestClaim` copies one to a rivalry in Firestore, swapping in uids.
- Recording/undoing a frame is one batched write: frame doc + `FieldValue.increment` on the match tally (+ session tally when the match finishes).
- One active session per rivalry (one guest game per phone).
- On reaching race-to, end the match and auto-start the next with the same settings.

## Security rules and indexes

`firestore.rules` (with `firebase.json`, deployed via Firebase CLI) is membership-based: any Google user can sign in; a player reads/writes only their own profile and email entry, their rivalries, and sessions (with matches/frames) they play in. Sessions can only be created in an active rivalry between exactly its two players. Profiles and emails are get-by-id, never listable. `rules-test/rules.test.js` covers this; its documents mirror what repositories write, so update them when writes change (`SyncTest` proves real writes pass).

The emulator doesn't enforce indexes. `firestore.indexes.json` holds what the app needs (Stats' collection-group queries on `matches`/`frames` by `playerIds`); add any new collection-group/compound query there and `firebase deploy --only firestore:indexes`.

Show a clear message when Firestore refuses something; never fail silently.

## Screens

1. **Home** (no account needed): invites; then rivals as one mirrored table in a panel (your wins, name + who leads, theirs, split bar), current/most recent first, three until See all; tapping opens the rivalry. Then **Play** (Quick game/Resume and Add a rival, two equal tiles) and **On this phone** (guest games, saveable to a rivalry). No rivals: a 0 – 0 scoreboard.
2. **Sign in** and **Invite**: 0 – 0 scoreboards with one button. **Add a rival**: three rows (email, share link, invite code), one open at a time.
3. **Rivalry**: win ring, all-time score, primary action (start a session), then **SESSIONS | STATS** tabs (session list; mirrored stats table).
4. **Session** (main screen), landscape full screen; each player's half is the tap target for a rack win.
   - Top line: back arrow + match/game/race left; clock centred with one-tap Undo beside it; session score and ≡ right. Foot: only the last rack's receipt, centred, unsynced cloud in a fixed slot beside it. Nothing on the centre line.
   - Actions (see `design/brief.md`, "Where actions live"): last rack's tags (break & run, golden break, won on three fouls) on its receipt, closing on choice; game, race, End match and End session in the ≡ match sheet; next match's settings and the winning rack's tags in the match-won panel.
   - Any score change is announced on both phones; messages replace the receipt, never cover a button.
   - Back (arrow or gesture) goes Home, never ends the session. Ending shows a final panel on both phones, then Home.
   - Match won: board dims, panel names the winner, with Undo.
   - Keep it clean; no new on-screen controls without a strong reason.
5. **Session detail**: mirrored scoreboard over match panels, racks as boxed digits, seven per row. All scrolls; once the scoreboard scrolls away, the top bar shows the score.

## Design

`design/brief.md` is the agreed brief (palette, type, spacing, shape, layout, motion, logo): follow it for all UI; no colour, size or radius outside it. `design/ux-plan.md`: the pre-release UX review; "UX phase N" means do that phase. `design/audit.md`: the review behind the brief. `design/ref/`: Kevin's reference screenshots. Mock-ups: <https://claude.ai/artifact/Cykp6oXE8meC1tupSfLu1c>.

Gist: near-black `#0D0B14`, white scores, cyan `#3FE3EC` you, pink `#FF5FA8` rival, amber `#FFB020` only for live/delete/errors. Diamond-rack logo. Montserrat (tabular) for numbers, Barlow for text. No dividers, no grey cards, one primary action per screen.

Tokens in `ui/theme`, only via `Rivals.colors`, `Rivals.type`, `Space`, `Shapes`, `Motion`, never literals. Build screens from `ui/components` (`Label`, `PrimaryButton`, `ListRow`, `Panel`, `RivalsTextField`, `EmptyState`/`LoadingState`/`ErrorState`, `HeadToHead`, `Pips`, `FormBar`, `WinRing`, `Tabs`, `StatRow`); extend them rather than using raw Material. Fonts bundled in `res/font` (OFL, `docs/licenses`).

UI loop: change → `./gradlew testDebugUnitTest --tests '*Screenshots*'` (seconds; PNGs in `app/build/screenshots`) → view changed PNGs → critique against brief and refs → fix. Add a render for any new state.

## Commands

- `./gradlew assembleDebug` / `installDebug` / `lint`
- `./gradlew testDebugUnitTest` (~30s; same as `test`): pure-Kotlin logic plus Robolectric + Roborazzi UI tests (`HomeUiTest`, `RivalryUiTest`, `SessionUiTest`) and screenshots (`Screenshots`, `FlowScreenshots`) from plain UI state.
- `cd rules-test && npm test`: rules tests against the Firestore emulator, seconds (`npm ci` first on fresh checkout).
- `scripts/emulator-tests.sh`: only `SyncTest` (`app/src/androidTest`) on the Android emulator against local Auth/Firestore emulators with real rules (two-phone sync, offline, reconnect). Boots/reuses headless `pool36`; leave it running. Never touches the real project. Keep screen tests out of it.
- `SyncTest.statsSeeEveryMatchAndFrameAcrossSessions` is flaky ("There's no match running": write/snapshot race). Rerun before chasing; fix if it gets frequent.
- CI (`.github/workflows/ci.yml`): all of the above as three parallel jobs on every push to `github.com/kbsharp/rivals` (private). `gh run list` / `gh run view`.
- `./gradlew installMinified`: R8-shrunk release code, debug-signed, to catch R8 issues.
- `./gradlew bundleRelease`: Play AAB (signed if `keystore.properties` exists).
- `scripts/play-graphics.sh`: renders `play/*.png` from SVGs with bundled fonts.
- `firebase deploy --only firestore:rules`

## Testing conventions

- Tally, undo and match-end logic: pure-Kotlin unit tests, no Firebase.
- Cross-phone sync: emulator test in `app/src/androidTest`. Rules allow/deny: case in `rules-test/`. Screens: Robolectric in `app/src/test`, never the emulator.

## Release

- Never commit the keystore, `keystore.properties` (gitignored; release signing reads it) or passwords.
- `applicationId` and package `com.kevinbevan.rivals` (confirmed; permanent once on Play). Bump `versionCode` every upload.
- Distribution: Play **internal testing** (two users). Add both Google accounts as testers, install via opt-in link, updates come through Play. No production release, so the 12-testers/14-days rule doesn't apply.
- Only Kevin can do (ask; don't work around): Firebase console setup (done), SHA-1 registration, upload keystore, Play Console setup and AAB upload. Before the first Play install, register the upload key's and Play App Signing key's SHA-1s (Play Console > App integrity); missing Play signing SHA-1 is the classic "sign-in works in debug, fails from Play" bug.

## Milestones

Done: 1 Scaffold, 2 Auth, 3 Rules, 4 Session flow, 5 History, 7 Stats, 8 Guests and rivalries, 9 Design pass (PLAN.md Phase 12; made History and Stats tabs on the rivalry screen). Tick milestones off here as they land.

- [ ] 6. **Release**: signing config, `bundleRelease`, first internal testing upload (or direct installs + Firebase App Distribution; see PLAN.md, Open questions).
