# Store listing

Generate listing assets from the real app; rerun after UI changes. `store/build/` is scratch;
`store/listing/<locale>/` holds upload-ready files.

## Regenerate

```bash
./gradlew :app:testDebugUnitTest --tests "*StoreScreenshots*" -PstoreScreenshots
node store/render.mjs
```

Capture uses the Android project's JDK/SDK setup; rendering needs Node and Chrome (`CHROME=<path>`
for a nonstandard installation). Upload per language in Play Console → Store presence → Main store listing.

| Output | Play slot / limits |
|---|---|
| `phoneScreenshots/NN-*.png` | Ordered phone screenshots; 2160 × 3840, 24-bit PNG; at most eight |
| `featureGraphic.png` | 1024 × 500, 24-bit PNG |
| `icon.png` | 512 × 512, 32-bit PNG |
| `title.txt`, `short_description.txt`, `full_description.txt` | App name/short/full description; 30/80/4000 characters |

`StoreScreenshots` runs `MainActivity` under Robolectric with `StoreGraph`, real repositories/database
and `DemoTutor` data. Device clock/locale/calendar/schedulers are replaced; time stops mid-lesson on
14 October 2026, 15:20, with one earlier lesson left unmarked (Today's «Over and not marked») and a backup
two days old (no backup banner). It writes raw captures, geometry/colors (`shots.json`) and actual
notification content (`notifications.json`) to `store/build/raw/<locale>/`. Film-only captures run last
because they change data. Without `-PstoreScreenshots`, capture is skipped by ordinary tests/CI.

The tour opens on Today, as the app does. At the end of the film captures, after the video's swipe,
`StoreWidgets` renders the home-screen widgets the way a launcher does (`WidgetScreenshotTest`) at the
capture's density, over transparency: `widget-today`, `widget-today-ticked` (the lesson on now ticked, as
the video's tap leaves it) and `widget-shortcuts`. That lesson is then marked taught in the data for
`film-today-dark`, the dark Today the video ends on. `HOME` in `store/lib/phone.mjs` places them on the drawn home screen at the sizes
`StoreWidgets` renders.

Swipes press and cross the touch slop in one `performTouchInput` batch: between batches the test clock runs
on, and a press held that long starts long-press picking instead. Drags are bounded and fail with
`_failed.png` rather than hang.

Settings captures open the index, then Calendar & invitations for `settings-calendar` and Language &
appearance for the theme transition, both through the gear on a tab's bar. Navigate back through the index
before returning to the tab.

`render.mjs` uses headless Chrome to frame captures with Golos Text and captions, render at higher
resolution and downsample. Lock-screen entries use recorded notifications; descriptions copy from
`store/text/<locale>/`.

## Change the listing

| Change | Edit |
|---|---|
| Captions, order, screen choice, tagline/CTA | `store/listing.json`; `film` overrides a caption for video; `filmOnly` holds captions shown only in the video (calendar sync). |
| Highlighted rows / composition | `callout` mark names, `layout: "tilted"`, or `layout: "pair"` with `behind`. Pixel finders handle `status`/`income-card`; drawn screens (`kind: "lockscreen"`/`"homescreen"`) lift `reminder`/`widget`. |
| Demo data | `DemoTutor.kt`; Russian uses BYN/Belarusian names, English USD. Keep both in the same week (`DemoTutorTest`); select roles like `onNow`/`debtor`, never list positions. |
| New screen / geometry / redrawn text | `StoreScreenshots.tour()` and `capture(...)`'s `marks`/`texts` (`firsts` for a mark that matches more than once), then reference in `listing.json`. |
| Widgets on the home screen | `StoreWidgets` (sizes, rows) and `HOME`/`homeScreen` in `store/lib/phone.mjs`. |
| Description | `store/text/<locale>/*.txt`; claim only implemented features. |
| Video motion and sound | `store/film/film.js`: `POSES`, `CAPTIONS`, `CALLOUTS`, `TAPS`; music in `sound.js`, sections at the end of `film.js`. |

Keep each still legible small: one lifted callout, a continuous backdrop across the row, and the
strongest screens first (day, taught lesson, debt, income). Keep the feature graphic's center clear
for Play's video button. The video close uses words rather than a custom Play-logo button.

## Promo video

```bash
node store/video.mjs                                  # both formats, each language
node store/video.mjs --format landscape               # Play listing format only
node store/video.mjs --4k                             # double width/height
node store/video.mjs --locale ru-RU --stills 6.2,7.5    # inspect specific moments
node store/video.mjs --software                       # screenshot fallback; slower
```

Release set: `--4k` on the GPU for YouTube (13 min for all four), and `--software` for the 1080p files
(about ten times slower), where the GPU draws the app's type harsher than software does.

Reuse the captures above; Node/Chrome suffice, with no ffmpeg or npm dependencies. Generated files in
`store/build/video/` are uncommitted: H.264/AAC, 60 fps, 46 seconds.

Scenes, on 2-second bars: Today (the lesson on now, the one to mark), the schedule swipe that marks it,
who owes, the month's income, the home screen's Today widget with its tick tapped, reminders on the lock
screen, homework swiped and typed, calendar sync, and the dark theme ending on Today.

| File | Dimensions | Use |
|---|---|---|
| `klas-<locale>.mp4` | 1920 × 1080 | Sharing the file directly |
| `klas-<locale>-vertical.mp4` | 1080 × 1920 | Shorts/Reels/stories |
| `klas-<locale>-4k.mp4` (`--4k`) | 3840 × 2160 | YouTube, linked from Play |

Play accepts a YouTube URL: upload the 4K landscape file public/unlisted, disable ads, then paste its URL.
YouTube streams a 4K upload at a higher bitrate at every size.

The film draws frames by time, with scenes on the music's 2-second bars. Tall captions stay above the
phone. Motion blur blends up to twelve exposures; `JUMPS` prevents blending across swipe releases.
Sound is deterministic synthesis in `sound.js`, with no sampled recordings.

- **Interaction:** `TAPS` reproduces clipped ripples; only swipes show a thumb. Standalone cards/buttons
  also press inward, segments only ripple. Draw feedback inside the affected screen so overlays cover it.
- **Capture workarounds:** homework typing is drawn from app strings because its dialog never idles under
  Robolectric. Swipe panels and post-swipe list positions come from actual intermediate captures. The
  widget's tick fades `widget-today-ticked` in over `widget-today`.
  Reports join `reports`/`film-reports-end` at a matching row above the floating tab bar (`bar` mark).
  The band from the bar's shadow down stays still while the list scrolls under it. Count-up uses
  captured typography.
  Theme transition uses `film-theme`/`film-theme-dark`, then `today-dark`.
- **Rendering:** `gpu.js` uses Chrome's experimental html-in-canvas (`CanvasDrawElement`), accumulates
  WebGL exposures and passes the canvas to WebCodecs. Unsupported Chrome or `--software` uses screenshots.
  `encoder.js` encodes; `lib/mp4.mjs` writes the MP4 index first. `lib/chrome.mjs` asks for the
  high-performance GPU, and `video.mjs` prints the one it got («Drawing on …»): on a two-GPU laptop it
  should name the discrete card. The stills (`render.mjs`) draw on the GPU too: their 4320 × 7680 pages
  with blurred glows took 3 min 39 s for both languages against about 7 in software, matching it to
  under a level on average. `render.mjs --software` draws them without it.
- **GPU constraints:** html-in-canvas uses page order, ignoring depth sorting: do not rely on
  `translateZ` for occlusion. Put the face after the edge; implement the final fade with `#fade`, since
  the stage's own styles are ignored. Don't butt two boxes edge to edge on the screen, such as the status bar
  and a capture. Both edges are antialiased against what lies behind them, and as the phone moves the seam
  blinks. Let one box's colour run over the other instead.

## When generation fails

| Guard | Action |
|---|---|
| Capture amount exceeds `StoreScreenshots.MAX_AMOUNT` (1,000) | Adjust tutor-scale demo data. |
| Caption/feature words collide or contrast falls below 4.5:1 | Shorten `listing.json` text or move the backdrop glow. |
| Description exceeds Play limits | Shorten `store/text/<locale>/` content. |
| Video words collide, or tagline cannot type in two seconds / fit two lines | Shorten the video wording. |
| `GPU_CHECKS` differ from screenshots by mean >3 levels or >1% outlier pixels | Inspect saved GPU/reference frames; check layer ordering/styles or use `--software`. |
| Report captures do not align pixel-for-pixel | Fix captures/join before encoding; do not hide the seam. |
| Playback size/duration/audio check fails | Inspect the output; generation is incomplete. |

Capture retries the first screen up to three launches if Robolectric's minute clock fails to show
“on now”; this has not been observed on devices. Run `node --test store/` for muxer, image comparison,
list-join, timeline-parts and pixel-finder tests.
