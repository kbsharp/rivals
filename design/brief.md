# Rivals design brief

Agreed 2026-09-22 with Kevin, from the references in `design/ref/` and the mock-ups at
<https://claude.ai/artifact/Cykp6oXE8meC1tupSfLu1c> (tokens, scoreboard, scoreboard with a match
won, Home, rivalry stats, rivalry sessions). Every screen pass is judged against this file.
The audit that led here is `design/audit.md`.

## The idea in one line

A sports broadcast at night: dark, calm and quiet, with white numbers as the hero. Colour names a
player or a state — never decoration.

## Palette

Dark is the design; light is derived from it later.

| Token | Hex | Used for |
|---|---|---|
| `base` | `#0D0B14` | App and scoreboard background. Not black: panels must stay visible. |
| `surface` | `#18151F` | Panels, the match-won banner, row avatars. |
| `raised` | `#231F2C` | Chips, secondary buttons, ring track. |
| `hairline` | `#332D40` | Empty race pips, the dash between scores. Never a divider line. |
| `fg` | `#F4F5F7` | Scores, headings, the primary button's fill. |
| `fg-2` | `#ABA6BA` | Secondary text. |
| `fg-3` | `#857F96` | Small uppercase labels (5.1:1 on base). |
| `you` | `#3FE3EC` | Your name, pips, bars, "you won". |
| `you-tint` | `you` at 16% | Chips on your side ("On the hill"). |
| `rival` | `#FF5FA8` | Your rival's name, pips, bars, "they won". |
| `rival-tint` | `rival` at 16% | Chips on their side. |
| `live` | `#FFB020` | The live dot, delete and errors. Amber, because red sits too close to the rival's pink. |

Rules that come with it:

- **Scores are white**, always. A player's colour appears on their name label, their race pips,
  their bar in a stat, and the "you won" caption — not on the number.
- **The primary button is white** with `base` text. Colour is never used for a plain action.
- **A chosen chip is `you` on `you-tint`.** Being chosen is a state, so it takes the accent
  rather than a white fill, which read as a toggle switch and left the pickers black and white.
- Two colours only, plus amber. No gradients, no tints of other hues, no colour for decoration.
- Cyan and hot pink ("neon hall", chosen 2026-09-24 over teal and apricot) are cool against warm,
  and differ in lightness too, so they separate for colour-blind eyes. Keep any replacement
  warm-vs-cool.

## Type

Two families, both open licence, bundled in the app (not downloaded):

- **Montserrat** (600/700/800) for every number and for screen titles.
- **Barlow** (400/500/600/700) for all other text.
- Every figure is tabular (`font-variant-numeric: tabular-nums`, or the Compose equivalent
  `FontFeatureSetting("tnum")`), so a score never shifts as it ticks.

| Step | Spec | Used for |
|---|---|---|
| score | Montserrat 700, sized to ~52% of the half's height | The scoreboard numbers (Home's hero stays 800) |
| display | Montserrat 800 · 56 / 60 | Head-to-head on a session, an invite, Sign in |
| headline | Montserrat 700 · 24 / 30 | Screen titles, the match-won line |
| title | Barlow 600 · 20 / 26 | Row titles, card headings |
| body | Barlow 400 · 16 / 22 | Sentences |
| label | Barlow 600 · 12 / 16, uppercase, +0.12em tracking, `fg-3` | Everything above a value |

## Spacing and shape

- 4dp grid: 4, 8, 12, 16, 24, 32, 48. Screen gutter 16dp. Sections 24dp apart.
- Touch targets at least 48dp.
- Radii: 8dp chips and score boxes, 16dp panels, fully round buttons.
- Space and alignment separate things. **No divider lines**, and no grey card on a grey background.

## Layout rules

- Every screen is anchored by a scoreboard: you, the score, your rival, plus a status label.
- One primary action per screen.
- A destructive action is never styled as primary; delete lives in an overflow menu.
- No helper text a returning user has already read (for example "no account needed" only appears
  signed out).
- Every screen has a written empty state and a loading state. Never a bare spinner.

## Motion and feel

- 150–250ms, no bounce.
- The score rolls when it changes; nothing else on the scoreboard moves.
- A crisp haptic on every score change.
- When a match is won, the board dims and a panel names the winner and the score, with Undo in it.

## The screens, as drawn in the mock-ups

- **Scoreboard:** charcoal field, both halves tappable. Player name above in their colour (20sp,
  read from across the table), the white score, then five race pips. A quiet line along the
  top holds the back-to-Home arrow and the match, game and race on the left, the clock centred
  with Undo beside it, and tonight's score and ≡ on the right. The foot of the board holds only
  the last frame's receipt, centred. Neither line, nor the system gesture strip, is
  part of either half's tap target, and the navigation bar stays so one swipe leaves the app. Nothing sits on the
  centre line.
- **Scoreboard, match won:** the halves dim to 30%, a `surface` panel gives the match, "Kevin
  takes it 5 – 2", what happens next, and an Undo button.
- **Home:** all-time score in big white numbers, each over its player's name in their colour, the
  last ten nights as a colour bar with who leads them, tonight's score, then the primary action.
  Below, three labelled blocks: **Rivals** (invites, the other rivals, Add a rival), **Play**
  (Quick game) and **On this phone** (the last three quick games in one `surface` panel, See all
  for the rest). Round two is at <https://claude.ai/artifact/DPKQkov4iMYFAUHTsAAYzD>.
