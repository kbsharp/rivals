# Play Console: app content answers

Drafts for the forms Play Console asks for before the first internal-testing release. Where a form isn't compulsory for internal testing, filling it in now still saves time later.

## App details

- **App name:** Rivals
- **Default language:** English (United Kingdom)
- **App or game:** App. **Category:** Sports
- **Free or paid:** Free
- **Short description (80 characters max):** Keep pool scores with a friend: frames, matches, nights and stats.
- **Full description:**
  > Rivals keeps score when two friends play pool. Tap the winner of each frame, and matches, nights and your all-time head-to-head update on both phones straight away, even with no signal in the pool hall.
  >
  > • One big button per player, usable one-handed with a cue in the other hand
  > • Races to any length, or open-ended matches; 9-ball, 10-ball or just a score
  > • Undo, including back into the previous match
  > • History of every night, down to each frame
  > • Stats: win rates, streaks, results by game, and how often the breaker wins
- **Graphics:** `play/icon-512.png` (app icon) and `play/feature-graphic.png` (feature graphic). Phone screenshots: see `app/build/screenshots` after `scripts/emulator-tests.sh` (Play needs at least 2, 16:9 or 9:16, with a minimum of 320 px on each side), or take them on the phone.

## Privacy policy

Play needs a public URL. The text is in `docs/privacy-policy.md`; replace `<CONTACT EMAIL>` before publishing it.

## Data safety

- **Does your app collect or share any of the required user data types?** Yes
- **Is all of the user data collected by your app encrypted in transit?** Yes
- **Do you provide a way for users to request that their data is deleted?** Yes (email; see the privacy policy)

| Data type | Collected | Shared | Optional? | Purposes |
|---|---|---|---|---|
| Personal info: Name | Yes | No | Required | App functionality, Account management |
| Personal info: Email address | Yes | No | Required | App functionality, Account management |
| Personal info: User IDs | Yes | No | Required | App functionality, Account management |
| Photos and videos: none (only a link to the Google profile photo, which counts as part of the name and profile) | No | | | |
| App activity: Other user-generated content (scores, venue names) | Yes | No | Required | App functionality |
| App info and performance: Crash logs | Yes | No | Required | App functionality (diagnosing crashes) |
| App info and performance: Diagnostics | Yes | No | Required | App functionality |
| Device or other IDs (Crashlytics installation ID) | Yes | No | Required | App functionality |

For every row: data is **not processed ephemerally**, and it's **collected, not shared**. Google Firebase processes it on the app's behalf, which Play counts as a service provider, not sharing.

## Content rating (IARC questionnaire)

- Category: **Utility, productivity, communication, or other**
- Violence, sexuality, language, controlled substances, gambling: **No** to every question. Scoring pool isn't gambling: no bets, prizes or money.
- User interaction: users **don't** interact or share content with the public. The two players share scores only with each other.
- Shares location: **No**. Digital purchases: **No**.
- Expected rating: PEGI 3 / Everyone

## Target audience and content

- **Target age groups:** 18 and over (avoids the Families policy requirements)
- **Appeals to children?** No
- **Ads:** The app contains no ads
- **Government app / financial features / health:** No
- **News app:** No

## Account deletion

Google Play's account-deletion policy applies to apps that let people create an account, which signing in with Google does. Published apps need an in-app deletion route plus a web link. The email route in the privacy policy covers an internal-testing release. If Rivals ever goes beyond internal testing, add a "Delete my account" action and a public web form.

## Access for review

App access: **All functionality is available without special access**. Quick games need no account, and any Google account can sign in and invite a rival. A reviewer can make a second test account to try rivalries.
