# Rivals

Android app for tracking scores between friends, starting with pool. Anyone can keep score in a quick game without an account (saved on the phone). Signing in with Google lets you invite a rival; every session, match and frame in a rivalry is stored in Firestore and synced between both phones in real time.

It's pool-only for now, but golf and other sports may follow. Keep pool-specific concepts (frames, 8-ball/9-ball, breaks) in the data model and domain logic rather than baked into the app's structure, but don't build for other sports yet.

## Status

Work through the milestones at the bottom in order, and tick each one off in this file as it lands.

As of 2026-09-22 only milestone 6 (**Release**) is left, and it's on hold: it needs Kevin's
decisions and console work, not code. Everything in PLAN.md's Phase 12 is built and on the phone;
what's open there is Kevin's own look at it. Don't start release work without being asked.

## Stack

- Kotlin, Jetpack Compose, Material 3
- Gradle (Kotlin DSL) with a version catalog (`gradle/libs.versions.toml`)
- SDK levels: `compileSdk` 37.2 (current stable AndroidX requires it), `targetSdk` 36 (the Google Play minimum for new apps and updates), `minSdk` 26
- Firebase Auth (Google provider) and Cloud Firestore, via the Firebase BoM
- Google sign-in through Credential Manager (`androidx.credentials` + `googleid`), not the deprecated legacy Google Sign-In SDK
- Navigation Compose, ViewModel + StateFlow, coroutines
- Use current stable versions of all dependencies. Check them; don't guess.

## Architecture

- Single `app` module, with these packages:
  - `ui/`: screens and ViewModels, one package per feature
  - `data/`: repositories and Firestore mappers
  - `model/`: plain data classes
  - `auth/`: sign-in
- Repositories expose `Flow`s built from Firestore snapshot listeners, and ViewModels map them to `StateFlow<UiState>`.
- Use manual constructor injection through an `AppContainer` in the `Application` class. Don't add Hilt unless it starts to hurt.
- Firestore's offline persistence (on by default on Android) covers bad signal in the pool hall, so don't add Room.
- Never block the UI on the network. Show pending (not yet synced) writes sensibly.

## Data model (Firestore)

```
players/{uid}                                 // get by uid only, never listed
  displayName, email, photoUrl, createdAt

emails/{lower-cased email}                    // find a rival by exact address
  uid

rivalries/{uidA_uidB}                         // uids in order: one rivalry per pair
  playerIds: [uidA, uidB]                     // sorted
  status: "pending" | "active"                // pending = email invite not yet accepted
  invitedBy, createdAt, acceptedAt?, inviteCode?

invites/{code}                                // share link: rivals-15bd9.web.app/invite/{code}
  from, fromName, createdAt                   // single use, 30 days; readable signed out

sessions/{sessionId}                          // one night out
  playerIds: [uidA, uidB]
  rivalryId
  status: "active" | "ended"
  startedAt, endedAt?, venue?, createdBy
  matchWins: { uidA: n, uidB: n }             // denormalised tally

sessions/{sessionId}/matches/{matchId}        // a race to N frames
  number: Int                                 // 1-based order within the session
  gameType: "9-ball" | "10-ball"              // absent when the match names no game
  raceTo: Int?                                // null = open-ended
  status: "active" | "ended"
  frameWins: { uidA: n, uidB: n }             // denormalised tally
  winnerId?, startedAt, endedAt?
  playerIds                                   // copied from the session, for stats queries

sessions/{sessionId}/matches/{matchId}/frames/{frameId}
  number, winnerId, breakerId? (legacy), recordedBy, recordedAt
  events?: ["break-and-run" | "golden-break" | "three-fouls"]   // tagged after the fact, credited to the winner
  playerIds
```

Guest (quick) games use the same documents, kept on the phone in `guest-games.json` by `LocalSessionStore` and played through the same `SessionRepository`. Their players are `guest-a` / `guest-b` with typed `names`. Saving one to a rivalry (`GuestClaim`) copies it to Firestore with the guest ids swapped for uids.

- Recording or undoing a frame is one batched write: the frame doc, plus a `FieldValue.increment` on the match tally. When a match finishes, the same write also updates the session tally.
- Only one session can be active per rivalry (and one guest game per phone).
- When a match hits its race-to, end it and start the next match automatically with the same settings.

## Security rules

`firestore.rules` (kept in the repo with `firebase.json`, deployed with the Firebase CLI) is membership-based: anyone can sign in with Google, and a player can read and write only their own profile and email index entry, their rivalries, and the sessions (with matches and frames) they play in. A session can only be created in an active rivalry between exactly its two players. Profiles and the email index can be fetched by id but never listed, so the player base can't be browsed. `rules-test/rules.test.js` covers all of this against the Firestore emulator, in Node (`npm test` in `rules-test/`, a few seconds). Its documents mirror what the repositories write, so when a repository's writes change, change them there too; `SyncTest` proves the app's real writes get through.

