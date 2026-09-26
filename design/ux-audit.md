# UX audit

UX phase 2 (`design/ux-plan.md`), 2026-09-26. A review of how Rivals flows, judged against the
plan's Context of use: a phone on the rail in a dim pub, one hand, glances, two phones that can
both score. Built from `design/flow.md`, the renders in `design/audit/renders/`, the walkthrough
in `design/audit/walkthrough/` and the code at `b556712`. It names problems only; phase 3
proposes the designs.

Jobs, as numbered in the plan: **1** record a frame, **2** undo, **3** tag a frame, **4** end or
start a match / change game, **5** end the night, **6** start a night / glance at the head to
head, **7** history and stats, **8** invite, sign in, quick game.

Severity: **blocks** a task · **slows** it · **friction** (works, but jars or surprises) ·
**polish**. Nothing found blocks a task outright: every job can be done. What hurts most is the
second job, fixing a mistake.

---

## Summary

The first job is right: one tap on a half, a haptic, the score rolls. Everything after it is
weaker, and it gets weaker in order of frequency. **Undo, the second most frequent job, is as
deep as ending the night, sits in the same list, and happens silently on both phones.** Tagging
gives no sign it worked. The in-game menu holds four jobs that happen at different moments of a
night. Leaving and ending the night are inconsistent: system back and the board's arrow go to
different places, and a night ends with no payoff.

---

## Findings, ranked

### Slows a task

**F1. Undo is silent, on both phones.** Job 2. *Walkthrough 22.*
Undo happens with no message: the score rolling back is the only sign. On the phone that did it
that's just about enough; on the other phone a score drops by one with nothing to say why, which
is exactly the surprise the Context of use rules out. When undo reaches back into the previous
match (a new match just started), it deletes that match and reopens the old one, again without a
word. The player can't tell *which* frame was taken back, so a wrong undo goes unnoticed.

**F2. Undo is two taps deep, next to End session.** Job 2. *Walkthrough 07.*
The second most frequent job lives in the same menu, at the same depth, as the once-a-night job,
and one row above *End match* and *End session*. It needs a precise tap on a small menu row in
the far corner rather than a glance and a thumb. The match-won panel is the one place where undo
is one tap, and only for 9 seconds.

**F3. Nothing guards against a double or stray frame.** Jobs 1, 2. *Render `session-board`.*
Each half of the screen is the tap target, and both phones can score. If both players record the
same frame, or a thumb lands on the screen as the phone is picked up off the rail, two frames
count. Nothing on the board says who recorded the last frame or when, so the mistake is only
caught by reading the score, and the fix is F1–F2's silent two-tap undo. (Protecting the tap
must not slow job 1: this is about *seeing* and *recovering*, not confirming.)

### Friction

**F4. The in-game menu mixes four jobs.** Jobs 2, 3, 4, 5. *Walkthrough 07, 12.*
Up to six items and a heading: three tags (about the last frame), undo (a fix), change game or
end match (the match), end session (the night). They happen at different moments and share only
the heading. The menu changes shape as the night goes on (tags appear after the first frame;
*Change game* and *End match* swap in the same slot), so no item has a fixed place to learn.
See the inventory below.

**F5. Tagging gives no feedback and doesn't say which frame.** Job 3. *Walkthrough 08, 09, 12.*
Tapping *Break & run* closes the menu and nothing on the board changes; the only sign is a tick
next time the menu is opened. The other phone learns nothing. "LAST FRAME" doesn't say whose or
which: straight after a match is won it means the previous match's winning frame, while the
menu beside it offers *Change game* for the new match (walkthrough 12). The tag most worth
recording (a break & run that wins the match) is the one that has to be found behind the
match-won panel.

**F6. Back goes two different places, with no prompt.** Job 6. *Walkthrough 13, 19, 24.*
The board's arrow goes to Home; the system back gesture pops one screen (Home or Rivalry, where
the night began). With the phone lying on the rail, an edge swipe is easy to make by accident:
it leaves the board at once, and the phone rotates to portrait. Nothing is lost (the session
keeps running), but it jars, and the way back is *Resume session* plus another rotation.

