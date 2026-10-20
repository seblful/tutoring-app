package com.seblful.klas.ui.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.seblful.klas.R
import com.seblful.klas.ui.theme.klasColors

/**
 * The lettering, cropped from `art/wordmark_source.png` at the largest size it is drawn, so a screen
 * showing it decodes 0.4 MB rather than the 6 MB artwork around it.
 */
@Composable
fun KlasWordmark(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.ic_wordmark),
        contentDescription = stringResource(R.string.app_name),
        modifier = modifier.padding(Spacing.inline),
        colorFilter = if (klasColors.isDark) ColorFilter.tint(MaterialTheme.colorScheme.onSurface) else null,
    )
}
