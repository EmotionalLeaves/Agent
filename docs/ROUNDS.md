# Build rounds: what is in this build

All twelve rounds from the project outline are implemented. Every build runs automated tests that start the real app without a phone:
they open every screen (empty and with data), walk an application from yes to accepted, plan, pause and re-plan a week, and upgrade a
build-1 database. The camera, calendar, email, AI services, GitHub and the web form filler are not covered by those tests and still need a real phone.

| Round | Built | Simplified or left for later |
| --- | --- | --- |
| 1 Core + CI | Kotlin/Compose app, Room database, settings, encrypted keys, signed release workflow, backup zip export and restore | |
| 2 Home + sessions | One next-step card, career level and gate, weekly hours, week planner with calendar booking, session timer, Do Not Disturb, end-of-session note and photo, energy check, wind-down prompt, Ideas, skill sessions, quick-settings tile, home widget | Honest Lockout hand-off not built. Studio playlist not built |
| 3 Capture + catalog | Photo or file import, original kept untouched, colour correction, four-corner straighten and crop, catalog, series, print check, editions, exports, file naming, certificate, print order text, GitHub backup | Corners are placed by hand (no automatic edge detection). Uses the phone's camera app (no in-app level or glare check). JPEG exports only, no TIFF or colour-profile soft proof. Size is typed in, not measured |
| 4 Materials | Interview by voice or text, voice samples, statements, bios, series statements, CV PDF, portfolio PDFs, portfolio web page, checklist, revision limits, park for a week, admin pass | |
| 5 Call intake | Share sheet (link, text, flyer photo), paste, by hand, email-alert senders, page sources, AI extraction, dedupe, trust flags, expiry | No site-specific adapters. Page sources only work for plain HTML pages and only where the site's terms allow |
| 6 Matching | Level filter, hard eligibility rules, fit score with reasons, weekly short list, one stretch call a month, work picker, deadline events | "Odds" uses call tier, not yet your own history |
| 7 Packs | Works, statement fitted to the limit, fit paragraph, objective checklist, images exported to the call's size rules and names, CV, image list | The prospectus is read once at intake; custom questions are answered by hand |
| 8 Email | Send by SMTP with a 10-minute undo, or hand off to the mail app; proof; lock as done; results and replies read over IMAP; fee log and budget | Uses an app password (IMAP/SMTP), not the Gmail API |
| 9 Web forms | In-app browser that stays signed in, heuristic field fill, pack files offered to file pickers, screenshot proof | Fill is a best-effort match on field names; you press the site's submit button |
| 10 Outreach | Venue research from a site, contacts, level gating, drafted email plus follow-up approved together, weekly cap, automatic follow-up, reply sorting, email list, proposal PDF | Venues are added by you (no automatic venue discovery). No calendar feed of openings |
| 11 Results + logistics | Accepted or declined handling, CV line added, checklist (from the email with AI, or a default list), wall labels, price list, print and framing order, ledger, sales | Shipping help is a packing note, not carrier quotes |
| 12 Learning + polish | No-reasons feed the ranking, quarterly report, year export, onboarding, light and dark themes | |

## Second build: flexibility and the walkthrough

| Area | Added |
| --- | --- |
| Your week | Your own studio slots (any day, time, length), pause for a week, two weeks or a month, skip a single block, session history, log a past session, pause the timer |
| Home | "Not today", pin your own next step, your own to-dos, a Guide card, progress folded away by default |
| Your rules | Set your level by hand, change gates and fee caps, show calls from every level, choose what matters most in ranking, short-list size, kinds of call, several formats, inches or centimetres, currency symbol, switch off outreach limits |
| Catalog | Several pictures per piece, tags, search and status filter, rename, resize or delete a series, edit CV lines |
| Writing | Your own documents with any name and length, your own interview questions, standing writing notes, your own banned words, any OpenAI-compatible AI service |
| Applications | Custom questions with drafted answers, reopen a locked application, residencies, grants and other call kinds |
| Guide | Fifteen steps with exact instructions (including getting an AI key, a Gmail app password and a GitHub token), worked out from what is really set up; test buttons for the AI key, email and backup |
| Less at once | Four tabs instead of five, one-time explanations on main screens, Settings split into nine short sections, long forms folded |
| Fixed | Opening from the tile, widget, a notification or a share on a cold start landed on Home instead of the right screen |