The emulator doesn't enforce indexes, so a query that works in tests can still be refused in production. `firestore.indexes.json` holds the ones the app needs (the Stats tab's collection-group queries over `matches` and `frames` by `playerIds`); a new collection-group or compound query gets an entry there, deployed with `firebase deploy --only firestore:indexes`.

Show a clear message when Firestore refuses something; don't fail silently.

## Screens

1. **Home** (no account needed): any invites, then every rival as one mirrored table in a panel (your wins, their name and who leads, theirs, a split bar), the one you're playing or played last first, three shown until See all. Tapping a rival opens their rivalry, where a night is started. Then **Play** (Quick game or Resume, and Add a rival, as two equal tiles) and **On this phone** (guest games that can be saved to a rivalry). With no rivals it's a 0 – 0 scoreboard.
2. **Sign in** and **Invite** are both scoreboards at 0 – 0 with one button. **Add a rival**: three rows — exact email, share link, invite code — one open at a time.
3. **Rivalry**: the win ring and all-time score, the primary action, then **SESSIONS | STATS** tabs. Sessions is the nights you've played; Stats is the mirrored table. Neither is a screen of its own any more.
4. **Session** (the main screen):
   - landscape and full screen: each player's half of the screen is the tap target for a frame win
   - a line along the top: the back arrow and the match, game and race on the left, the clock centred with a one-tap Undo beside it, tonight's score and ≡ on the right. Along the foot, only the last frame's receipt, centred, with the unsynced cloud in a fixed slot beside it. Nothing sits on the centre line
   - where actions live (UX phase 3; `design/brief.md`, "Where actions live"; built in UX phase 4):
     - the last frame: Undo beside the clock, and tagging it (break & run, golden break, won on three fouls) on its receipt; choosing a tag closes them
     - the match (game, race, End match) and End session are in the match sheet behind ≡
     - the next match's settings, and the winning frame's tags, are in the match-won panel
   - anything done to the score is named on both phones; scoreboard messages go in the receipt's place, never over a button
   - back (arrow or gesture) goes Home and never ends the night; ending it shows a full-time panel on both phones, then Home
   - when a match is won the board dims and a panel names the winner, with Undo in it
   - keep it as clean as possible; don't add on-screen controls without a strong reason
5. **Session detail**: one night, match by match, with its frames as boxed digits.

## Design

`design/brief.md` is the agreed design brief: palette, type, spacing, shape, layout rules and
motion. Follow it for every UI change; don't introduce a colour, size or radius that isn't in it.
`design/ux-plan.md` is the UX review in progress (flow and the in-game menu, before Release): when Kevin says "UX phase N", do that phase from there.
`design/audit.md` is the review the brief came from, and `design/ref/` holds Kevin's reference
screenshots. The mock-ups of the agreed direction are at
<https://claude.ai/artifact/Cykp6oXE8meC1tupSfLu1c>.

In short: near-black `#0D0B14`, white scores, cyan `#3FE3EC` for you, hot pink `#FF5FA8` for your
rival, amber `#FFB020` only for live, delete and errors. The logo is the diamond rack (brief, The logo). Montserrat for numbers (tabular figures), Barlow
for text. No dividers, no grey cards, one primary action per screen.

The tokens live in `ui/theme`: reach them through `Rivals.colors`, `Rivals.type`, `Space`,
`Shapes` and `Motion`, never with a literal. `ui/components` holds the vocabulary every screen is
built from — `Label`, `PrimaryButton`, `ListRow`, `Panel`, `RivalsTextField`, the written
`EmptyState`/`LoadingState`/`ErrorState`, and the scoreboard parts (`HeadToHead`, `Pips`,
`FormBar`, `WinRing`, `Tabs`, `StatRow`). Build a screen from those; add to them rather than
reaching for a Material component. The fonts are bundled under `res/font` (OFL, see
`docs/licenses`), so nothing is downloaded at runtime.

The loop for any UI work: change → `scripts/emulator-tests.sh` (which renders every screen and
state into `app/build/screenshots`) → look at the PNGs → critique against the brief and the refs →
fix. The scoreboard renders at landscape size, and the between-matches panel and the empty states have
their own renders; add a render whenever a pass introduces a new state.

## Commands

