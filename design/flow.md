# Flow map

How Rivals is put together as a thing to use: every route, every way in and out of it, and
every action on every screen. Built for UX phase 1 (`design/ux-plan.md`) from
`ui/navigation` and the screens as of `da2e884`. Nothing here is a judgement; that's phase 2.

- **Renders** of every state named below are in `design/audit/renders/` as `<name>-dark.png` /
  `<name>-light.png`. On the landscape board, an open menu is composited onto the board where a
  landscape window would put it; a dialog over the board is shot on its own.
- **The real sequence** of a night, on the phone, is in `design/audit/walkthrough/`.
- **Jobs** are the numbered list in the plan's Context of use: 1 record a frame, 2 undo,
  3 tag a frame, 4 end/start a match or change game, 5 end the night, 6 start a night / glance
  at the head to head, 7 history and stats, 8 invite, sign in, quick game.
- **Taps** count from the running scoreboard, confirmations included, so the numbers compare
  with job 1 (one tap). "—" means it isn't reachable from a night without leaving it.

## Routes

```mermaid
flowchart LR
  Home -- "Play X → New session → Start" --> Session
  Home -- "Resume session" --> Session
  Home -- "Quick game → Start / Resume quick game" --> SessionG["Session (guest)"]
  Home -- "Head to head · rival row" --> Rivalry
  Home -- "Add a rival" --> AddRival
  Home -- "Sign in" --> SignIn
  Home -- "quick-game row" --> DetailG["Session detail (guest)"]
  Rivalry -- "Start session → Start / Resume session" --> Session
  Rivalry -- "Sessions row" --> Detail["Session detail"]
  AddRival -- "Enter a code → Open invite" --> Invite
  Link(("invite link")) --> Invite
  Invite -- "Sign in with Google to accept" --> SignIn
  Invite -- "Accept" --> Rivalry
  Session -- "back arrow" --> Home
```

| Route | Ways in | Ways out | System back |
|---|---|---|---|
| **Home** (`HomeRoute`, start) | Launch; back from anything; the board's back arrow; sign-out from a rival's screen | Every route below | Leaves the app |
| **Sign in** (`SignInRoute`) | Home top bar *Sign in*; Home signed-out *Sign in with Google*; Invite *Sign in with Google to accept* | Signed in → pops back where it came from; back arrow | Pops back |
| **Add a rival** (`AddRivalRoute`) | Home *Add a rival* (row, or the primary button with no rivals) | *Open invite* → Invite; share sheet (leaves the app); back arrow | Pops back |
| **Invite** (`InviteRoute`) | Share link `rivals-15bd9.web.app/invite/{code}` (deep link); Add a rival → *Enter a code* | *Accept* → Rivalry, with the stack reset to Home → Rivalry; *Sign in…* → Sign in; back arrow → pops, or Home when opened cold from the link | Same as the back arrow |
| **Rivalry** (`RivalryRoute`) | Home *Head to head* (hero), another rival's row; Invite *Accept* | Session; Session detail; *Remove rival* → Home (the screen leaves when the rivalry is gone, here or on the other phone); back arrow | Pops back |
| **Session** (`SessionRoute`) | Home *Play X* / *Resume session* / *Quick game* / *Resume quick game*; Rivalry *Start session* / *Resume session* | Back arrow → **Home** (pops everything above it); ending the session → pops back one (Home or Rivalry); the other phone ending it → same | Pops back one: Home, or **Rivalry** if started there — not the same place as the back arrow |
| **Session detail** (`SessionDetailRoute`) | Rivalry Sessions row; Home quick-game row (guest) | Back arrow; *Delete session* → pops back | Pops back |

Signing out while on a rival's screen (Rivalry, Add a rival, a rivals' Session or its detail)
pops to Home; a guest game carries on.

## Home

The only screen with more than one job on it. Its shape changes with who's signed in and
what's going on; renders: `home`, `home-idle`, `home-live`, `home-invites`,
`home-two-rivals`, `home-no-rivals`, `home-guest` / `home-signed-out`,
`home-signed-out-quick-game-running`, `home-quick-game-running`, `home-guest-games-all`,
`home-loading`, `home-message`.

