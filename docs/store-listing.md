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
node store/video.mjs                                   # store/build/video/klas-<locale>.mp4
node store/video.mjs --4k                              # 3840 × 2160 instead of 1920 × 1080
node store/video.mjs --locale ru-RU --stills 6.2,7.5   # single frames as PNGs, to check a moment
```

It takes the same captures as the stills (step 1 above) and needs only Node and Chrome: no ffmpeg. Each
language gets a 40-second MP4, 1920 × 1080 at 60 fps (H.264 and AAC), in about four minutes; Chrome
draws in software, which on a laptop beat its discrete GPU by ten times (every frame is read back). Play takes
a video only as a **YouTube link** (Main store listing → Video): upload the file to YouTube, public or
unlisted, with ads off, and paste its URL. The files are not committed: they are tens of megabytes, and
the scripts make the same ones again from the captures.

- **The cut** — `store/film/film.js` draws every frame as a function of its time, so frames can be made
  in any order. The phone moves through poses (`POSES`); the captions are `listing.json`'s, word by
  word; rows lift off the screen at the marks the capture recorded; taps land on them. Scenes start on
  2-second bars, the music's downbeats. The intro types the tagline, the close shows `cta`.
- **The homework being typed** is drawn by the film in the app's own words (`strings.xml`): under
  Robolectric the app's homework dialog never lets Compose go idle, so it cannot be shot. What it
  lands on, the list with the new row, is the real app (`film-homework`).
- **The sound** — `store/film/sound.js` synthesises all of it in Chrome's Web Audio, offline: a
  I–V–vi–IV groove in C with a quieter break under the reminders, and an effect on every cue the cut
  sets — keys, taps, whooshes, notification chimes. Nothing is sampled, so there is no licence to
  clear, and every run sounds the same.
- **Encoding** — `store/film/encoder.js` hands each screenshot to Chrome's WebCodecs encoders;
  `store/lib/mp4.mjs` writes the MP4 with its index first. `node --test store/` runs its tests.

## Guards

Each step refuses rather than writing a bad listing:

- **No amount over 1,000** on any screen (`StoreScreenshots.MAX_AMOUNT`). The capture reads every
  amount on screen and fails if one is larger: the listing shows a private tutor, not a business.
- **Captions fit.** A caption shrinks to at most two lines clear of the phone, and the feature graphic's
  words must stop short of its phone. What still does not fit fails the render: shorten it in `listing.json`.
- **Text fits** Play's character limits, checked before it is copied.
- **The video's words fit.** A caption line that does not fit its column at the smallest size, or a
  tagline too long to type in two seconds, stops the video.
- **The video plays.** Each file is played back in Chrome before it counts: the right size and length,
  with sound.
- The first screen gets three launches to show its lesson «on now». Under Robolectric the schedule's
  minute clock now and then fails to reach a fresh screen; this has not been seen on a device.

## Changing things

- **Captions, order, which screens** — `store/listing.json`; the video reuses its captions, tagline and
  `cta`. Play shows at most 8 phone screenshots;
  spare captures (`student`, `schedule-week`, `schedule-month`, `group`, `settings`, `more`,
  `reports-dark`) can be swapped in by name.
- **Demo data** — `DemoTutor.kt`. Russian uses BYN and Belarusian names for things (ЦТ, +375); English
  uses USD. Keep both languages the same week, so the two listings show the same app.
- **A new screen** — add a step to `StoreScreenshots.tour()`, then reference it from `listing.json`.
- **Description** — `store/text/<locale>/*.txt`. Claim only what the app does; the README's features are
  the reference.
- **The video's timing** — `film.js`: `POSES` for the phone, `CAPTIONS`, `CALLOUTS` and `TAPS` for what
  happens when. A new mark is a `marks` entry on a `capture(...)` in `StoreScreenshots`. The music is
  `sound.js`, its sections at the end of `film.js`.
- **After a UI change**, rerun the steps: the listing is the real app, so it goes stale with it.
  `store/build/` is scratch; `store/listing/` is what gets uploaded.
