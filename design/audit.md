# Design audit (2026-09-22)

A product-design review of every screen, against the references in `design/ref/`. Renders come from `Screenshots` (`scripts/emulator-tests.sh`, output in `app/build/screenshots`), which now draws the scoreboard in landscape with a running clock, and adds History, Sign in, Invite and the empty and between-matches states.

## What the references share

| Ref | What to take from it |
|---|---|
| `Pasted image.png` (humidity dial) | One enormous heavy geometric number as the hero. Tiny uppercase tracked labels above each value ("CURRENT HUMIDITY"). Deep ink-navy base, one glowing accent, no cards at all. |
| `Pasted image (2).png` (golf app) | Charcoal base, a single green accent used only for meaning. Lists are rows, not cards, with the big number right-aligned in the accent colour and a small delta badge (+63). A ring for the hero stat. |
| `Pasted image (3).png` (football final score) | The scoreboard header: team, score, team, with a status chip (FINAL) and a caption (FULL TIME). A mirrored stats table (value · label · value) is exactly what a two-player rivalry needs. |
| `Pasted image (4).png` (NFL boxes) | Broadcast idiom: boxed score digits, a red LIVE pill, uppercase tracked tabs, and an event banner with an accent outline (TOUCHDOWN) for the moment something happens. |
| `Pasted image (5).png` | Identical to (4); drop one or swap in another reference. |

Common DNA: an **ink-navy base** (not green-grey), **heavy geometric numerals**, **small uppercase labels**, **saturated colour only where it means something**, **rows and space instead of cards and lines**, and a **scoreboard header** as the anchor of each screen.

The Phase 11 draft brief proposed a base tinted towards baize green. The references point clearly to ink-navy; the revised brief below follows the refs.

## Bugs found by the new renders (fix regardless of direction)

1. **Between-matches panel, dark theme: black text on black.** "Next match", "Tonight: …", "Race to" and the setting labels are unreadable (`session-next-dark.png`). The panel also scrolls in landscape, so the lower settings sit below the fold. *Fixed.*
2. **Match clock renders black on the dark pill** (`session-dark.png`); the rest of the pill is light. Both came from text with no `Surface` to set its colour. *Fixed.*
3. **The pill and the menu button sit on the divide** between the two halves, over both colours, which weakens the one idea of the screen (left is Kevin, right is Julian). *Moved into the scoreboard mock-ups.*
4. **Stats empty copy is out of date:** "once your rival has signed in" (rivals now accept an invite, so the real empty case is "no sessions yet"). *Fixed.*
5. **Rivalry title says "You v Julian"** while the score below says "Kevin"; pick one voice. *Fixed: the title is the rival's name and your side reads "You".*
6. **Session detail puts Delete in the top bar in the accent colour**, styled as the page's main action. *Fixed: moved to the overflow menu.*

## Top 10 issues, ranked

