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
14 October 2026, 15:20. It writes raw captures, geometry/colors (`shots.json`) and actual notification
content (`notifications.json`) to `store/build/raw/<locale>/`. Film-only captures run last because they
change data. Without `-PstoreScreenshots`, capture is skipped by ordinary tests/CI.

Settings captures open the index, then Calendar & invitations for `settings-calendar` and Language &
appearance for the theme transition, both through the gear on a tab's bar. Navigate back through the index
before returning to the tab.

`render.mjs` uses headless Chrome to frame captures with Golos Text and captions, render at higher
resolution and downsample. Lock-screen entries use recorded notifications; descriptions copy from
`store/text/<locale>/`.

## Change the listing

| Change | Edit |
|---|---|
| Captions, order, screen choice, tagline/CTA | `store/listing.json`; `film` overrides a caption for video. |
| Highlighted rows / composition | `callout` mark names, `layout: "tilted"`, or `layout: "pair"` with `behind`. Pixel finders handle `status`/`income-card`. |
| Demo data | `DemoTutor.kt`; Russian uses BYN/Belarusian names, English USD. Keep both in the same week (`DemoTutorTest`); select roles like `onNow`/`debtor`, never list positions. |
| New screen / geometry / redrawn text | `StoreScreenshots.tour()` and `capture(...)`'s `marks`/`texts`, then reference in `listing.json`. |
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

Reuse the captures above; Node/Chrome suffice, with no ffmpeg or npm dependencies. Generated files in
`store/build/video/` are uncommitted: H.264/AAC, 60 fps, 42 seconds.

| File | Dimensions | Use |
|---|---|---|
| `klas-<locale>.mp4` | 1920 × 1080 | Play/YouTube |
| `klas-<locale>-vertical.mp4` | 1080 × 1920 | Shorts/Reels/stories |

Play accepts a YouTube URL: upload the landscape file public/unlisted, disable ads, then paste its URL.

The film draws frames by time, with scenes on the music's 2-second bars. Tall captions stay above the
phone. Motion blur blends up to twelve exposures; `JUMPS` prevents blending across swipe releases.
Sound is deterministic synthesis in `sound.js`, with no sampled recordings.

- **Interaction:** `TAPS` reproduces clipped ripples; only swipes show a thumb. Standalone cards/buttons
  also press inward, segments only ripple. Draw feedback inside the affected screen so overlays cover it.
- **Capture workarounds:** cancelled status overlays only the re-opened sheet's status control, avoiding
  changed homework; homework typing is drawn from app strings because its dialog never idles under
  Robolectric. Swipe panels and post-swipe list positions come from actual intermediate captures.
  Reports join `reports`/`film-reports-end` at a matching row; count-up uses captured typography.
  Theme transition uses `film-theme`/`film-theme-dark`.
- **Rendering:** `gpu.js` uses Chrome's experimental html-in-canvas (`CanvasDrawElement`), accumulates
  WebGL exposures and passes the canvas to WebCodecs. Unsupported Chrome or `--software` uses screenshots.
  `encoder.js` encodes; `lib/mp4.mjs` writes the MP4 index first. `lib/chrome.mjs` selects the fastest GPU.
- **GPU constraints:** html-in-canvas uses page order, ignoring depth sorting: do not rely on
  `translateZ` for occlusion. Put the face after the edge; implement the final fade with `#fade`, since
  the stage's own styles are ignored.

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
