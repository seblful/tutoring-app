package com.seblful.klas.screenshot

import com.github.takahirom.roborazzi.AndroidComposePreviewTester
import com.github.takahirom.roborazzi.ComposePreviewTester
import com.github.takahirom.roborazzi.ComposePreviewTester.TestParameter.JUnit4TestParameter.AndroidPreviewJUnit4TestParameter
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.ThresholdValidator

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

internal const val ChangeThreshold = 0.005f
