# Walkthrough: one night on the phone

A real session against Julian, driven over `adb` on the Pixel 10a (debug build `da2e884`,
dark theme), 2026-09-26. Race to 2 so matches end fast; the session was deleted at the end, so
the head to head is back where it was (0 – 1). Taps were made by `adb shell input`; what
appears on screen is the app's own.

What the renders can't show and this does: the real order of screens, where the system back
gesture goes, what reappears on resume, how the phone rotates between Home and the board, and
the snackbar over the between-matches panel.

| # | Screen | Caption |
|---|---|---|
| 01 | Home | Opening the app: all-time 0 – 1 with Julian, *Play julian* as the one primary action. |
| 02 | New session dialog | *Play julian* opens it on the last settings used (9-ball, open-ended) and the recent venue. |
| 03 | New session dialog | Race set to 2 (tap Open-ended off, then − three times), *Kings* picked from Recent. |
| 04 | Board | *Start*: the phone turns to landscape and the board opens at match 1, 0 – 0. |
| 05 | Board, menu | Menu before any frame: *Undo last frame* greyed, *Change game*, *End session*. No tags yet. |
| 06 | Board | One tap on Kevin's half: 1 – 0, "On the hill" (race to 2). |
| 07 | Board, menu | Menu after a frame: the LAST FRAME heading and three tags, Undo, *End match* where *Change game* was, End session. |
| 08 | Board | *Break & run* tapped: the menu closes and nothing on the board says it was tagged. |
| 09 | Board, menu | The only sign of the tag: a tick beside it when the menu is opened again. (Tapping the board to close the menu doesn't record a frame.) |
| 10 | Board | Julian's frame: hill-hill, 1 – 1, both "On the hill". |
| 11 | Match won | Kevin's frame wins it: the board dims, "Kevin takes it 2 – 1", *Play on* and *Undo*. Match 2 has already started underneath. |
| 12 | Board, menu | The panel timed out (9 s) before the menu was opened. In match 2 at 0 – 0 the menu offers tags for the *last frame*, which is match 1's winner, next to *Change game* for match 2. |
| 13 | Home | One system back gesture left the scoreboard with no prompt and landed on Home in portrait: LIVE chip, all-time already counting tonight (1 – 1), *Resume session*. |
| — | Board (not captured) | *Resume session* reopened the board **with match 1's won panel showing again**, though it had been seen and timed out (the accessibility dump shows "Kevin takes it 2 – 1" and *Play on*; the screenshot missed it). *Play on* cleared it. |
| 14 | Board | Match 2, Julian's frame: 0 – 1, Julian on the hill. |
| 15 | End match confirm | Menu → *End match*: "Nobody has reached 2 yet, so it won't count for either of you." |
| 16 | Between matches | Confirmed. The between-matches panel, with a "Match ended with no winner" snackbar lying **over the Start match button**. |
| 17 | Between matches | Four seconds later the snackbar's gone: tonight 1 – 0, next match settings, *Start match*, then *End session*, *Undo last frame*, *Home* as text. |
| 18 | Board | *Start match* → match 3; Kevin's frame, 1 – 0. |
| 19 | Home | The board's back arrow → Home, portrait again, *Resume session*. |
| 20 | Rivalry | *Head to head* → the rivalry, mid-night: tonight 1 – 0 and *Resume session* above the tabs. |
| 21 | Board | *Resume session* from the rivalry: back on match 3, 1 – 0. The stack is now Home → Rivalry → Session. |
| 22 | Board | Menu → *Undo last frame*: straight back to 0 – 0, no confirmation and no message; the score rolling back is the only feedback. |
| 23 | End session confirm | Menu → *End session*: "Final score: Kevin 1 – 0 julian." |
| 24 | Rivalry | Confirmed: the board closes and the phone lands on the **rivalry** (it came from there), portrait. Tonight's night is at the top of Sessions ("Kings · 6m", 1 – 0, You won); there's no summary of the night on the way out. |
| 25 | Rivalry, Stats | The Stats tab, now including tonight. |
| 26 | Session detail | Tonight's row: 1 – 0, match 1's frames as boxes (frame 1 ringed for the break & run), "Won the hill-hill decider", match 2 "No winner". |
| 27 | Session detail, menu | ⋮ → *Delete session*, in amber, the only item. |
| 28 | Delete confirm | "Its matches and frames go too, and it comes off the head to head. This can't be undone." |
| 29 | Rivalry | Deleted: back on the rivalry, all time 0 – 1 again. |
| 30 | Home | Back arrow → Home, as it was at the start. |