1. **The scoreboard isn't a scoreboard yet.** Pastel mid-tone halves with regular-weight Roboto digits look like a settings toggle, not a broadcast graphic. Numbers aren't tabular, so 9→10 shifts. Nothing happens on a tap beyond a haptic. *Refs 1, 4:* heavy tabular numerals, deeper colour, a number roll, and a TOUCHDOWN-style banner when a match is won ("KEVIN TAKES MATCH 4 · 5–3", with Undo).
2. **No type system.** Everything is default Roboto at regular weight; hierarchy comes only from size. *Ref 1:* uppercase tracked labels over heavy values. Bundle one display face with tabular figures for every number, and keep a quiet text face.
3. **Colour means nothing.** The app's primary mint is used for every button, the "Session running" text, links and chips, and it clashes with both player colours. *Ref 2:* one accent, used for meaning only. Player colours should be the only saturated colour in the app, plus a red LIVE for live sessions.
4. **Dark mode is grey cards on near-black** (Home, History, Stats). *Refs 1–4:* an ink base with barely-raised surfaces, and rows rather than cards.
5. **Home has no hero.** A signed-in player's rivalry is the point of the app, but Quick game is the biggest thing on screen and the rival is a plain outlined card. *Ref 3:* the rival as a scoreboard header (you · 12–9 · Julian, LIVE chip). Quick game drops to a secondary row once you have a rival.
6. **Rivalry screen: dead top half, three equal buttons.** *Ref 3:* scoreboard header with an ALL TIME chip, a form guide (last 10 matches as coloured dots), one primary Start or Resume, then SESSIONS | STATS tabs in place. That folds History and Stats into this screen, removing two screens and two taps.
7. **Stats reads as a list of sentences.** "Kevin 12 … 9 Julian" repeats names in every card, and Nights and Streaks are prose. *Ref 3:* one mirrored table (Kevin value · label · Julian value), with the leader's value in their colour. *Ref 2:* a ring for overall win %. The form guide doubles as the streak.
8. **History and detail are generic cards.** *Ref 2:* rows with a small date label, venue, and the score right-aligned in bold tabular type, coloured for whoever won the night. Detail gets the ref 3 header with a FINAL chip, frame results as boxed digits, and Delete moves to an overflow menu.
9. **Sign in and Invite are text floating in a void.** Invite especially: it's the first thing Julian sees. Show it as a scoreboard: "KEVIN vs YOU · 0–0 · NEW RIVALRY", with the accept button beneath. Sign in needs the brand mark and a reason, not just a button.
10. **Empty, loading and error states are bare** (spinners, one centred line). Each needs a designed state: Stats empty shows the mirrored table at 0–0 with "Play your first session"; History empty shows the rivalry header with "No nights yet".

## Smaller notes

- **Scoreboard:** "on the hill" is plain text; make it a chip. The pill's three lines compete; make it one status bar: `12:34 · MATCH 4 · 8-BALL · RACE TO 5 · TONIGHT 2–1`, with the sync icon only when offline for a while.
- **Scoreboard:** tagging a frame means opening the menu. A transient "Tag frame" chip for 4 seconds after each frame (Break & run / Golden break) would save a dive, and it disappears by itself so the screen stays clean.
- **Home:** the "Keep score for any game, no account needed" helper line is permanent noise for a signed-in player; show it only when signed out.
- **Home:** the incoming invite should use the ref 4 event-banner style (accent outline, name, Accept) so it reads as news, not a form.
- **Add a rival:** three sections split by dividers. Make them three rows (Email · Link · Code) with the email field open by default.
- **Launcher icon** is still the placeholder; do it last, once the palette and typeface are chosen.
- **Light theme:** keep it, but derive it from the dark design rather than designing both.

## Revised design brief (proposed for `CLAUDE.md`)

- **Mood:** a sports broadcast at night. Calm and dark until something happens, then it's loud.
- **Dark first:** ink-navy base (around `#0B0F1C`), surfaces a step lighter, no borders. Light theme is derived.
- **Colour:** the two player colours are the only saturated colours, plus red for LIVE. Everything else is white-on-ink at three emphasis levels. Colour always means a player or a state, never decoration.
- **Numbers:** one heavy display face with tabular figures for every score and stat (candidates: Barlow Condensed ExtraBold for a broadcast feel, or Montserrat ExtraBold for the ref 1 feel; decide in the mock-ups).
- **Labels:** small uppercase with wide tracking above values; text otherwise in a quiet sans.
- **Layout:** every screen is anchored by a scoreboard header (you · score · rival + status chip). Rows and space, not cards and dividers. 4/8dp grid, 16dp gutters.
- **Hard rules:** no dividers, no grey cards, one primary action per screen, destructive actions never styled as primary, no helper text a returning user has already read.
- **Motion:** 150–250ms, no bounce. A number roll on every score change and a banner moment when a match is won.
- **Feel:** a crisp haptic on every score change; nothing on the scoreboard moves unless the score does.
- **States:** every screen has a designed empty, loading and error state.

## Suggested order

1. **Fix the six bugs above** (small, independent of taste).
2. **Approve or edit the brief**, then it moves into `CLAUDE.md`.
3. **Mock-ups before code:** three directions for the scoreboard and for Home/Rivalry, as a shareable page, choosing the typeface at the same time.
4. **Tokens:** palette, fonts, type scale and spacing in `ui/theme`.
5. **Screen passes** in the order of the ranking: scoreboard, Home/Rivalry (with History and Stats folded in), Stats, History and detail, Invite and Sign in, states, icon.
