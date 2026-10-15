# Store listing

How the Google Play listing — screenshots, feature graphic, icon, text and promo video — is made, and how
to change it.

## Regenerate

```bash
./gradlew :app:testDebugUnitTest --tests "*StoreScreenshots*" -PstoreScreenshots   # 1. capture the app
node store/render.mjs                                                              # 2. compose the listing
```

Step 1 needs nothing but the JDK; step 2 needs Node and Google Chrome (`CHROME=<path>` if it is not in
a standard place). The results land in `store/listing/<locale>/`, ready to upload in Play Console →
Store presence → Main store listing (per language):

| File | Play slot | Format |
|---|---|---|
| `phoneScreenshots/NN-*.png` | Phone screenshots, in file order | 2160 × 3840, 24-bit PNG |
| `featureGraphic.png` | Feature graphic | 1024 × 500, 24-bit PNG |
| `icon.png` | App icon | 512 × 512, 32-bit PNG |
| `title.txt`, `short_description.txt`, `full_description.txt` | App name, short and full description | ≤ 30, 80, 4000 characters |

## How it works

1. **Capture** — `app/src/test/.../store/StoreScreenshots` runs the real `MainActivity` under Robolectric
   at 4× density, with the production Hilt graph and database. `DemoSeeder` fills the database through
   the repositories with `DemoTutor`: a tutor's term since July, one version per language. It then taps
   through the app and saves each screen to `store/build/raw/<locale>/`. Alongside the screens it writes
   `shots.json` (each screen's top and bottom edge colours, and where the rows and buttons the video
   zooms to or taps are) and `notifications.json`. The second holds what the app's own
   `AndroidNotifier` posted for the day's reminder, the morning agenda and the debt summary. Last come
   the video's own moments (`film-*`), which change the books and so are shot after everything else.
   Only the clock, the phone's locale, the device calendar and the WorkManager schedulers are replaced.
   The clock is stopped on Wednesday 14 October 2026, 15:20, mid-lesson.
   Without `-PstoreScreenshots` the test skips itself, so the unit suite and CI never run it.
2. **Compose** — `store/render.mjs` drives one headless Chrome over the DevTools protocol. Each
   capture goes into a phone frame under a caption from `store/listing.json`, with the app's Golos Text font.
   A `lockscreen` entry draws the recorded notifications on a lock screen instead. Every page is drawn
   at 2× (the fixed-size graphics at 4×) and averaged down for smooth text. It is then re-encoded in the
   format Play asks for, and the text in `store/text/<locale>/` is copied beside it.

## Promo video

```bash
node store/video.mjs                                   # every format and cut, per language
node store/video.mjs --format landscape --cut full     # just the one for the Play listing
node store/video.mjs --4k                              # twice the size each way
node store/video.mjs --locale ru-RU --stills 6.2,7.5   # single frames as PNGs, to check a moment
```

It takes the same captures as the stills (step 1 above) and needs only Node and Chrome: no ffmpeg.
Each language gets four MP4s in `store/build/video/` (H.264 and AAC, 60 fps):

| File | Frame | Length | For |
|---|---|---|---|
| `klas-<locale>.mp4` | 1920 × 1080 | 40 s | the Play listing, YouTube |
| `klas-<locale>-15s.mp4` | 1920 × 1080 | 15 s | ads, posts |
| `klas-<locale>-vertical.mp4` | 1080 × 1920 | 40 s | Shorts, Reels, stories |
| `klas-<locale>-vertical-15s.mp4` | 1080 × 1920 | 15 s | the same, short |

A 40-second one takes about ten minutes, a short one four. Chrome draws in software, which on a
laptop beat its discrete GPU by ten times (every frame is read back). Play takes a video only as a
**YouTube link** (Main store listing → Video): upload the wide 40-second file to YouTube, public or
unlisted, with ads off, and paste its URL. The files are not committed: they are tens of megabytes,
and the scripts make the same ones again from the captures.

