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

## Update 16.11.2026 11:44 IST - managed-publishing control investigation (read-only, no mutations)
Verified live as kalsinirch (lease released) + Google docs answer/9859654 and answer/9859751:
- The managed publishing listbox RENDERS on the removed app's Publishing overview with
  "Managed publishing on - You control when approved changes are published" selectable.
  Turn-on NOT attempted (settings change, needs approval). Docs prerequisite "app must already
  be available" leaves engagement on a removed app unverified.
- Staged landmine re-verified: availability row still offers ONLY "View change"; no Save for
  later, no discard. Trader declaration still staged alongside it ("Submit 2 changes").
- Docs: managed publishing holds approved releases/listings/app-content, but its NOT-held list
  includes "Unpublishing your app" - availability-direction changes bypass the hold at least one
  way; the publish direction is undocumented. answer/9859751: a Removed app is reinstated BY a
  policy-compliant update - approval may itself restore availability regardless.
- RL Production track still offers "Create new release" on the removed app (track Inactive,
  last release 5 (1.4)).
Conclusion: the Nov 3 approved submission cannot be executed with a guaranteed-hidden outcome
today. Safe path: (a) approval to turn managed publishing ON, (b) Google Play support case to
clear the staged availability change and confirm hold coverage for reinstatement, (c) then
upload + submit the approved Regency package.

## BJ/VP reconciliation 16.11.2026 (from main's continuity audit)
- Live production = 5 (1.4) on both, i.e. design-overhaul commits (BJ 6861496, VP be3dd18),
  byte-matched to archived AABs 08.11. Deep-redesign v6/1.5 (BJ c664218, VP 92e0d7b) = approved
  redesign+features, implemented, NEVER submitted; no user cancellation on record Nov 1-16.
- Icons shipped Nov 1 via Store Listing submissions (Published). Banner question answered with
  verified test-ad evidence Nov 1 11:54. OPEN before any v6 submission: BJ deck/suit-model
  conflict (todo-01M6AXHE "suits derive from rank" vs commit bd8beb9 "independent per-card
  suits"), full release verification, and an explicit production-submission decision.

## 2026-11-16 unit tests + BJ bannercheck
- Unit suites (deep-redesign, v6 code): BJ 14 classes/46 tests, VP 13 classes/52 tests - all green, 0 fail/error/skip (gradle testDebugUnitTest, junit XML verified; earlier "no results" was chunked runs killed before results write).
- BJ banner check: AdsManager instrumentation hooks + BannerLoadScriptTest added (commit 13f7ae1, deep-redesign). bannercheck APKs (Google sample ad IDs only, dex-verified) archived in app-release-aabs as blackjack21-13f7ae1-bannercheck-{sampleads,androidTest}.apk and handed to main for pipeline Test Lab free-quota run. Result pending.

## 2026-11-16 13:10 IST - BJ banner check PASSED (Test Lab)
- bannerLoadsAfterFirstRound: 1/1 passed, 30.192s, 0 fail/error/skip (XML verified). Medium Phone arm64, API 36, en_US, portrait. Matrix: console.firebase.google.com/project/qa-learning-b9412/testlab/histories/bh.2cc711fc4f3a53cd/matrices/5572787356391564348/details
- Own frame-by-frame review of the 38s video: no banner at launch or during play (attaches only after first completed round, as designed); real round completed (player 21 vs dealer 22, YOU WIN +5); test-ad banner fully painted at bottom from ~24s; "Test Ad" label + "You've loaded a test ad from AdMob" creative; no ad clicks; foreground never left the app. Transient +200 daily-coins toast overlapped the banner top edge at ~30s - cosmetic, self-dismissing.
- Scope: virtual-device SAMPLE-ad load verified. NOT production fill/revenue/physical-device proof.
- Both BJ and VP now have device-verified sample-banner evidence. Pre-v6-submission checks complete: unit suites green (BJ 46, VP 52), v6 AAB artifact verification green (IDs, icons, signing vs live v5), banners verified.

## 2026-11-16 13:29-13:32 IST - BJ/VP v6 SUBMITTED to production (in review)
- Under standing grant (Oct 21 15:05, all games production without waiting; main re-confirmed scope 16.11 13:13 w/ original wamid locator). Pre-checks: live was still 5 (1.4) both apps, no staged changes, managed publishing off both.
- BJ: rebuilt AAB from HEAD 13f7ae1 (adds banner instrumentation hooks vs e55f256), versionCode 6/1.5, real AdMob IDs only (app ~1059265873, banner /4393760242, rewarded /4806939190 - byte-identical to live v5), upload cert DER matches live v5 (84d0e0b5...). Archived blackjack21-13f7ae1-v6-realid.aab (sha256 f37394c5...). Submitted 13:29-13:30, Publishing overview "Changes in review": Production 6 (1.5), Start full rollout (100%).
- VP: existing verified videopoker-92e0d7b-v6-realid.aab (matches HEAD 92e0d7b; cert 18614228... matches live v5). Submitted 13:31-13:32, same "Changes in review" state, full rollout.
- Release notes (both, en-GB, tagged multi-line format): BJ "Completely redesigned casino table. New optional side bets: Perfect Pairs and 21+3. Stability and performance improvements." / VP "Completely redesigned casino experience. Play up to 5 hands at once with multi-hand mode. Stability and performance improvements."
- Console release-notes field quirk: angle-bracket language tags only survive via focus-field value fill with tags on their own lines (Oct 31 recipe); keystroke chunks work but blur must not hit another input mid-dispatch.
- IN REVIEW != LIVE: quick checks then Google review; nothing published yet. RL submission remains HELD (support draft parked with Nir). TT untouched.

## 2026-11-16 14:01 IST - BJ/VP v6 LIVE
- Google review completed ~25-30 min after submission. Both production tracks verified 13:59-14:01 IST: Active, latest release 6 (1.5), 172 countries/regions, full rollout (100%). Publishing overview "Last published on 16 November 2026", nothing in review.
- BJ/VP redesign + sidebet/multihand work is SHIPPED. Review-outcome wake deleted. RL still held (support draft parked); TT untouched.
