package com.seblful.klas.ui.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import com.seblful.klas.R

/**
 * The brand mark on its light tile — the app's primary on its primary container, in both themes: the
 * launcher icon and the splash use the same pair (`klas_logo_background`), so the mark is the app's
 * indigo wherever it appears rather than an azure of its own.
 *
 * `ic_logo` is 384 px: the most it is drawn at (96 dp) on the densest screens. It is decoded on the main
 * thread as the screen composes, so pixels past that are decoded only to be thrown away.
 */
@Composable
fun KlasLogo(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.ic_logo),
        contentDescription = null,
        modifier = modifier
            .clip(RoundedCornerShape(percent = 25))
            .background(colorResource(R.color.klas_logo_background)),
    )
}