- **The cut** — `store/film/film.js` draws every frame as a function of its time, so frames can be made
  in any order. The phone moves through poses (`POSES`); the captions are `listing.json`'s, word by
  word; rows lift off the screen at the marks the capture recorded; taps land on them. Scenes start on
  2-second bars, the music's downbeats. The intro types the tagline; the close shows Google's own
  Play badge (`store/film/badges/`, as downloaded from Google's badge page, one per language).
- **The short cut** is three pieces of the same timeline (`CUTS` in `film.js`): the lesson marked
  taught, the reminders, the close. Each piece starts a whole number of bars from where it sat, so the
  music keeps its beat; `store/film/parts.js` maps the times and the music's sections.
- **The tall frame** puts every caption above the phone, which stays in the middle and zooms less so it
  never reaches them.
- **What the app does on screen** — the report's total counts up in the app's own type (the capture
  records the text's size, weight and colour) and its list scrolls: two captures of it (`reports`,
  `film-reports-end`) joined into one, at a row no text crosses, after finding to the pixel how far it
  moved. The dark theme spreads from the tap on Settings' «Dark» (`film-theme`, `film-theme-dark`).
- **The homework being typed** is drawn by the film in the app's own words (`strings.xml`): under
  Robolectric the app's homework dialog never lets Compose go idle, so it cannot be shot. What it
  lands on, the list with the new row, is the real app (`film-homework`).
- **Motion blur** — a moving frame is blended from up to twelve exposures over a half-open shutter, as
  many as its fastest edge needs; a still frame is one. The phone has depth: its edge, side buttons and
  a glare that slides as it turns.
- **The sound** — `store/film/sound.js` synthesises all of it in Chrome's Web Audio, offline: a
  I–V–vi–IV groove in C with a quieter break under the reminders, and an effect on every cue the cut
  sets — mechanical-keyboard keys, taps, the count's ticks, whooshes, notification chimes. Nothing is
  sampled, so there is no licence to clear, and every run sounds the same.
- **Encoding** — `store/film/encoder.js` blends each frame's screenshots and hands them to Chrome's
  WebCodecs encoders; `store/lib/mp4.mjs` writes the MP4 with its index first. `node --test store/`
  runs the tests of the muxer, the list join and `parts.js`.

## Guards

Each step refuses rather than writing a bad listing:

- **No amount over 1,000** on any screen (`StoreScreenshots.MAX_AMOUNT`). The capture reads every
  amount on screen and fails if one is larger: the listing shows a private tutor, not a business.
- **Captions fit.** A caption shrinks to at most two lines clear of the phone, and the feature graphic's
  words must stop short of its phone. What still does not fit fails the render: shorten it in `listing.json`.
- **Text fits** Play's character limits, checked before it is copied.
- **The video's words fit.** A caption line that does not fit its column at the smallest size, a
  caption that runs into the phone in the tall frame, or a tagline too long to type in two seconds or
  to set on two lines, stops the video.
- **The scrolled list lines up.** The two captures of the report must overlap and match to the pixel
  once shifted, or the video stops rather than show a seam.
- **The video plays.** Each file is played back in Chrome before it counts: the right size and length,
  with sound.
- The first screen gets three launches to show its lesson «on now». Under Robolectric the schedule's
  minute clock now and then fails to reach a fresh screen; this has not been seen on a device.

## Changing things

- **Captions, order, which screens** — `store/listing.json`; the video reuses its captions and
  tagline. Play shows at most 8 phone screenshots;
  spare captures (`student`, `schedule-week`, `schedule-month`, `group`, `settings`, `more`,
  `reports-dark`) can be swapped in by name.
- **Demo data** — `DemoTutor.kt`. Russian uses BYN and Belarusian names for things (ЦТ, +375); English
  uses USD. Keep both languages the same week, so the two listings show the same app.
- **A new screen** — add a step to `StoreScreenshots.tour()`, then reference it from `listing.json`.
- **Description** — `store/text/<locale>/*.txt`. Claim only what the app does; the README's features are
  the reference.
- **The video's timing** — `film.js`: `POSES` for the phone, `CAPTIONS`, `CALLOUTS` and `TAPS` for what
  happens when, `CUTS` for the short cut. A new mark is a `marks` entry on a `capture(...)` in
  `StoreScreenshots`, a redrawn text a `texts` one. The music is `sound.js`, its sections at the end of
  `film.js`.
- **The badge** — Google's rules: use the badge as given, in the language of the video, with a quarter
  of its height clear around it. A TV advert or a campaign past a million impressions needs Google
  Play's brand team to approve it first.
- **After a UI change**, rerun the steps: the listing is the real app, so it goes stale with it.
  `store/build/` is scratch; `store/listing/` is what gets uploaded.