**F7. Resuming shows the match-won panel again.** Job 1. *Walkthrough, between 13 and 14.*
After leaving and resuming, the last match-won panel comes back though it was seen and timed
out, because "seen" isn't kept when the board is left. The dimmed board then swallows the first
tap. It says something already known, and costs a tap.

**F8. The between-matches snackbar covers Start match.** Job 4. *Walkthrough 16.*
"Match ended with no winner" appears for about four seconds over the primary button, as a light
block that isn't in the brief's palette (the same happens with the offline message on the
board, render `session-message`). The confirm dialog already said the match won't count, so the
message repeats it at the moment it's in the way.

**F9. A night ends with no payoff, in different places.** Jobs 5, 7. *Walkthrough 23, 24.*
Confirming *End session* closes the board and lands on Home or Rivalry, wherever the night
began. There is no summary of the night on the way out; tonight's result is a row in Sessions
(on Rivalry) or folded into the all-time score (on Home). The Session detail screen already
tells the story of the night (score, highlights, each match's frames) but is three taps away.

**F10. A match's settings can't be changed once it's started.** Job 4. *Render `session-menu`.*
*Change game* is only offered before a match's first frame. A race set wrong (5 instead of 7) is
found out at 3 – 2; the only way out is *End match*, which throws the match away. The time to
change the next match's settings (just after a match is won) is in the panel's 9 seconds, but
the panel doesn't offer it; it's in the menu, behind the tags.

**F11. The status line is too quiet to glance at from the rail.** Jobs 1, 4. *Walkthrough 04.*
Match number, game, race and tonight's score are in the small uppercase `label` step in `fg-3`,
centred along the bottom. From a table's length in a dim room it's unreadable; the only large
thing on the board is the frame score. The brief's own open question (telling the halves apart
in a bright room) is the same problem at the other end of the lighting.

### Polish

**F12. The between-matches panel has three text actions of mixed kinds in a row.** Jobs 2, 4, 5,
6. *Walkthrough 17.* *End session* (the night), *Undo last frame* (a fix) and *Home*
(navigation) sit together as equal text under *Start match*, with End session nearest the thumb
on the left. The dash between the two scores is almost invisible on `base`.

**F13. The game menu opens over your rival's score.** Job 1. *Walkthrough 07.* Anchored at the
bottom right, it covers the right-hand half, so you can't see the score you're about to undo or
tag.

**F14. A finished quick game can be saved only from Home's row.** Job 8. *Render
`detail-quick-game`.* Opening the game shows it in Session detail with no *Save*; the action
lives on the row you tapped to get there.

**F15. Names are shown as typed.** Job 6. *Walkthrough 01, 20.* "Play julian", a rivalry titled
"julian". Display names aren't capitalised, so the one primary action on Home reads lower-case.

**F16. Some small actions aren't confirmed.** Job 8. Sign out, decline an invite, cancel an
invite. Each is rare and recoverable (sign in again, send another invite), so no confirmation
is right; listed only so phase 3 doesn't add one by accident.

---

## In-game menu inventory

The menu today (≡, bottom-right of the status line), in its order, and where each item belongs.

| Item | How often | When in a night | Grouped with today | Proposed home |
|---|---|---|---|---|
| *Break & run* | 0–3 a night | Just after the frame, often the frame that wins a match | Other tags, under "LAST FRAME" | **Contextual:** on the last frame, where it's just been recorded on the board, and in the match-won panel for the winning frame. Not in the menu |
| *Golden break* | Rare | Same | Same | Same |
| *Won on three fouls* | Rare | Same | Same | Same |
| *Undo last frame* | A few a night | Straight after a wrong tap, or a frame later | The tags, then match and night actions | **On screen**, one tap, near the last frame, with a message naming what was undone on both phones. Stays in the match-won panel |
| *Change game* | Rarely; before a match | Between matches, or at 0 – 0 | Tags, undo, end session | **Contextual:** the match-won panel and the between-matches panel, where the next match is set. The menu keeps it as *Match settings*, available during a match (F10) |
| *End match* | Rarely | Leaving mid-match, or an open-ended match | Same slot as Change game | **Menu**, beside the match's settings |
| *End session* | Once | End of the night | Everything | **Menu**, last, apart from the match actions |
| Back arrow (status line, not the menu) | Now and then | Glancing at Home or the rivalry mid-night | — | **Stays**, but with the same destination as system back (F6) |

