# UX review plan

A review of how Rivals *flows*: where each action lives, how you get from one screen to the next,
and what the in-game menu is for. Phases 11–12 (`design/brief.md`, `design/audit.md`) settled how
the app *looks*; this plan is about how it *works* to use. It runs before Release (PLAN.md,
Phase 7).

Kevin starts each phase in a new conversation ("let's do UX phase 1"). A session picking one up:
read this file, `design/brief.md` and the Design section of `CLAUDE.md`, do that phase only, tick
its boxes, write down what it decided, and stop at its exit. **Phases 1–3 change no app code**;
only new test renders in `Screenshots` are allowed.

---

## Context of use

_The situation the app is judged against. Kevin: correct anything here before phase 2 starts._

- **Where:** stood at a pool table, often in a dim pub or pool hall with bad signal. The phone lies
  on the rail or a table in landscape during a night, and gets picked up between frames.
- **Hands and attention:** often one hand free (cue or drink in the other). Glances, not reading.
  An action should be doable without breaking concentration on the game.
- **Two people, two phones:** both players can score; either phone can undo. Whoever acts must
  see clearly what happened, and the other phone must not be surprised.
- **How often each job happens** (the order that should decide prominence):
  1. Record who won a frame (dozens of times a night)
  2. Fix a mistake: undo the last frame (a few times a night)
  3. Tag a frame: break & run, golden break, won on three fouls (occasionally)
  4. End a match, start the next one, change game or race (a few times a night)
  5. End the night (once)
  6. Start a night, glance at the head-to-head (once a night)
  7. Look back at history and stats (between nights)
  8. Invite a rival, sign in, quick game as a guest (rarely, or once ever)
- **Known pain:** the in-game menu feels bunched. Things live in it because there was nowhere
  else to put them, not because they belong together.

---

## UX phase 1: Capture
_Give the audit eyes. Everything a player can see or reach, as screenshots and a map._

- [x] Add renders to `Screenshots` for every state the current set misses, in both themes:
      the session menu open, each overflow menu open, every dialog and confirmation (end match,
      end session, delete, undo), the tag picker, the change-game or race picker, pending/offline
      states, and anything else a player can open
- [x] Walk the app on the phone (or the emulator) over `adb` for what renders can't show: the
      real sequence of screens through one night, from opening the app to ending the session.
      Screenshots to `design/audit/walkthrough/`, numbered in order, each with a one-line caption
- [x] `design/flow.md`: a flow map built from `ui/navigation` and the screens. Every route, every
      way in and out of it (including back), and **every action on every screen**, with where it
      lives (on screen, menu, long-press, dialog), how many taps it takes from the session screen,
      and which job above it serves
- [x] Copy the full render set to `design/audit/renders/` so later phases don't need a test run

**Exit:** every screen, state, menu and dialog has an image; `design/flow.md` lists every action.

**Done 2026-09-26.** What it produced and decided:
- `FlowScreenshots` (androidTest) renders 64 more states in both themes: every menu open, every
  dialog, loading, pending, error and snackbar states. On the landscape board an open menu is
  composited where a landscape window puts it (the test window is portrait); a dialog over the
  board is shot on its own. Portrait menus and dialogs are shot on the whole display, over
  their screen. `design/audit/renders/` holds all 148 renders from both classes.
