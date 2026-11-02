package io.github.fachsimpeln.abreisser.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.fachsimpeln.abreisser.R
import io.github.fachsimpeln.abreisser.model.FlyerState
import io.github.fachsimpeln.abreisser.model.HeaderSettings
import io.github.fachsimpeln.abreisser.model.PageSettings
import io.github.fachsimpeln.abreisser.model.StripItem
import io.github.fachsimpeln.abreisser.model.StripSettings
import io.github.fachsimpeln.abreisser.render.FlyerRenderer
import io.github.fachsimpeln.abreisser.render.ImageStore
import io.github.fachsimpeln.abreisser.render.metrics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

class FlyerViewModel(app: Application) : AndroidViewModel(app) {
    val images = ImageStore(app)
    val renderer = FlyerRenderer(images)
    private val prefs = app.getSharedPreferences("flyer", Context.MODE_PRIVATE)

    var state by mutableStateOf(load())
        private set

    /** Bumped when images finish loading, so previews redraw. */
    var imageVersion by mutableStateOf(0)
        private set

    fun update(transform: (FlyerState) -> FlyerState) {
        val next = transform(state).clamped().withItems()
        if (next == state) return
        state = next
        prefs.edit { putString(KEY, next.toJson().toString()) }
        warmImages()
    }

    fun page(f: (PageSettings) -> PageSettings) = update { it.copy(page = f(it.page)) }

    fun header(f: (HeaderSettings) -> HeaderSettings) = update { it.copy(header = f(it.header)) }

    fun strips(f: (StripSettings) -> StripSettings) = update { it.copy(strips = f(it.strips)) }

    fun item(index: Int, f: (StripItem) -> StripItem) = strips { s ->
        s.copy(items = s.items.mapIndexed { i, it -> if (i == index) f(it) else it })
    }

    /** Copies the picked image into app storage, then stores its path via [set]. */
    fun pickImage(uri: Uri, set: (String) -> Unit) {
        viewModelScope.launch {
            val path = withContext(Dispatchers.IO) { images.import(uri) } ?: return@launch
            set(path)
        }
    }

    fun fillPage() = page { it.copy(rows = maxOf(1, metrics(state).fit)) }

    fun reset() {
        update { defaults(getApplication()) }
    }

    /** Decodes (and recolors) images off the main thread before the preview needs them. */
    private fun warmImages() {
        val s = state
        val wanted = buildList {
            s.header.image?.let { add(it to s.header.imageRecolor) }
            s.strips.image?.let { add(it to s.strips.imageRecolor) }
            s.strips.items.forEach { item -> item.image?.let { add(it to s.strips.imageRecolor) } }
        }
        if (wanted.isEmpty()) return
        viewModelScope.launch {
            withContext(Dispatchers.Default) { wanted.forEach { (p, r) -> images.bitmap(p, r) } }
            imageVersion++
        }
    }

    private fun load(): FlyerState {
        val base = defaults(getApplication())
        val saved = prefs.getString(KEY, null)
        val loaded = try {
            if (saved != null) FlyerState.fromJson(JSONObject(saved), base) else base
        } catch (e: Exception) {
            base
        }
        return loaded.withItems()
    }

    init {
        warmImages()
        viewModelScope.launch(Dispatchers.IO) {
            images.prune(state.imagePaths())
        }
    }

    override fun onCleared() {
        images.prune(state.imagePaths())
    }

    private companion object {
        const val KEY = "state"

        fun defaults(context: Context) = FlyerState(
            header = HeaderSettings(
                title = context.getString(R.string.default_title),
                desc = context.getString(R.string.default_desc),
            ),
            strips = StripSettings(text = context.getString(R.string.default_strip)),
        )

        /** Makes sure there is a per-strip entry for every strip. */
        fun FlyerState.withItems(): FlyerState {
            val items = strips.items
            if (items.size >= strips.count) return this
            return copy(strips = strips.copy(items = items + List(strips.count - items.size) { StripItem() }))
        }
    }
}
