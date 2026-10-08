package com.seblful.klas.screenshot

import com.github.takahirom.roborazzi.AndroidComposePreviewTester
import com.github.takahirom.roborazzi.ComposePreviewTester
import com.github.takahirom.roborazzi.ComposePreviewTester.TestParameter.JUnit4TestParameter.AndroidPreviewJUnit4TestParameter
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.ThresholdValidator

/**
 * How every `@Preview` in the app is screenshotted — the generated test (see `roborazzi {}` in
 * `app/build.gradle.kts`) finds the previews and hands each one to this.
 *
 * It is Roborazzi's own tester with one change: the share of pixels a screenshot may differ by before
 * it counts as changed. The default is none, which is right for goldens recorded and verified on one
 * machine and wrong for this project, where they are recorded on the developer's Windows and verified
 * on CI's Linux. Robolectric's native graphics are the same Skia on both, but built per OS, and glyph
 * edges can come out a shade apart — anti-aliasing noise that a zero tolerance would read as every
 * text-bearing component having changed.
 *
 * [ChangeThreshold] is sized to swallow that and nothing a reviewer would care about. A pixel still
 * only counts as "the same" within Roborazzi's default colour distance, and no neighbouring-pixel shift
 * is allowed, so a moved edge, a changed colour or a different padding moves far more than half a
 * percent of the image and fails. The deliberate-failure check in docs/testing.md is how that was
 * confirmed.
 *
 * A parameterless class because the generated test builds it by reflection; delegation because
 * [AndroidComposePreviewTester] is final.
 */
@OptIn(ExperimentalRoborazziApi::class)
class KlasPreviewTester :
    ComposePreviewTester<AndroidPreviewJUnit4TestParameter> by AndroidComposePreviewTester(
        capturer = { parameter ->
            val options = parameter.roborazziOptions
            AndroidComposePreviewTester.DefaultCapturer().capture(
                parameter.copy(
                    roborazziOptions = options.copy(
                        compareOptions = options.compareOptions.copy(
                            resultValidator = ThresholdValidator(ChangeThreshold),
                        ),
                    ),
                ),
            )
        },
    )

/** The share of an image's pixels that may differ (beyond anti-aliasing distance) and still pass: 0.5%. */
internal const val ChangeThreshold = 0.005f
