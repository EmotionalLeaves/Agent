# Studio Agent

A personal Android app that helps one artist get work into exhibitions: it protects studio time, documents the work,
writes the paperwork, finds and ranks open calls, builds application packs, runs slow outreach, and handles what comes
after a yes or a no. You make the art and tap approve.

Everything lives on the phone. There is no server. AI, email and GitHub features switch on when you add your own keys in Settings.

## Install

Every push to `main` builds a signed APK with GitHub Actions and publishes it under **Releases**.
Open the newest release on your phone, download the `.apk`, and install it. Later builds install over the old one.

## First run

1. Answer the four setup questions.
2. Allow calendar and notifications if you want studio time booked and quiet reminders.
3. Optional, in More > Settings: an AI key, email with an app password, a GitHub token for backup and the portfolio page.

## Look and feel

Gallery-wall white, graphite text, one cobalt for actions, and masking-tape yellow only for the one thing to do now.
Titles are set in Bricolage Grotesque, everything else in a copy of Atkinson Hyperlegible with a plain zero (renamed Studio Legible, as its licence asks).
Every build photographs the main screens in light, dark and large text and attaches them to the release as `screens.zip`.

## Where things are

- `app/src/main/java/.../domain` rules with no Android in them: career levels, call scoring, week planner, text rules (unit tested)
- `.../work` the engine: daily run, applications, results, outreach, backup, paper documents
- `.../ai` provider layer and prompts
- `.../media` image straightening, colour fix, exports, PDF writer
- `.../net` page fetch, GitHub, email
- `.../ui` screens
- `docs/ROUNDS.md` what each build round delivered and what is still simplified

The signing key in `keystore/` is deliberately not secret, so builds upgrade each other. Replace it with GitHub Secrets before sharing the app.
