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
| `base` | `#131418` | App and scoreboard background. Not black: panels must stay visible. |
| `surface` | `#1B1D23` | Panels, the match-won banner, row avatars. |
| `raised` | `#262A33` | Chips, secondary buttons, ring track. |
| `hairline` | `#363B47` | Empty race pips, the dash between scores. Never a divider line. |
| `fg` | `#F4F5F7` | Scores, headings, the primary button's fill. |
| `fg-2` | `#A8AEBC` | Secondary text. |
| `fg-3` | `#808898` | Small uppercase labels (5.0:1 on base). |
| `you` | `#6FD3C4` | Your name, pips, bars, "you won". |
| `you-tint` | `you` at 16% | Chips on your side ("On the hill"). |
| `rival` | `#F0A883` | Your rival's name, pips, bars, "they won". |
| `rival-tint` | `rival` at 16% | Chips on their side. |
| `live` | `#FF4757` | The live dot, and delete. Nothing else in the app is red. |

Rules that come with it:

- **Scores are white**, always. A player's colour appears on their name label, their race pips,
  their bar in a stat, and the "you won" caption — not on the number.
- **The primary button is white** with `base` text. Colour is never used for a plain action.
- Two colours only, plus red. No gradients, no tints of other hues, no colour for decoration.
- Teal and apricot were chosen for colour-blind separation (warm against cool) and for being soft
  rather than loud. Keep any replacement warm-vs-cool and similar in lightness.

## Type

Two families, both open licence, bundled in the app (not downloaded):

- **Montserrat** (600/700/800) for every number and for screen titles.
- **Barlow** (400/500/600/700) for all other text.
- Every figure is tabular (`font-variant-numeric: tabular-nums`, or the Compose equivalent
  `FontFeatureSetting("tnum")`), so a score never shifts as it ticks.

| Step | Spec | Used for |
|---|---|---|
| score | Montserrat 800, sized to ~45% of the half's height | The scoreboard numbers |
| display | Montserrat 800 · 56 / 60 | Head-to-head on Home |
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

- **Scoreboard:** charcoal field, both halves tappable. Player name above in their colour, the
  white score, then five race pips. One quiet line along the bottom holds the clock, the match,
  the game, the race and tonight's score, with the menu button in the corner. Nothing sits on the
  centre line.
- **Scoreboard, match won:** the halves dim to 30%, a `surface` panel gives the match, "Kevin
  takes it 5 – 2", what happens next, and an Undo button.
- **Home:** all-time score in big white numbers with teal and apricot labels beneath, the last ten
  matches as a colour bar, tonight's score, then the primary action. Below: any invite, then plain
  rows for Add a rival and Quick game, then games kept on this phone.
- **Rivalry:** a win-percentage ring in both colours beside the all-time score, the primary action,
  then SESSIONS and STATS tabs. Stats is a mirrored table: your value, the label, theirs, with the
  leader's value white and bold and the other grey. Sessions is one row per night, with only the
  "you won" caption in colour.

## Open design questions

- The scoreboard has no colour fill, so in a bright room the halves are told apart only by the
  small coloured names. If that reads weakly on the phone, add a tint of about 8% to each half
  rather than colouring the numbers.
- Teal and apricot are close in lightness, so the form bar on Home reads as texture until you look
  at it. Check it on the phone before trusting it as a glanceable stat.
- Light theme is not designed yet; derive it once the dark screens are done.