With tags and undo moved out, the menu is about one thing, the match and the night: *Match
settings*, *End match*, *End session*. It stops changing shape, and every item in it is rare
enough to be two taps away.

---

## Checked against the seven questions

- **Prominence against frequency.** Job 1 is right (one tap, the whole half). Job 2 is at the
  depth of job 5 (F2). Job 3 is fine at two taps but blind (F5). Jobs 6 and 7 are right on Home
  and Rivalry, where *Resume session* is the primary action mid-night.
- **Grouping.** The game menu (F4) and the between-matches panel's text row (F12). Home's blocks
  (Rivals, Play, On this phone) and the single-item overflows are coherent.
- **Mistakes and recovery.** Undo is present everywhere a frame can be recorded, but silent
  (F1) and deep (F2); nothing helps catch a double frame (F3). The confirmations are the right
  ones: ending a match or the night, removing a rival, deleting a session. Undo is rightly not
  confirmed. The one irrecoverable path is F10 (a wrong race).
- **Feedback.** Recording a frame: good (roll, haptic). Undo, tags: none (F1, F5). Pending sync:
  a small cloud on the board and Home, adequate. Match won: very good, on both phones. Snackbars
  cover content and break the palette (F8).
- **Reach and glanceability.** The halves are ideal for one hand. The menu is a small target in
  the far corner (F2, F13). The status line can't be read from the rail (F11).
- **Ways in and out.** Every screen has a way back; no dead ends. Back is inconsistent on the
  board only (F6). The end of the night drops the player in two different places (F9). Resume
  re-shows old news (F7).
- **Consistency.** Destructive actions are consistently in overflow menus and amber; dialogs
  are consistent. Inconsistent: back on the board (F6), *Save* only on Home's row (F14), and
  undo (menu item on the board, button in the match-won panel, text in the between-matches
  panel).

---

## What works (phases 3–4 must not break these)

- **One tap on a half records a frame**, with a haptic and a rolling score. Nothing else on the
  board moves.
- **The match-won panel**: the board dims, the winner and score are named on both phones, *Undo*
  is in it, the next match has already started, and it clears itself.
- **A tap on the dimmed board dismisses the panel** instead of recording a frame, so an early
  tap can't score in the next match.
- **Starting a night is two taps** from Home (*Play Julian* → *Start*): the dialog remembers the
  last game, race and venue.
- **Leaving the board never ends the night.** *Resume session* is the primary action on Home and
  Rivalry while a night runs, with the LIVE chip and tonight's score.
- **The confirmations say what will happen**: whether a match counts and for whom, the final
  score, that an empty session is deleted.
- **No on-screen clutter**: nothing on the centre line; the status line carries the rest.
- **Session detail tells the story of a night**: highlights, boxed frames, the decider.
- **Destructive actions** are amber, confirmed and one level down, never primary.

---

## Problem areas for phase 3

Proposed; Kevin picks which go forward (see the plan).

1. **Fixing a mistake** (F1, F2, F3, F13): undo on the board in one tap, what it undid said on
   both phones, and a way to see who recorded the last frame.
2. **Tagging** (F5): tag the frame where it happened, with visible feedback on both phones.
3. **The game menu** (F4, F10): one group, match and night, with settings changeable mid-match.
4. **Between matches** (F8, F10, F12): the match-won panel and the between-matches panel as the
   place for the next match's settings; messages that don't cover the action.
5. **Leaving and ending** (F6, F7, F9): one meaning for back, resume without old news, and an end
   of the night that shows the night.
6. **Glancing from the rail** (F11): the status line, and the brief's open question on the
   halves.

Small fixes that need no proposal, to build in phase 4 as they are: F14 (*Save* on the quick
game's detail), F15 (capitalised names), F16 (leave as is).
