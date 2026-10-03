package com.seblful.klas.ui.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.seblful.klas.R

/** Draws only the lettering from the original artwork, separately from the symbol. */
@Composable
fun KlasWordmark(modifier: Modifier = Modifier) {
    val artwork = ImageBitmap.imageResource(R.drawable.ic_wordmark_source)
    val lettering = remember(artwork) {
        BitmapPainter(
            image = artwork,
            srcOffset = IntOffset(221, 919),
            srcSize = IntSize(815, 288),
        )
    }
    Image(
        painter = lettering,
        contentDescription = stringResource(R.string.app_name),
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(colorResource(R.color.klas_logo_background))
            .padding(Spacing.inline),
    )
}
