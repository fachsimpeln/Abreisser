package io.github.fachsimpeln.abreisser.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.fachsimpeln.abreisser.R
import io.github.fachsimpeln.abreisser.model.FlyerState
import io.github.fachsimpeln.abreisser.render.FlyerRenderer
import io.github.fachsimpeln.abreisser.render.PAGE_H
import io.github.fachsimpeln.abreisser.render.PAGE_W

private val ZOOMS = listOf(1f, 1.5f, 2f, 3f)

/** Scrollable preview of all pages, drawn by the same renderer that makes the PDF. */
@Composable
fun PreviewPane(state: FlyerState, renderer: FlyerRenderer, imageVersion: Int, modifier: Modifier = Modifier) {
    var zoom by rememberSaveable { mutableFloatStateOf(1f) }
    Column(modifier.background(MaterialTheme.colorScheme.background)) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.preview), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Segmented(ZOOMS, zoom, { zoom = it }, Modifier) {
                Text(if (it == 1f) stringResource(R.string.zoom_fit) else "${fmt(it)}×", maxLines = 1)
            }
        }
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val pageWidth = (maxWidth - 32.dp) * zoom
            LazyColumn(
                Modifier
                    .fillMaxSize()
                    .horizontalScroll(rememberScrollState()),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(state.page.pages) {
                    Canvas(
                        Modifier
                            .width(pageWidth)
                            .height(pageWidth * (PAGE_H / PAGE_W))
                            .shadow(3.dp),
                    ) {
                        // Capturing imageVersion redraws the page once images have loaded
                        if (imageVersion < 0) return@Canvas
                        drawIntoCanvas { renderer.drawPage(it.nativeCanvas, state, size.width / PAGE_W) }
                    }
                }
            }
        }
    }
}