| Action | Where it lives | Taps from the board | Job |
|---|---|---|---|
| **Resume session** (a night is running) | Primary button under the hero | 1 back + 1 = 2 | 6 |
| **Play *Julian*** → New session dialog → *Start* | Primary button → dialog (`home-new-session`): game (optional), race, venue, recent venues | — | 6 |
| *Head to head* → Rivalry | Text action beside "All time" (hero) | 2 | 7 |
| Glance at the head to head, form bar, tonight's score | On screen (hero) | 1 | 6 |
| Open another rival | Row in **Rivals** (says who leads, or "Playing now") | 2 | 7 |
| Accept / Decline an incoming invite | Buttons on the invite row in **Rivals**; no confirmation either way | — | 8 |
| Cancel an outgoing invite | Text action on the pending row; no confirmation | — | 8 |
| *Add a rival* | Row at the end of **Rivals**; the primary button when there are none | — | 8 |
| *Quick game* → dialog (names, game, race) → *Start* | Row in **Play** (`home-quick-game`); primary button when signed out | — | 8 |
| *Resume quick game* | Replaces the Quick game row / button while one runs | — | 8 |
| Open a finished quick game | Row in **On this phone** → Session detail (guest) | — | 7 |
| *Save* a quick game to a rivalry → dialog (which side was you, against whom) | Button on each quick-game row, signed in with a rival (`home-save-game`) | — | 8 |
| *See all N* / *Show fewer* quick games | Text action beside **On this phone** (more than three) | — | 7 |
| *Sign out* | Overflow menu, top bar (`home-menu`); its only item; no confirmation | — | 8 |
| *Sign in* | Top bar text action; *Sign in with Google* below the fold when signed out | — | 8 |
| Pending-sync cloud | Icon in the top bar (`home-live`) | — | feedback |

## Rivalry

Renders: `rivalry`, `rivalry-stats`, `rivalry-live`, `rivalry-empty`, `rivalry-not-accepted`,
`rivalry-starting`, `rivalry-loading`, `rivalry-error`.

| Action | Where it lives | Taps from the board | Job |
|---|---|---|---|
| **Start session** → New session dialog → *Start* | Primary button (`rivalry-new-session`); disabled until the invite's accepted | — | 6 |
| **Resume session** | Replaces Start, with tonight's score above it (`rivalry-live`) | 3 (back, Head to head, Resume) | 6 |
| Glance: win ring, all-time score, streak line | On screen | 2 | 6, 7 |
| *Sessions* / *Stats* tabs | Tabs under the primary button | 2 / 3 | 7 |
| Open a past night | Row in Sessions → Session detail | 3 | 7 |
| *Remove rival* → confirm | Overflow menu (`rivalry-menu`), its only item, in amber → dialog (`rivalry-remove`) | — | 8 |
| Back | Top bar arrow | — | — |

## Session (the scoreboard)

Landscape, status bar hidden, screen kept on. Three states: the board with a match running,
the match-won panel over the dimmed board, and the between-matches panel (only after a match
is ended by hand). Renders: `session`, `session-board`, `session-first-frame`,
`session-hill-hill`, `session-open-ended`, `session-quick-game`, `session-pending`,
`session-message`, `session-loading`, `session-won`, `session-next`.

### On the board

| Action | Where it lives | Taps | Job |
|---|---|---|---|
| **Record a frame** | Tap the player's half; a haptic, and the score rolls | 1 | 1 |
| Back to Home | Arrow, left end of the status line (session keeps running) | 1 | 6 |
| Read the clock, match number, game, race, tonight's score | Status line along the bottom | 0 | 1, 4 |
| Pending-sync cloud | Status line, after the clock (`session-pending`) | 0 | feedback |
| "On the hill" | Chip under a player's pips at race − 1 | 0 | 1 |
| Errors ("Couldn't save…", "Nothing to undo", "No frame to tag yet") | Snackbar above the status line (`session-message`) | 0 | feedback |

### The game menu (≡, right end of the status line)

Its contents change with the state of the night (`session-menu`, `session-menu-new-match`,
`session-menu-first-frame`):

| Item | Shown when | Taps | Job |
|---|---|---|---|
| **LAST FRAME** heading, then *Break & run*, *Golden break*, *Won on three fouls* (a tick on the ones set; tap toggles) | Any frame has been played this session. The frame is the session's last one, which is the previous match's winner right after a match is won | 2 | 3 |
| *Undo last frame* | Always; greyed out before the first frame | 2 | 2 |
| *Change game* → Match settings dialog → *Save* (`session-change-game`) | The running match has no frames yet | 3 + adjustments | 4 |
| *End match* → confirm (`session-end-match`, `session-end-match-open-ended`) | The running match has frames | 3 | 4 |
| *End session* → confirm (`session-end-session`, `session-end-session-empty`) | Always | 3 | 5 |

- Up to 6 items plus a heading. Tagging (occasional, about the last frame), undo (a fix),
  match settings and ending the night all share one list with no grouping but the heading.
- Undo has no confirmation and no feedback beyond the score rolling back. It reaches back one
  match: with a new match just started, it deletes it and takes the winning frame off the
  previous one, reopening it.
- *End match* only appears once a frame is in; *Change game* only before. They sit in the same
  slot.
- *End match*'s confirm says what the match will count as: with a race, "won't count for
  either of you"; open-ended, the leader wins it, or level counts for nobody.
- *End session*'s confirm gives the final score and, with frames in the running match, what
  happens to it; with nothing played, it says the session will be deleted.

### Match won (`session-won`)

A race is reached → the next match starts automatically with the same settings; the board dims
and a panel says "Kevin takes it 5 – 2" and "Match 5 starts now. Tonight 3 – 1.", on both
phones, with a heavier haptic.