## Third build: easier with ADHD and OCD

| Area | Added |
| --- | --- |
| Starting | "Start now" picks the piece and starts the clock in one tap; "Just 5 minutes"; the tile and widget start instantly; a bar that fills to your usual session length; reminders offer Start, In 30 minutes, Not today |
| Small steps | Each piece can be broken into tiny steps; the session screen shows only the next one; your own written finish line and a "call it finished" button |
| Certainty | "Saved" with the time on every editing screen; a done page after each session; Receipts lists everything sent and everything the app did in the background; approved texts lock |
| Calmer | No red warnings; fit shown in words; optional fewer-words mode; a "Done this week" list with no streaks; park an idea straight from Home |
| Settings | A "Focus and calm" section where each of these can be switched |

## Builds 8 to 11: four improvement cycles

| Cycle | Added |
| --- | --- |
| 1 Find bugs first | A crash catcher with one-tap "send the details"; a problem report in Settings, Permissions; tests for image straightening, colour fix and export, backup and restore, and the whole AI pipeline against a stand-in server. Setup now needs only a first name: every other question has a "not sure" or "decide for me" choice. A Plain answers page and tap-to-explain notes cover terms nobody should be expected to know |
| 2 Never lose work | Automatic daily backup to a folder you pick, keeping the newest five; every delete can be put back for 30 seconds |
| 3 Time you can see | A week strip on Home, a finish forecast for each series from your real pace, one focus piece a week |
| 4 Start over | When a piece is marked documented, blanks are filled with safe defaults and listed; sentence starters for every interview question; help for an empty CV |

Still not possible to test without a phone: PDFs, the camera, the calendar, real email, real AI services, GitHub, folder backup, the web form filler.

## Builds 12 to 17: fifteen more cycles

| Cycle | Added |
| --- | --- |
| 1 | Bring in work you have already made: pick several files and each becomes a finished piece with title, size, edition and price filled in. "Make another like this" |
| 2 | Calls are read without an AI key: deadline, fee, link, email, kind and reach are picked out by pattern matching |
| 3 | Statements and bios can be built from your own interview answers and CV facts, with no AI |
| 4 | Getting it printed: five plain steps per piece from file to framed, with costs that go into your records |
| 5 | "Is this series ready to show?": objective checks, nothing to perfect beyond them |
| 6 | Ready-made emails for nine common situations (print lab quote, asking a campus gallery, saying yes, withdrawing, thank you...) |
| 7 | Openings log with a gentle one-a-month count and how to find and survive one |
| 8 | A starter list of four places every campus and town has, each with what to find out |
| 9 | "Where this is going": the five levels, what each opens, and your progress |
| 10 | The screen stays on during a session so the time bar is always visible |
| 11 | A three-minute weekend look-ahead: what you did, one piece for next week, your studio times |
| 12 | A question you have answered before is pre-filled with your earlier answer |
| 13 | A Copy list in the entry-form browser for boxes that Fill misses |
| 14 | Inventory export as a spreadsheet file; pictures in lists are cached so scrolling is smooth |
| 15 | Re-audit: the More menu is grouped into Help, Do and Look up; the Guide gained a printing step |

## UI and UX cycles (builds 18 to 23)

Fifteen passes on how the app looks and feels. No new features.

