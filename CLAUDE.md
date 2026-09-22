# Rivals

Android app for tracking scores between friends, starting with pool. Anyone can keep score in a quick game without an account (saved on the phone). Signing in with Google lets you invite a rival; every session, match and frame in a rivalry is stored in Firestore and synced between both phones in real time.

It's pool-only for now, but golf and other sports may follow. Keep pool-specific concepts (frames, 8-ball/9-ball, breaks) in the data model and domain logic rather than baked into the app's structure, but don't build for other sports yet.

## Status

Work through the milestones at the bottom in order, and tick each one off in this file as it lands.

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
  gameType: "8-ball" | "9-ball" | "other"
  raceTo: Int?                                // null = open-ended
  status: "active" | "ended"
  frameWins: { uidA: n, uidB: n }             // denormalised tally
  winnerId?, startedAt, endedAt?
  playerIds                                   // copied from the session, for stats queries

sessions/{sessionId}/matches/{matchId}/frames/{frameId}
  number, winnerId, breakerId? (legacy), recordedBy, recordedAt
  events?: ["break-and-run" | "golden-break"]   // tagged after the fact, credited to the winner
  playerIds
```

Guest (quick) games use the same documents, kept on the phone in `guest-games.json` by `LocalSessionStore` and played through the same `SessionRepository`. Their players are `guest-a` / `guest-b` with typed `names`. Saving one to a rivalry (`GuestClaim`) copies it to Firestore with the guest ids swapped for uids.

- Recording or undoing a frame is one batched write: the frame doc, plus a `FieldValue.increment` on the match tally. When a match finishes, the same write also updates the session tally.
- Only one session can be active per rivalry (and one guest game per phone).
- When a match hits its race-to, end it and start the next match automatically with the same settings.

## Security rules

`firestore.rules` (kept in the repo with `firebase.json`, deployed with the Firebase CLI) is membership-based: anyone can sign in with Google, and a player can read and write only their own profile and email index entry, their rivalries, and the sessions (with matches and frames) they play in. A session can only be created in an active rivalry between exactly its two players. Profiles and the email index can be fetched by id but never listed, so the player base can't be browsed. `RulesTest` covers all of this against the emulator.

Show a clear message when Firestore refuses something; don't fail silently.

## Screens

1. **Home** (no account needed): Quick game (or resume it), your rivals with the head to head, invites to accept, Add a rival, and guest games on the phone that can be saved to a rivalry.
2. **Sign in**: optional, reached from Home or an invite. **Add a rival**: exact email, share link, or invite code. **Invite**: opened from a share link. **Rivalry**: one rival's head to head, start or resume a session, History, Stats.
3. **Session** (the main screen):
   - landscape and full screen: each player's half of the screen is the tap target for a frame win
   - a small pill with the match clock, the match and race-to, and tonight's score
   - everything else sits behind one floating menu: tag the last frame (break & run, golden break), undo, change game or end match, end session
   - keep it as clean as possible; don't add on-screen controls without a strong reason
4. **History**: past sessions, newest first. Tapping one opens its detail with matches and frames.
5. **Stats**: win % overall and by game type, streaks, and counts of tagged specials.

## Design

`design/brief.md` is the agreed design brief: palette, type, spacing, shape, layout rules and
motion. Follow it for every UI change; don't introduce a colour, size or radius that isn't in it.
`design/audit.md` is the review it came from, and `design/ref/` holds Kevin's reference
screenshots. The mock-ups of the agreed direction are at
<https://claude.ai/artifact/Cykp6oXE8meC1tupSfLu1c>.

In short: charcoal `#131418`, white scores, teal `#6FD3C4` for you, apricot `#F0A883` for your
rival, red `#FF4757` only for live and delete. Montserrat for numbers (tabular figures), Barlow
for text. No dividers, no grey cards, one primary action per screen.

The loop for any UI work: change → `scripts/emulator-tests.sh` (which renders every screen and
state into `app/build/screenshots`) → look at the PNGs → critique against the brief and the refs →
fix. The scoreboard renders at landscape size, and the between-matches panel and the empty states have
their own renders; add a render whenever a pass introduces a new state.

## Commands

- `./gradlew assembleDebug` and `./gradlew installDebug`
- `./gradlew test` and `./gradlew lint`
- `scripts/emulator-tests.sh` runs the instrumented tests (`app/src/androidTest`) on the Android emulator against local Firebase Auth and Firestore emulators with the real rules: two-phone live sync, offline play and reconnect, the allow-list, and Compose UI tests of each screen. Boots the `pool36` emulator headless if none is running; never touches the real project
- CI: `.github/workflows/ci.yml` runs all of the above on every push to `github.com/kbsharp/rivals` (private). Check with `gh run list` / `gh run view`
- `./gradlew installMinified` installs the R8-shrunk release code signed with the debug key, to catch R8 problems before an upload
- `./gradlew bundleRelease` builds the AAB for Play (signed when `keystore.properties` exists)
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
- Anything that syncs between phones or depends on the rules gets an emulator test in `app/src/androidTest`, rather than relying on two real devices.
- When a chunk of work builds and passes `test`, `lint` and (if it touches data or rules) `scripts/emulator-tests.sh`, always do both without asking: install the debug build on the connected phone (`./gradlew installDebug`, or `adb -s <serial> install -r` when the emulator is attached too), and commit.

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
- [x] 5. **History**: the session list and session detail.
- [ ] 6. **Release**: signing config, `bundleRelease`, and the first internal testing upload (or
      direct installs plus Firebase App Distribution; see PLAN.md, Open questions).
- [x] 7. **Stats**: the stats screen.
- [x] 8. **Guests and rivalries**: no forced sign-in, quick games on the phone, invites by email or
      link, membership security rules.
- [ ] 9. **Design pass**: every screen rebuilt on `design/brief.md` (PLAN.md, Phase 12).