| Action | Where it lives | Taps | Job |
|---|---|---|---|
| *Play on* | Primary button in the panel | 1 | 1 |
| Tap anywhere on the dimmed board | Same as Play on (a frame tap here does not record) | 1 | 1 |
| *Undo* | Secondary button in the panel: takes the winning frame back and reopens the match | 1 | 2 |
| Wait | The panel clears itself after 9 s | 0 | — |
| Change the next match's game or race | Menu → *Change game* (the new match has no frames) | 3+ | 4 |

### Between matches (`session-next`)

Only after *End match* by hand. Laid out in two columns: tonight's score; "Next match" with the
settings picker.

| Action | Where it lives | Taps (from here) | Job |
|---|---|---|---|
| **Start match** | Primary button, with the game and race picker above it | 1 + adjustments | 4 |
| *End session* → confirm (`session-next-end-session`) | Text action, bottom left | 2 | 5 |
| *Undo last frame* | Text action, bottom right (reopens the ended match) | 1 | 2 |
| *Home* | Text action, bottom right | 1 | 6 |

### Leaving the session

- *End session* (menu, or the between-matches panel) → confirm → the session ends on both
  phones and each pops back one screen (Home or Rivalry). A session with nothing played is
  deleted rather than kept.
- The back arrow goes to Home and leaves the session running; Home and Rivalry then offer
  *Resume session*. System back pops one screen instead.
- Resuming re-shows the last match-won panel if nothing has been played in the new match yet,
  even after it was seen on this phone: "seen" isn't kept when the board is left
  (walkthrough, after 13).

## Session detail

Renders: `detail`, `detail-quick-game`, `detail-no-matches`, `detail-not-found`,
`detail-loading`, `detail-menu`, `detail-delete`.

| Action | Where it lives | Taps from the board | Job |
|---|---|---|---|
| Read the night: score, venue and times, highlights, each match's frames as boxed digits | On screen | 3 (back, Head to head, row) | 7 |
| *Delete session* → confirm | Overflow menu, its only item, amber (`detail-menu` → `detail-delete`); only for an ended session | — | 7 |
| Back | Top bar arrow | — | — |

A finished quick game opens here too; there's no *Save* on this screen (it's on Home's row).

## Add a rival

Three rows, one open at a time: renders `add-rival-closed`, `add-rival`/`add-rival-found`,
`add-rival-not-found`, `add-rival-invited`, `add-rival-link`, `add-rival-code`,
`add-rival-busy`. All job 8.

| Action | Where it lives |
|---|---|
| *Find by email* → type → *Find* (or the keyboard's search) → *Invite* on the result | First row, expands; result row with a secondary *Invite* |
| *Share a link* → *Share an invite link* → Android share sheet | Second row, expands |
| *Enter a code* → type → *Open invite* (or the keyboard's Go) → Invite | Third row, expands |
| Back | Top bar arrow |

## Invite

Renders `invite`/`invite-signed-out`, `invite-signed-in`, `invite-own`, `invite-gone`,
`invite-loading`, `invite-accepting`, `invite-error`. All job 8.

| Action | Where it lives |
|---|---|
| *Accept* (signed in) → Rivalry | Primary button, bottom |
| *Sign in with Google to accept* (signed out) → Sign in, then back here to Accept | Primary button, bottom |
| *Retry* | On the error state |
| Back | Top bar arrow |

## Sign in

Renders `sign-in`, `sign-in-busy`, `sign-in-error`. Job 8.

| Action | Where it lives |
|---|---|
| *Sign in with Google* → the system's account sheet | Primary button, bottom |
| Back | Top bar arrow |

## Dialogs and confirmations, in one place

| Dialog | From | Confirm | Destructive styling | Render |
|---|---|---|---|---|
| New session | Home *Play X*, Rivalry *Start session* | Start | — | `home-new-session`, `rivalry-new-session` |
| Quick game | Home | Start | — | `home-quick-game`, `quick-game`, `quick-game-chosen` |
| Save to a rivalry | Home quick-game row | Save | — | `home-save-game` |
| Change game | Session menu | Save | — | `session-change-game` |
| End this match? | Session menu | End match | No | `session-end-match`, `session-end-match-open-ended` |
| End tonight's session? | Session menu; between-matches panel | End session | No | `session-end-session`, `session-end-session-empty`, `session-next-end-session` |
| Remove *Julian*? | Rivalry overflow | Remove | Amber | `rivalry-remove` |
| Delete this session? | Session detail overflow | Delete | Amber | `detail-delete` |

Not confirmed: undo (menu, panel), every tag toggle, sign out, decline or cancel an invite.

## Overflow menus

| Screen | Items |
|---|---|
| Home (⋮, signed in only) | Sign out |
| Rivalry (⋮) | Remove rival |
| Session detail (⋮, ended sessions only) | Delete session |
| Session (≡) | Last frame tags ×3, Undo last frame, Change game / End match, End session |