- **Rivalry:** a win-percentage ring in both colours beside the all-time score, the primary action,
  then SESSIONS and STATS tabs. Stats is a mirrored table: your value, the label, theirs, with the
  leader's value white and bold and the other grey. Sessions is one row per night, with only the
  "you won" caption in colour.

## What Phase 12 settled

The screens are built. Three things the brief didn't pin down, decided in the building:

- **Home's all-time score is 96 / 96**, not the 56 / 60 `display` step. The mock-up draws it at
  96 on a 390dp phone and 56 is too quiet to be the screen's hero; `display` keeps its size
  everywhere else.
- **Home's form bar counts nights, not matches.** Home reads sessions; per-match form would be a
  collection-group query per rivalry. A drawn night is `hairline`, so the bar has three states.
  The mock-up says "last 10 matches"; the rivalry screen's Stats tab has the per-match record.
- **The light theme is derived, not designed.** The neutrals invert (`base` #F7F8FA, `surface`
  white, `fg` #15171C) and the two player colours darken until they carry 4.5:1 on the light
  ground: `you` #00788A, `rival` #C2186A, `live` #9A5B00 (neutrals `base` #F8F7FA, `fg` #15131C).
  It is legible rather than drawn.

## The logo

The diamond rack from `https://claude.ai/artifact/LdCkwaSaxS7kTzNNqFLZ5L` ("6a · Tips"): nine balls
racked for 9-ball, the top one `you`, the bottom one `rival`, the 9 in the middle `fg`, the rest
`hairline`, always on `base` (dark, in either theme, because the 9 is white). It is the launcher
icon (`ic_launcher_foreground`, with a one-colour `ic_launcher_monochrome` for themed icons), the
Play icon and feature graphic, and the launch splash. On Android 12+ the splash animates it
(`splash_icon_animated`): the rack fades in, then you, your rival and the 9 grow into place, 750ms
in all.

## What Home round two settled (2026-09-24)

- **Home's blocks are 32dp apart**, not the 24dp `section` step: with labels over them, 24 read as
  one list.
- **No rivals yet is a scoreboard at 0 – 0** ("You" against "Your rival", an empty `raised` form
  bar) with one line under it, not a paragraph.
- **Quick games are coloured like a match:** Player 1 in `you`, Player 2 in `rival` (as the Quick
  game dialog labels them), the score white, and "Tom won" in the winner's colour.
- **Another rival's row says who leads** ("Sam leads by 2") in the leader's colour, with the
  rival's initial in `rival` on `rival-tint`.
- **The top bar carries the rack**, drawn small (`RackMark`) beside "Rivals".

## What Home round three settled (2026-09-27)

Chosen by Kevin as "F2" from the mock-ups at <https://claude.ai/artifact/75cYVKXpgHLWThetPuspCB>;
it replaces the single-rival hero above.

- **Every rival is one mirrored table** in a `surface` panel, headed YOU · RIVALS · THEM: your
  all-time wins on the left, theirs on the right (the leader's white and heavy, the other `fg-3`,
  as in the Stats table), the name and who leads between them, and a split bar in both colours.
  Rows are kept apart by space, not lines.
- **Most recent first, three shown**, then See all N rivals. The one you're playing leads, and
  says "Playing now" in `live` with tonight's score.
- **Home has no primary button.** A rival's row is the way in: it opens their rivalry, whose
  primary action starts or resumes the night.
- **Play is two equal tiles**: Quick game (or Resume, since the phone only holds one running game)
  and Add a rival, which moved out of the rivals list.

## Where actions live (UX phase 3, 2026-09-26)

Agreed with Kevin in UX phase 3 (`design/ux-plan.md`, Decisions); the mock-ups are at
<https://claude.ai/artifact/PjVTJpHqistZrN2vwS7qXR>.

- **An action lives on the thing it acts on.** About the last frame (undo, tags): on the last
  frame's receipt at the foot of the board (tags), and Undo beside the clock at the top, away from
  the back arrow. About the match: the match sheet behind ≡, or the
  match-won panel for the next match. About the night: last in the match sheet, set apart.
- **The other phone is never surprised.** Anything done to the score (a frame, an undo, a tag)
  is named on both phones, and says when it came from the other phone.
- **Messages on the scoreboard go at the foot of the board**, in place of the receipt, never over a
  button, and never as a snackbar outside the palette.
- **Back from the scoreboard goes Home**, whether you use the arrow or the system gesture.
  Leaving never ends the night, and resuming shows the board as you left it.
- **A night ends at the table:** a full-time panel on both phones, then Home.
- **The top and foot lines are read from the rail.** Their text is Barlow 600 14 sp, +0.06em, in `fg-2`,
  and tonight's score is white Montserrat 700 20 sp. This is the one place outside the type table's
  steps. Nothing on them moves when something else comes and goes: the clock stays centred
  whether Undo shows or not, and the unsynced cloud has its own slot beside the receipt. A double frame (two within 10 s from different phones) is the one amber state on the board.

## Open design questions

- The scoreboard has no colour fill, so in a bright room the halves are told apart only by the
  small coloured names. If that reads weakly on the phone, add a tint of about 8% to each half
  rather than colouring the numbers. UX phase 3 kept this open: try it (option 6C)
  on the phone during UX phase 5's real night.
