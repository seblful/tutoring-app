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

/** The original brand artwork on its light surface, independent of the app theme. */
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
