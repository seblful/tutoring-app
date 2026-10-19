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
   at 4× density, with the production graph (`StoreGraph`, a few bindings swapped) and database. `DemoSeeder` fills the database through
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

## How the screenshots look

Play shows a screenshot at a fraction of its size, in a row with the others, so each one is built to be
read small and to sit well beside the next:

- **One thing called out.** The row a caption is about — the lesson on now, the debtor, the month's
  income — lifts off the screen, bigger and shadowed, standing past the phone's edge (`callout` in
  `listing.json`, by mark name; several marks lift as one). Two are found in the capture's pixels rather
  than marked: `status`, the lesson sheet's row of statuses, and `income-card`, the report's whole card
  (`store/lib/find.mjs`).
- **One backdrop across all eight.** The gradient drifts in tone from one screenshot to the next, into
  night under the dark-theme one, and its glows sit on the joins, half on each side, so the row in Play's
  gallery reads as one picture.
- **Two compositions break the row:** `layout: "tilted"` turns the lock screen's phone so its edge shows,
  with the reminder lifted off it; `layout: "pair"` puts the dark theme in front of the light one
  (`behind`).
- **The order is the one people meet:** the day, a lesson marked taught, who owes, the income. Most
  visitors see only the first three.
- **The feature graphic** keeps its words out of its middle: once the listing has a video, Play draws its
  play button there.

## Promo video

```bash
node store/video.mjs                                   # both formats, per language
node store/video.mjs --format landscape                # just the one for the Play listing
node store/video.mjs --4k                              # twice the size each way
node store/video.mjs --locale ru-RU --stills 6.2,7.5   # single frames as PNGs, to check a moment
node store/video.mjs --software                        # screenshots instead of the GPU (slow)
```

It takes the same captures as the stills (step 1 above) and needs only Node and Chrome: no ffmpeg.
Each language gets two MP4s in `store/build/video/` (H.264 and AAC, 60 fps):

| File | Frame | Length | For |
|---|---|---|---|
| `klas-<locale>.mp4` | 1920 × 1080 | 42 s | the Play listing, YouTube |
| `klas-<locale>-vertical.mp4` | 1080 × 1920 | 42 s | Shorts, Reels, stories |

One takes about two minutes on the GPU (see **Drawing** below), against about forty minutes in
software. Play takes a video only as a
**YouTube link** (Main store listing → Video): upload the wide file to YouTube, public or
unlisted, with ads off, and paste its URL. The files are not committed: they are tens of megabytes,
and the scripts make the same ones again from the captures.

- **The cut** — `store/film/film.js` draws every frame as a function of its time, so frames can be made
  in any order. The phone moves through poses (`POSES`); the captions are `listing.json`'s, word by
  word; rows lift off the screen at the marks the capture recorded, set off by
  their shadow alone; taps land on them. Scenes start on 2-second bars, the music's downbeats. The intro types
  the tagline; the close shows `cta` in a white pill — words only, as Google allows its Play logo only
  on its own badge.
- **Taps** are the app's own feedback, not a marker over the video: Material's ripple spreading inside
  what is tapped, cut to its shape, with no dot for the finger — the phone draws none, and the ripple
  already shows where it landed. Only a swipe keeps its thumb: a row sliding off by itself would not
  read as one. A card or button standing on its own (the
  lesson row, the add button) is also pressed in and springs back — a copy of it, shrunk over a patch
  the colour beside it. A segment of a control only ripples: shrunk, it would break the control's
  outline. Each tap is drawn inside the screen it lands on, so the sheet, the dialog or the dark theme
  covers it as they cover the app. `TAPS` in `film.js` (`lift`, and `end` for a control's last segment,
  whose rounded end the mark does not record).
- **No short cut.** A 17-second cut (three pieces of the timeline joined on the beat) was removed, with
  the mapping of times and music through its joins (`cut` in `parts.js`); git history has it.
- **The tall frame** puts every caption above the phone, which stays in the middle and zooms less so it
  never reaches them.
- **What the app does on screen** — the report's total counts up in the app's own type (the capture
  records the text's size, weight and colour) and its list scrolls: two captures of it (`reports`,
  `film-reports-end`) joined into one, at a row no text crosses, after finding to the pixel how far it
  moved. The dark theme spreads from the tap on Settings' «Dark» (`film-theme`, `film-theme-dark`).
- **The lesson scene** — a lesson swiped taught, then the day's last one called off from its sheet. The
  app closes the sheet on the tap, so «Cancelled» is never seen chosen there; the capture opens the sheet
  again on the cancelled lesson (`film-sheet-cancelled`) and the film lays just its status control over
  the sheet as it opened — the rest of that sheet changed with the lesson (its homework moved on), and
  swapped whole it jumped.
- **The swipes** — that lesson, and a piece of homework swiped done before the next is set. The capture books a lesson earlier today
  that was never marked (`DemoTutor.UNMARKED`, booked through what `DemoSeeder.seed` returns; film-only: the listing's shots are taken
  first), then drags each row with a real touch and shoots it twice on the way, just short of the mark
  and past it (`film-swipe-unarmed`, `film-swipe-armed`, and `film-homework-swipe-…`): the panel the
  film slides the row off is the app's own, glyph grown at the mark. Let go, the lesson's row is home at
  once saying «Taught» (`film-swiped`), as the app redraws it rather than springing it back; the
  homework leaves its list, and the rows below rise into the gap by as far as the lists before and after
  (`film-homework-list`, `film-homework-done`) show they moved, found to the pixel.
- **The homework being typed** is drawn by the film in the app's own words (`strings.xml`): under
  Robolectric the app's homework dialog never lets Compose go idle, so it cannot be shot. What it
  lands on, the list with the new row, is the real app (`film-homework`).