- The walkthrough was a real session against Julian, deleted afterwards (Kevin's choice): 30
  shots and captions in `design/audit/walkthrough/README.md`.
- `design/flow.md` maps routes, back behaviour, every action with its place, taps from the
  board and job, all dialogs and confirmations, and the overflow menus.
- Seen on the phone and not in the renders, for phase 2 to judge (recorded, not yet assessed):
  system back leaves the board for the previous screen with no prompt, while the board's own
  arrow goes to Home; resuming a session re-shows the last match-won panel after it was seen;
  the "Match ended with no winner" snackbar covers *Start match*; tagging a frame gives no
  feedback outside the menu; ending the night lands on Home or Rivalry, wherever it started,
  with no summary.

## UX phase 2: Audit
_A UX expert's review of the flow, before any solutions._

- [x] `design/ux-audit.md`, then published as an artifact (linked here). For each screen and each
      step of a night, check against the context above:
  - prominence against frequency (is the most common job the easiest?)
  - grouping (do the things in a menu belong together?)
  - mistakes and recovery (undo, destructive actions, confirmations that are missing or pointless)
  - feedback (does the player, and the other phone, know what just happened and what's pending?)
  - reach and glanceability (one hand, landscape, dim light, phone on the rail)
  - the way in and the way out of each screen; dead ends; back behaviour
  - consistency (the same kind of action in the same place everywhere)
- [x] **In-game menu inventory:** a table of every item with how often it's used, when in a night,
      and what it's grouped with today. Proposes where each belongs: on screen, in a contextual
      place (e.g. the match-won panel, a long-press on the last frame), in the menu, elsewhere, or gone
- [x] Findings ranked by severity (blocks a task / slows it / friction / polish), each with its
      screenshot and the job it hurts
- [x] **What works:** a short list of things the next phases must not break
- [ ] 🧑 Kevin reads it (comments on the artifact are fine) and picks which findings go forward

**Exit:** the audit is agreed and the list of problem areas to solve is written below.

**Written 2026-09-26; waiting on Kevin's read.** 16 findings (none blocks a task; 3 slow one,
all about fixing a mistake: undo is silent on both phones, two taps deep beside End session, and
nothing helps catch a double frame). The menu inventory moves tags and undo out of the menu to
contextual and on-screen places, leaving it *Match settings*, *End match*, *End session*. Six
problem areas are proposed for phase 3 (Fixing a mistake, Tagging, The game menu, Between
matches, Leaving and ending, Glancing from the rail), plus two small fixes for phase 4 as they
are. The chosen areas go in Decisions once Kevin has picked.

## UX phase 3: Proposals
_Real alternatives, so the choice isn't just "polish the first idea"._

- [x] One artifact, linked here: for each problem area from phase 2, **two or three different
      designs** as mock-ups side by side, built on `design/brief.md` (its palette, type and
      components only, landscape for the session screen). Each with its trade-off, the jobs it
      helps and hurts, and Claude's recommendation
- [x] The whole night as a storyboard in the recommended option: the sequence of screens from
      opening the app to ending the session
- [ ] 🧑 Kevin comments on the artifact; revise until each area has a decision
- [ ] Decisions written below, and any new rule of thumb (e.g. "where actions live") added to
      `design/brief.md` and the Session section of `CLAUDE.md`

**Exit:** every problem area has a chosen design, recorded here and in the brief.

**Proposed 2026-09-26; waiting on Kevin's comments.** No pick of problem areas was recorded
from phase 2, so all six are in (any can be dropped). Recommended: **1A** the last frame on the
status line with one-tap Undo, named on both phones, amber for a double frame · **2A** tag by
tapping that receipt, and in the match-won panel for the winning frame · **3B** ≡ opens a match
sheet (game and race editable mid-match, End match, End session) · **4A** the match-won panel sets
the next match; scoreboard messages live in the status line · **5A** back always goes Home, and
the night ends with a full-time panel on both phones · **6A** a louder status line, with 6C (tinted
halves) tried on the phone in phase 5. A 14-step storyboard runs a night through them. Source:
`design/ux-proposals.html`.

## UX phase 4: Build
_One problem area per pass, in the order below._

- [ ] For each area: change → `scripts/emulator-tests.sh` renders → compare with the chosen
      mock-up → fix. Add renders for any new state, and UI tests for any new action. Commit,
      push and install per the Conventions in `CLAUDE.md`
- [ ] Update `design/flow.md` as the flow changes

**Exit:** every decision is built, the renders match the mock-ups, tests pass.

## UX phase 5: A real night
- [ ] 🧑 Kevin and Julian play a night on the new build; Kevin notes anything that jars
- [ ] Fix those, or add them to the next round of this plan
- [ ] Tick Phase 13 in PLAN.md; Release (Phase 7) is next

**Exit:** a full night played without reaching for the wrong thing.

---

## Decisions

_Filled in by phases 2–3._

## Artifacts

_Links added as they are published._

- UX phase 2 audit: <https://claude.ai/artifact/16muSoaiVPkBa4JsyCfEck> (source `design/ux-audit.md`)
- UX phase 3 proposals: <https://claude.ai/artifact/PjVTJpHqistZrN2vwS7qXR> (source `design/ux-proposals.html`)
