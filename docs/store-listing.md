# Store listing

Generate listing assets from the real app; rerun after UI changes. `store/build/` is scratch;
`store/listing/<locale>/` holds upload-ready files.

## Regenerate

```bash
./gradlew :app:testDebugUnitTest --tests "*StoreScreenshots*" -PstoreScreenshots
node store/render.mjs
```

`-PstoreLocales=de-DE,ja-JP` captures only those languages, and `render.mjs --locale de-DE` renders only
that one. A full capture takes about a minute per language.

Capture uses the Android project's JDK/SDK setup; rendering needs Node and Chrome (`CHROME=<path>`
for a nonstandard installation). Commit the result and [publish](#publish) it.

Each of the 25 app languages has a folder `store/text/<locale>/`, named by Play's code (`de-DE`, `no-NO`,
`ar`), which holds everything that language says:

| File | Holds | Read by |
|---|---|---|
| `title.txt`, `short_description.txt`, `full_description.txt` | The Play listing's texts | `render.mjs`, `publish.mjs` |
| `captions.json` | Tagline, store-button words (`cta`), and a title and subtitle for each shot in `listing.json`, keyed by `shot`; `film` holds the video's own wording | `render.mjs`, `video.mjs` |
| `demo.json` | The demo tutor: app language, phone locale, currency and `scale` (rates × this, so an hour costs what it does there), subjects, people, homework | `DemoTutor` |
| `video.txt` | The YouTube link Play shows | `publish.mjs` |

`listing.json` holds only the layout every language shares. Every language tells the same week
(`DemoTutor.of`): the same days, times, debts and credit, with names and prices of its own. The phone's
region in `demo.json` sets the dates, money and first day of the week. Arabic is captured right to left;
its swipes go leftward.

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
notification content (`notifications.json`, with the phone's 12- or 24-hour clocks) to
`store/build/raw/<locale>/`. Film-only captures run last
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
| Order, screen choice | `store/listing.json`; `filmOnly` holds shots shown only in the video (calendar sync). |
| Captions, tagline/CTA | `store/text/<locale>/captions.json`; `film` overrides a caption for the video. |
| Highlighted rows / composition | `callout` mark names, `layout: "tilted"`, or `layout: "pair"` with `behind`. Pixel finders handle `status`/`income-card`; drawn screens (`kind: "lockscreen"`/`"homescreen"`) lift `reminder`/`widget`. |
| Demo data | `store/text/<locale>/demo.json`; the shared week is `DemoTutor.of` (`DemoTutorTest` checks every language). Select roles like `onNow`/`debtor`, never list positions. |
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
node store/video.mjs --4k                             # double width/height (not in the release set)
node store/video.mjs --locale ru-RU --stills 6.2,7.5    # inspect specific moments
node store/video.mjs --software                       # screenshot fallback; slower
```

Release set: the plain 1080p files on the GPU, both formats for every language; the wide one goes to
YouTube. Each takes about half a minute.

Reuse the captures above; Node/Chrome suffice, with no ffmpeg or npm dependencies. Generated files in
`store/build/video/` are uncommitted: H.264/AAC, 60 fps, 46 seconds.

Scenes, on 2-second bars: Today (the lesson on now, the one to mark), the schedule swipe that marks it,
who owes, the month's income, the home screen's Today widget with its tick tapped, reminders on the lock
screen, homework swiped and typed, calendar sync, and the dark theme ending on Today.

| File | Dimensions | Use |
|---|---|---|
| `klas-<locale>.mp4` | 1920 × 1080 | YouTube (linked from Play) and sharing the file directly |
| `klas-<locale>-vertical.mp4` | 1080 × 1920 | Shorts/Reels/stories |

Play takes a YouTube link, not a file: `store/youtube.mjs` uploads the wide 1080p file ([Publish](#publish)).

The film draws frames by time, with scenes on the music's 2-second bars. Tall captions stay above the
phone. Motion blur blends up to twelve exposures; `JUMPS` prevents blending across swipe releases.
Sound is deterministic synthesis in `sound.js`, with no sampled recordings.

- **Interaction:** `TAPS` reproduces clipped ripples; only swipes show a thumb. Standalone cards/buttons
  also press inward, segments only ripple. Draw feedback inside the affected screen so overlays cover it.
- **Capture workarounds:** homework typing is drawn from app strings because its dialog never idles under
  Robolectric. Swipe panels and post-swipe list positions come from actual intermediate captures. The
  widget's tick fades `widget-today-ticked` in over `widget-today`.
  Reports join `reports`/`film-reports-end` at a matching row above the docked tab bar (`bar` mark).
  The band from the bar's hairline down stays still while the list scrolls under it. Count-up uses
  captured typography.
  Theme transition uses `film-theme`/`film-theme-dark`, then `today-dark`.
- **Rendering:** `gpu.js` uses Chrome's experimental html-in-canvas (`CanvasDrawElement`), accumulates
  WebGL exposures and passes the canvas to WebCodecs. Unsupported Chrome or `--software` uses screenshots.
  On the GPU the captures are shown blurred by `SOFTEN` (copies in `store/build/film/soft-*`): shrunk
  as they are, their type otherwise came out harsher than in software, which smooths them itself.
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

## Publish

```bash
node store/video.mjs                          # if the video changed
node store/youtube.mjs                        # languages without video.txt; --replace to upload again
git add store/text/*/video.txt && git commit  # the links Play will show
```

Then run **Actions → Store listing** on `main` (tick *validate* to only check), or locally
`PLAY_SERVICE_ACCOUNT_JSON="$(cat key.json)" node store/publish.mjs [--validate]`.

`publish.mjs` sends each language's words from `store/text/<locale>/`, its `video.txt` link, and the graphics in
`store/listing/<locale>/`. Languages without rendered graphics get only words; Play shows them the default
language's graphics. It compares with Play first and sends only what differs. A graphic type (icon,
feature graphic, screenshots) is replaced whole when any image differs, including the screenshots'
order. An unchanged listing creates nothing for review. A missing `video.txt` keeps Play's video, never
clears it. Listing changes go through Play review. Under managed publishing, the edit is saved, and you
send it from Publishing overview. The service account needs the store-listing permission
([releasing](releasing.md#set-up-the-pipeline-once)).

`youtube.mjs` runs on your computer, not in CI. YouTube needs the channel owner signed in, and an
unverified app's sign-in lasts 7 days. It uploads unlisted, embeddable and not made for kids, then reads
the video back. If YouTube made it private, which can happen to uploads from apps that haven't passed
YouTube's API audit, it stops without writing `video.txt`. Play also needs ads off: if the channel is
monetised, turn them off for the video in YouTube Studio. `--replace` leaves the old video on the
channel; delete it once Play shows the new one.

YouTube setup (once), in the Google Cloud project that holds Play's service account:

1. Enable **YouTube Data API v3**.
2. Google Auth Platform: an **External** app in testing, with your Google account as a test user.
3. Clients → create an OAuth client of type **Desktop app**; download its JSON to
   `~/keystores/youtube-client.json`, beside the upload key (or point `YOUTUBE_CLIENT` at it).
4. On the first run, sign in with the account that owns the channel and pick the channel. The sign-in is
   kept beside it in `youtube-token.json`; both are secrets, like the upload key.

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
