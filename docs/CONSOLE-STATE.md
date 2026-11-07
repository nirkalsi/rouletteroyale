# Roulette (nir.com.rouletteroyale, Play Console app 4973368814007510864) - Console state and landmines
Verified live in Play Console (kalsinirch@gmail.com session) on 05.11.2026; reconciled against
pipeline's fresh check 07.11.2026 21:53 IST. Maintainer note for the Roulette Regency resubmission.

## STAGED REPUBLISH LANDMINE (critical for any RL submission)
Publishing overview carries a staged change: "Advanced settings / App availability /
Set app availability to Published".
- Provenance: the Oct 31 21:04 restoration submission (recorded in Instinct todo
  todo-01M64QT8Q1DGQ82T5EFE18VAVA: "set app availability Published... in review"). Google's
  Nov 2 removal (Impersonation) bounced it back to staged state. An authorized-at-the-time
  leftover, not an anomaly.
- The Console submit control is all-or-nothing ("Submit 2 changes for review"); there is NO
  per-row exclusion (the row's More actions offers only "View change").
- Discard on the Advanced settings > App availability page does NOT clear it (radio already
  reads "Published"; Discard goes disabled after click, staged change persists in the overview).
- Consequence: ANY RL submission (including the staged trader declaration and the future
  Roulette Regency resubmission) would bundle this republish-type change. Nir's constraint:
  RL stays hidden until he says otherwise (03.11 06:04 "לבינתיים תסתיר את המשחק"). Do not
  submit anything on RL until this staged change is resolved or Nir explicitly approves the
  availability change.
- Managed publishing on RL is OFF (observed 05.11). Enabling it is still the planned control
  before any resubmission, but it does not remove this staged change from the batch.

## Resubmission gates (all must hold before any RL submit)
1. Nir lifts the keep-hidden constraint.
2. Staged availability landmine resolved (see above).
3. Console sign-in live (expired 03.11; pipeline re-authenticated 07.11 - recover actual
   scope/access when needed, sign-in state may differ per session).
4. Managed-publishing control verified so approved changes stay hidden until Nir chooses.

## Other durable facts
- RL removed by Google 02.11 (Impersonation); Console shows red "App removed" banner.
  Removal email thread 1a19e1fa7ead0670; flagged assets were the old app screenshot and
  featured graphic.
- Trader declaration for RL: SAVED (staged) 05.11 06:10, NOT submitted (blocked by landmine).
- BJ (4974769765787412253) and VP (4975338266818438661) trader declarations APPROVED + PUBLISHED:
  verified live in Console submission activity 05.11 08:23 IST (BJ submission 6 "App Content"
  Nov 05 6:08am = Published; VP submission 6 "App Content" Nov 05 6:09am = Published).
  BJ/VP production listings public (pipeline 07.11 21:53).
- Tic Tac Neon: standing freeze - no Console change ever (Nir 31.10 11:46; main confirmed
  05.11 06:14 no override). TT is unpublished.
- Rebrand package: this branch @ 42d22b5. AAB rouletteroyale-42d22b5-v6-realid.aab in
  nirkalsi/app-release-aabs @ 554b64d (sha256 starts d3503d9bdb4ceff8). jarsigner verified
  (roulette-upload.jks), versionCode 6 / 1.5, label "Roulette Regency", real AdMob IDs only,
  monetization unchanged (coin packs only, no remove-ads offer, no interstitials).
  Store-screenshot candidates are LOCAL Robolectric renders (not device): shot1-bets,
  shot2-spin, shot4-win (1080x1920).
