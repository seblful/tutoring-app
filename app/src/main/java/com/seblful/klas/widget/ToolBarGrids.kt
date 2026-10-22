package com.seblful.klas.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceModifier
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding

/** [items] in a column, [ItemsSpacing] apart, each an equal share of the height. */
@Composable
internal fun SpacedColumn(items: List<@Composable () -> Unit>, modifier: GlanceModifier) {
    Row(modifier = modifier) {
        Column(modifier = GlanceModifier.fillMaxHeight().defaultWeight()) {
            items.forEachIndexed { index, item ->
                Box(
                    modifier = GlanceModifier
                        .padding(
                            top = if (index == 0) 0.dp else ItemsSpacing / 2,
                            bottom = if (index == items.lastIndex) 0.dp else ItemsSpacing / 2,
                        )
                        .fillMaxWidth()
                        .defaultWeight(),
                ) { item() }
            }
        }
    }
}

/** [items] in a row, [ItemsSpacing] apart, each an equal share of the width. */
@Composable
internal fun SpacedRow(items: List<@Composable () -> Unit>, modifier: GlanceModifier) {
    Column(modifier = modifier) {
        Row(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
            items.forEachIndexed { index, item ->
                Box(
                    modifier = GlanceModifier
                        .padding(
                            start = if (index == 0) 0.dp else ItemsSpacing / 2,
                            end = if (index == items.lastIndex) 0.dp else ItemsSpacing / 2,
                        )
                        .fillMaxHeight()
                        .defaultWeight(),
                ) { item() }
            }
        }
    }
}

/** [items] split over two [SpacedRow]s, the first half on top. */
@Composable
internal fun TwoRowGrid(items: List<@Composable () -> Unit>) {
    val middle = items.size / 2
    Column(modifier = GlanceModifier.fillMaxSize()) {
        SpacedRow(
            items.subList(0, middle),
            modifier = GlanceModifier.fillMaxWidth().defaultWeight().padding(bottom = ItemsSpacing / 2),
        )
        SpacedRow(
            items.subList(middle, items.size),
            modifier = GlanceModifier.fillMaxWidth().padding(top = ItemsSpacing / 2).defaultWeight(),
        )
    }
}
