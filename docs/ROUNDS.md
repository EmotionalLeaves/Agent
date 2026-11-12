# Build rounds: what is in this build

All twelve rounds from the project outline are implemented in this first build. The code compiles and its rule logic is unit tested in CI.
It has not yet been run on a phone, so expect to find and report bugs on first use.

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
