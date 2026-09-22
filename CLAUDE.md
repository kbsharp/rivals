# Rivals

Android app for tracking scores between friends, starting with pool: me and a friend. Both of us sign in with Google, and every session, match and frame is stored in Firestore and synced between our phones in real time.

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
players/{uid}
  displayName, email, photoUrl, createdAt

sessions/{sessionId}                          // one night out
  playerIds: [uidA, uidB]
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

sessions/{sessionId}/matches/{matchId}/frames/{frameId}
  number, winnerId, breakerId?, recordedBy, recordedAt
```

- Recording or undoing a frame is one batched write: the frame doc, plus a `FieldValue.increment` on the match tally. When a match finishes, the same write also updates the session tally.
- Only one session can be active at a time.
- When a match hits its race-to, end it and start the next match automatically with the same settings.

## Security rules

Only our two Google accounts can read or write anything. Keep `firestore.rules` and `firebase.json` in the repo and deploy them with the Firebase CLI.

```
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    function isMember() {
      return request.auth != null
        && request.auth.token.email_verified == true
        && request.auth.token.email in ['iambevan@gmail.com', 'julianjones56@gmail.com'];
    }
    match /{document=**} {
      allow read, write: if isMember();
    }
  }
}
```

If a Google account that isn't on the list signs in, Firestore reads fail with `PERMISSION_DENIED`. Handle that by showing a "this account isn't allowed" message and signing out. Don't fail silently.

## Screens

1. **Sign in**: a single "Sign in with Google" button.
2. **Home**: the all-time head-to-head record, and a button to resume the active session or start a new one.
3. **Session** (the main screen):
   - a big tap target per player to record a frame win
   - the current match score and race-to
   - undo last frame
   - end match and end session
   - usable one-handed while holding a cue
4. **History**: past sessions, newest first. Tapping one opens its detail with matches and frames.
5. **Stats** (later): win % overall and by game type, streaks, and break-and-win rate.

## Commands

- `./gradlew assembleDebug` and `./gradlew installDebug`
- `./gradlew test` and `./gradlew lint`
- `scripts/emulator-tests.sh` runs the instrumented tests (`app/src/androidTest`) on the Android emulator against local Firebase Auth and Firestore emulators with the real rules: two-phone live sync, offline play and reconnect, and the allow-list. Needs the `pool36` emulator running; never touches the real project
- CI: `.github/workflows/ci.yml` runs all of the above on every push to `github.com/kbsharp/rivals` (private). Check with `gh run list` / `gh run view`
- `./gradlew bundleRelease` builds the AAB for Play
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
- [ ] 6. **Release**: signing config, `bundleRelease`, and the first internal testing upload.
- [ ] 7. **Stats**: the stats screen.