- **Motion blur** — a moving frame is blended from up to twelve exposures over a half-open shutter, as
  many as its fastest edge needs; a still frame is one. The phone has depth: its edge, side buttons and
  a glare that slides as it turns.
- **The sound** — `store/film/sound.js` synthesises all of it in Chrome's Web Audio, offline: a
  I–V–vi–IV groove in C with a quieter break under the reminders, and an effect on every cue the film
  sets — soft key clicks, taps, the count's ticks, whooshes, notification chimes. Nothing is
  sampled, so there is no licence to clear, and every run sounds the same.
- **Drawing** — the page is laid out as a web page, but drawn on the GPU with nothing read back
  (`store/film/gpu.js`): Chrome's html-in-canvas (`texElementImage2D`, behind its `CanvasDrawElement`
  flag) puts the stage into a WebGL texture at each exposure, the exposures are added up in a float
  buffer, and the canvas goes straight to the encoder — Chrome's hardware H.264 where the GPU has one.
  Chrome runs on the fastest GPU, with no frame-rate cap (`store/lib/chrome.mjs`). Taking a screenshot
  of every exposure instead, as it first did, was the slow part: a 1080p screenshot took longer than
  drawing and encoding the frame. A Chrome without html-in-canvas, or `--software`, still does that.
- **Encoding** — `store/film/encoder.js` hands each frame to Chrome's WebCodecs encoders (blending the
  screenshots first, in software); `store/lib/mp4.mjs` writes the MP4 with its index first.
  `node --test store/` runs the tests of the muxer, the list join, `parts.js`, the image comparison
  (`compare.mjs`) and the stills' finders (`find.mjs`).

## Guards

Each step refuses rather than writing a bad listing:

- **No amount over 1,000** on any screen (`StoreScreenshots.MAX_AMOUNT`). The capture reads every
  amount on screen and fails if one is larger: the listing shows a private tutor, not a business.
- **Captions fit.** A caption shrinks to at most two lines clear of the phone, and the feature graphic's
  words must stop short of its phone and of the play button's circle in its middle. What still does not
  fit fails the render: shorten it in `listing.json`.
- **Captions can be read.** Each caption's title and subtitle must keep 4.5:1 contrast with the backdrop
  behind them, measured with the words taken away. A glow moved under a caption fails the render.
- **Text fits** Play's character limits, checked before it is copied.
- **The video's words fit.** A caption line that does not fit its column at the smallest size, a
  caption that runs into the phone in the tall frame, or a tagline too long to type in two seconds or
  to set on two lines, stops the video.
- **The GPU draws what a screenshot shows.** html-in-canvas is experimental, so before each video three
  of its frames are held against screenshots of the same moments (`GPU_CHECKS` in `video.mjs`): off by
  more than 3 levels on average, or 1% of pixels far out, stops the video and saves the frame drawn
  and the screenshot. Anti-aliasing along edges differs by about one. html-in-canvas paints in page
  order, with no sorting by depth, so nothing may rely on `translateZ` to hide behind a later element:
  the phone's face is its own layer after the edge stacked behind it. It also ignores the stage's own
  styles, so the fade to black at the end is a layer over the stage (`#fade`), not its opacity.
- **No frame blends across a jump.** A swiped row let go is home (or gone) in one frame, as in the app;
  motion blur stops at those moments (`JUMPS` in `film.js`), or the frame shows the row in both places.
- **The scrolled list lines up.** The two captures of the report must overlap and match to the pixel
  once shifted, or the video stops rather than show a seam.
- **The video plays.** Each file is played back in Chrome before it counts: the right size and length,
  with sound.
- The first screen gets three launches to show its lesson «on now». Under Robolectric the schedule's
  minute clock now and then fails to reach a fresh screen; this has not been seen on a device.

## Changing things

- **Captions, order, which screens** — `store/listing.json`; the video reuses its captions, tagline and
  `cta` — a caption's `film` is its wording in the video instead, where the video shows more than the
  still (the lesson scene swipes as well as taps). `callout`, `layout` and `behind` shape each screenshot (above). Play shows at most 8 phone
  screenshots;
  spare captures (`student`, `schedule-week`, `schedule-month`, `group`, `settings`, `more`,
  `reports-dark`) can be swapped in by name.
- **Demo data** — `DemoTutor.kt`. Russian uses BYN and Belarusian names for things (ЦТ, +375); English
  uses USD. Keep both languages the same week, so the two listings show the same app; `DemoTutorTest`
  holds them to it, and to the moments the film relies on (a lesson on now, the day's last one still to
  come, a free gap for the unmarked one). Who plays a part is asked of the tutor (`onNow`, `debtor`,
  `lastToday`, …), never picked by position in a list.
- **A new screen** — add a step to `StoreScreenshots.tour()`, then reference it from `listing.json`.
- **Description** — `store/text/<locale>/*.txt`. Claim only what the app does; the README's features are
  the reference.
- **The video's timing** — `film.js`: `POSES` for the phone, `CAPTIONS`, `CALLOUTS` and `TAPS` for what
  happens when. A new mark is a `marks` entry on a `capture(...)` in
  `StoreScreenshots`, a redrawn text a `texts` one. The music is `sound.js`, its sections at the end of
  `film.js`.
- **The close** — words only. Google allows the Play logo only on its own badge; anything else with it
  (a button of our own, say) needs its brand team's approval first.
- **After a UI change**, rerun the steps: the listing is the real app, so it goes stale with it.
  `store/build/` is scratch; `store/listing/` is what gets uploaded.