1. Screen pictures: every build photographs the main screens (light and dark) and attaches `screens.zip` to the release.
2. One palette: warm paper, deep green, a little terracotta, in light and dark.
3. Type: smaller, tighter scale with clear steps between heading, body and note.
4. Cards: white panels with a hairline edge; tappable ones show an arrow; first-visit hints are compact.
5. Calls: each call is a card with fit, time left and fee as small tags.
6. Session: a ring timer and a two-step "I am stopping" flow.
7. Studio: thumbnails and status tags in the list.
8. Piece screen: title first, photographing tips behind a tap.
9. Guide: drawn tick, ring and "do this now" marks instead of text symbols; numbered steps line up.
10. Forms: the microphone sits inside the field; duplicate headings removed; rarely used forms are folded.
11. Empty states: every empty list says what will appear there and offers at most one button.
12. Navigation: the bottom tab stays lit on sub-screens, so it always shows where you are.
13. Motion and touch: folds open smoothly with a turning arrow; a short buzz on start, stop, approve and send.
14. Accessibility: 48dp touch targets, whole-row switches, spoken states for check marks and week dots, named remove buttons, undo on deleted money entries.
15. Dark theme checked on eight screens; small fixes (plural wording, hidden budget when none is set, quieter Home on a fresh install).

## Redesign (builds 24 to 29)

Twenty passes that replace the look rather than adjust it.

1. Palette: gallery-wall white, graphite, pencil grey, one cobalt for actions, masking-tape yellow only for "do this now". Dark version of each.
2. Type: Bricolage Grotesque for titles, Atkinson Hyperlegible (made for easy reading) for everything else. Both bundled, both open-licence (see docs/FONT-LICENSE-*).
3. Buttons: squared-off, 50dp tall, one filled button per screen.
4. Headers: main tabs get a large title and no back arrow; deeper screens get a slim bar.
5. Help: first-visit explanations no longer sit on the page; they wait behind an info button in the header.
6. Lists: tappable items are plain rows with hairlines instead of boxed cards.
7. Bottom bar: flat, hairline on top, the right tab stays lit; hidden during sessions, setup and the saved screen.
8. Movement: a short slide between screens.
9. Home: the next step is a label taped to the wall; "Not today" and "Pin my own" moved into its menu.
10. Home: the week as seven marks with hours; deadlines and the rest as quiet rows underneath.
11. Studio: a gallery wall of pieces, two across, each with a wall label (title, year and size, status).
12. Piece: the picture first, on a white sheet; with no picture yet, the two ways to add one are right there.
13. Session: full screen, nothing else to tap, the current small step taped up under the timer.
14. Saved screen: one large "Saved." and the next step taped up.
15. Setup: five small pages, one question each, with a progress bar.
16. More: a grid of tiles with icons.
17. Calls: title as the way in, no dot separators, shorter level note.
18. Choice chips: capitalised, regular weight unless chosen.
19. Dark mode contrast fixes.
20. Final audit from screenshots of every main screen, light and dark.

## Design cycles 21 to 70 (builds 30 to 35)

1-10. Plain zero in the body face; paper-white fields; framed empty states; Guide and levels drawn as steps on a line; shorter Materials rows; a saved line with a tick.
11-20. Call facts at a glance (deadline, fee, fit) with urgent deadlines in red; editing folded away; the application as five numbered stages (works, words, check, pack, send); tidier All and Applied lists.
21-28. Money as two figures with forms folded; People level as one line and openings as a big count; research button only when AI is on; Settings grouped with icons.
29-36. The week as a schedule sheet; session kinds explained in place; a three-step line on a fresh Home; receipts grouped by month.
37-44. Status messages as a tinted strip; Ideas as a pinboard; Home extras as icon rows; a length meter on documents; screenshots at large text size.
45-50. Buttons grow with text size; session buttons stacked; Admin with icons; Park it appears only with text; dark and large-text pass.

## Overall checks, round 1

1. Text contrast measured for every colour pair; the outline grey darkened to pass 3:1.
2. Compiler warnings: none left.
3. Every icon button has a spoken name.
4. Every delete can be undone (call sources and series were missing it).
5. Proof lines use readable dates, not 2026-11-11.
6. The back gesture steps back through setup instead of leaving it.
7. Every list has an empty state.
8. All screens still open in tests, fresh and with data.
9. Large-text screenshots checked; nothing clips.
10. README describes the look.