- `./gradlew assembleDebug` and `./gradlew installDebug`
- `./gradlew test` and `./gradlew lint`
- `cd rules-test && npm test` runs the Firestore rules tests (Node, against the Firestore emulator; `npm ci` there first on a fresh checkout). Seconds, so run it for any change to `firestore.rules`
- `scripts/emulator-tests.sh` runs the instrumented tests (`app/src/androidTest`) on the Android emulator against local Firebase Auth and Firestore emulators with the real rules: two-phone live sync, offline play and reconnect, and Compose UI tests of each screen. Boots the `pool36` emulator headless if none is running; never touches the real project. The full run takes minutes, so:
  - while iterating, run just the class you're working on: `scripts/emulator-tests.sh -Pandroid.testInstrumentationRunnerArguments.class=com.kevinbevan.rivals.ui.HomeUiTest` (comma-separate several classes; `Class#method` for one test)
  - leave the Android emulator running between runs (the script reuses one that's up rather than booting its own)
  - run the whole suite once, before committing
- CI: `.github/workflows/ci.yml` runs all of the above (three jobs in parallel: unit tests/lint/build, rules, Android emulator) on every push to `github.com/kbsharp/rivals` (private). Check with `gh run list` / `gh run view`
- `SyncTest.statsSeeEveryMatchAndFrameAcrossSessions` is flaky: it fails now and then with "There's no match running", a race between the write and the snapshot it reads back, and passes on a rerun. Rerun before chasing it; fix it properly if it starts failing often
- `./gradlew installMinified` installs the R8-shrunk release code signed with the debug key, to catch R8 problems before an upload
- `./gradlew bundleRelease` builds the AAB for Play (signed when `keystore.properties` exists)
- `scripts/play-graphics.sh` renders `play/*.png` from their SVGs with the app's bundled fonts
- `firebase deploy --only firestore:rules`

## Things only I can do

Ask me to do these; don't try to work around them.

- In the Firebase console:
  - create the Firebase project and add the Android app
  - enable the Google provider in Auth
  - download `google-services.json` into `app/`
- Register SHA-1 fingerprints in Firebase:
  - the debug keystore's, now
  - the upload key's and the Play App Signing key's (Play Console > App integrity), before the first Play install
  - A missing Play signing SHA-1 is the classic bug where sign-in works in debug but fails when installed from Play.
- Create the upload keystore, set up the app in Play Console, and upload the AAB.

## Conventions

- Never commit the keystore, `keystore.properties` or any passwords. Release signing reads from `keystore.properties`, which is gitignored.
- The `applicationId` and Kotlin package are `com.kevinbevan.rivals` (confirmed). It becomes permanent once uploaded to Play.
- Increment `versionCode` on every upload.
- Unit-test the tally, undo and match-end logic as pure Kotlin, with no Firebase.
- Anything that syncs between phones gets an emulator test in `app/src/androidTest`, rather than relying on two real devices. What the rules allow or refuse gets a case in `rules-test/`.
- When a chunk of work builds and passes `test`, `lint` and (if it touches data or rules) `rules-test` and the full `scripts/emulator-tests.sh`, always finish with all three without asking: commit, push, and install the debug build on the connected phone (`./gradlew installDebug`, or `adb -s <serial> install -r app/build/outputs/apk/debug/app-debug.apk` when the emulator is attached too). `adb` isn't on the PATH; it's under `platform-tools` in the `sdk.dir` from `local.properties`. This holds until the app is published on Play, when it gets revisited.

## Distribution

The app only has two users, so use Play's **internal testing** track:

- Add both Google accounts as testers.
- Install from the opt-in link; updates then arrive through the Play Store.
- There's no production release, so the 12-testers-for-14-days rule doesn't come into it.

## Milestones

- [x] 1. **Scaffold**: a Compose + M3 app with the version catalog and the SDK levels above, running on the emulator.
- [x] 2. **Auth**: Firebase wired up, Credential Manager Google sign-in feeding Firebase Auth, upsert `players/{uid}` on sign-in, and sign-out.
- [x] 3. **Rules**: `firestore.rules` and `firebase.json` in the repo and deployed, with unauthorised accounts handled.
- [x] 4. **Session flow**: start a session, run matches with race-to, record and undo frames, end a match or session.
- [x] 5. **History**: the session list and session detail. (Phase 12 made the list a tab on the rivalry screen; the detail is still its own screen.)
- [ ] 6. **Release**: signing config, `bundleRelease`, and the first internal testing upload (or
      direct installs plus Firebase App Distribution; see PLAN.md, Open questions).
- [x] 7. **Stats**: win % overall and by game type, streaks, tagged specials. (Phase 12 made it a tab on the rivalry screen.)
- [x] 8. **Guests and rivalries**: no forced sign-in, quick games on the phone, invites by email or
      link, membership security rules.
- [x] 9. **Design pass**: every screen rebuilt on `design/brief.md` (PLAN.md, Phase 12).
