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
