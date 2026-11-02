package io.github.fachsimpeln.abreisser.model

import org.json.JSONArray
import org.json.JSONObject

enum class Align { LEFT, CENTER, RIGHT, JUSTIFY }
enum class ImagePos { LEFT, TOP, RIGHT }
enum class Direction { UP, DOWN }
enum class StripMode { SAME, INDIVIDUAL }
enum class FontChoice { SANS, SERIF, MONO, HAND }

data class PageSettings(
    val marginX: Float = 8f,
    val marginY: Float = 8f,
    val gap: Float = 6f,
    val rows: Int = 2,
    val pages: Int = 1,
    val cutLines: Boolean = true,
    val outline: Boolean = false,
)

data class HeaderSettings(
    val enabled: Boolean = true,
    val title: String = "Take what you need",
    val desc: String = "Pull off a strip – it is free!",
    val height: Float = 45f,
    val align: Align = Align.CENTER,
    val titleSize: Float = 22f,
    val descSize: Float = 12f,
    /** Absolute path of a copy in the app's private storage. */
    val image: String? = null,
    val imagePos: ImagePos = ImagePos.LEFT,
    val imageSize: Float = 30f,
    val imageBgOn: Boolean = false,
    val imageBg: Int = 0xFF222222.toInt(),
    val imageRecolor: Boolean = false,
)

data class StripItem(
    val text: String = "",
    val link: String = "",
    val image: String? = null,
) {
    val isCustom get() = text.isNotBlank() || link.isNotEmpty() || image != null
}

data class StripSettings(
    val count: Int = 10,
    val length: Float = 70f,
    val direction: Direction = Direction.UP,
    val padding: Float = 1.5f,
    val font: FontChoice = FontChoice.SANS,
    val fontSize: Float = 9f,
    val align: Align = Align.CENTER,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val mode: StripMode = StripMode.SAME,
    val text: String = "A smile for you",
    val link: String = "",
    val image: String? = null,
    val imageSize: Float = 12f,
    val imageBgOn: Boolean = false,
    val imageBg: Int = 0xFF222222.toInt(),
    val imageRecolor: Boolean = false,
    val qrSize: Float = 15f,
    val items: List<StripItem> = emptyList(),
)

/** What a single strip shows once empty per-strip fields fall back to the shared values. */
data class StripContent(val text: String, val link: String, val image: String?)

data class FlyerState(
    val page: PageSettings = PageSettings(),
    val header: HeaderSettings = HeaderSettings(),
    val strips: StripSettings = StripSettings(),
) {
    fun stripContent(i: Int): StripContent {
        val s = strips
        if (s.mode != StripMode.INDIVIDUAL) return StripContent(s.text, s.link, s.image)
        val it = s.items.getOrNull(i) ?: StripItem()
        return StripContent(
            text = it.text.ifEmpty { s.text },
            link = it.link.ifEmpty { s.link },
            image = it.image ?: s.image,
        )
    }

    /** All image files the state refers to. */
    fun imagePaths(): Set<String> =
        (listOf(header.image, strips.image) + strips.items.map { it.image }).filterNotNull().toSet()

    fun toJson(): JSONObject = JSONObject().apply {
        put("page", JSONObject().apply {
            put("marginX", page.marginX.toDouble())
            put("marginY", page.marginY.toDouble())
            put("gap", page.gap.toDouble())
            put("rows", page.rows)
            put("pages", page.pages)
            put("cutLines", page.cutLines)
            put("outline", page.outline)
        })
        put("header", JSONObject().apply {
            put("enabled", header.enabled)
            put("title", header.title)
            put("desc", header.desc)
            put("height", header.height.toDouble())
            put("align", header.align.name.lowercase())
            put("titleSize", header.titleSize.toDouble())
            put("descSize", header.descSize.toDouble())
            put("image", header.image ?: JSONObject.NULL)
            put("imagePos", header.imagePos.name.lowercase())
            put("imageSize", header.imageSize.toDouble())
            put("imageBgOn", header.imageBgOn)
            put("imageBg", colorToHex(header.imageBg))
            put("imageRecolor", header.imageRecolor)
        })
        put("strips", JSONObject().apply {
            put("count", strips.count)
            put("length", strips.length.toDouble())
            put("direction", strips.direction.name.lowercase())
            put("padding", strips.padding.toDouble())
            put("font", strips.font.name.lowercase())
            put("fontSize", strips.fontSize.toDouble())
            put("align", strips.align.name.lowercase())
            put("bold", strips.bold)
            put("italic", strips.italic)
            put("underline", strips.underline)
            put("mode", strips.mode.name.lowercase())
            put("text", strips.text)
            put("link", strips.link)
            put("image", strips.image ?: JSONObject.NULL)
            put("imageSize", strips.imageSize.toDouble())
            put("imageBgOn", strips.imageBgOn)
            put("imageBg", colorToHex(strips.imageBg))
            put("imageRecolor", strips.imageRecolor)
            put("qrSize", strips.qrSize.toDouble())
            put("items", JSONArray().apply {
                strips.items.forEach {
                    put(JSONObject().apply {
                        put("text", it.text)
                        put("link", it.link)
                        put("image", it.image ?: JSONObject.NULL)
                    })
                }
            })
        })
    }

    companion object {
        /**
         * Reads a saved state (or a preset in the web app's JSON format) over [base].
         * Unknown or invalid fields keep the value from [base].
         */
        fun fromJson(json: JSONObject, base: FlyerState = FlyerState()): FlyerState {
            val p = json.optJSONObject("page")
            val h = json.optJSONObject("header")
            val s = json.optJSONObject("strips")
            val page = base.page.let { d ->
                if (p == null) d else d.copy(
                    marginX = p.float("marginX", d.marginX),
                    marginY = p.float("marginY", d.marginY),
                    gap = p.float("gap", d.gap),
                    rows = p.optInt("rows", d.rows),
                    pages = p.optInt("pages", d.pages),
                    cutLines = p.optBoolean("cutLines", d.cutLines),
                    outline = p.optBoolean("outline", d.outline),
                )
            }
            val header = base.header.let { d ->
                if (h == null) d else d.copy(
                    enabled = h.optBoolean("enabled", d.enabled),
                    title = h.optString("title", d.title),
                    desc = h.optString("desc", d.desc),
                    height = h.float("height", d.height),
                    align = h.enum("align", d.align),
                    titleSize = h.float("titleSize", d.titleSize),
                    descSize = h.float("descSize", d.descSize),
                    image = h.path("image", d.image),
                    imagePos = h.enum("imagePos", d.imagePos),
                    imageSize = h.float("imageSize", d.imageSize),
                    imageBgOn = h.optBoolean("imageBgOn", d.imageBgOn),
                    imageBg = h.color("imageBg", d.imageBg),
                    imageRecolor = h.optBoolean("imageRecolor", d.imageRecolor),
                )
            }
            val strips = base.strips.let { d ->
                if (s == null) d else d.copy(
                    count = s.optInt("count", d.count),
                    length = s.float("length", d.length),
                    direction = s.enum("direction", d.direction),
                    padding = s.float("padding", d.padding),
                    font = s.font("font", d.font),
                    fontSize = s.float("fontSize", d.fontSize),
                    align = s.enum("align", d.align),
                    bold = s.optBoolean("bold", d.bold),
                    italic = s.optBoolean("italic", d.italic),
                    underline = s.optBoolean("underline", d.underline),
                    mode = s.enum("mode", d.mode),
                    text = s.optString("text", d.text),
                    link = s.optString("link", d.link),
                    image = s.path("image", d.image),
                    imageSize = s.float("imageSize", d.imageSize),
                    imageBgOn = s.optBoolean("imageBgOn", d.imageBgOn),
                    imageBg = s.color("imageBg", d.imageBg),
                    imageRecolor = s.optBoolean("imageRecolor", d.imageRecolor),
                    qrSize = s.float("qrSize", d.qrSize),
                    items = s.optJSONArray("items")?.let { arr ->
                        (0 until arr.length()).mapNotNull { i ->
                            arr.optJSONObject(i)?.let {
                                StripItem(it.optString("text", ""), it.optString("link", ""), it.path("image", null))
                            }
                        }
                    } ?: d.items,
                )
            }
            return FlyerState(page, header, strips).clamped()
        }
    }

    /** Keeps every value inside the ranges the editor allows. */
    fun clamped(): FlyerState = copy(
        page = page.copy(
            marginX = page.marginX.coerceIn(Limits.margin),
            marginY = page.marginY.coerceIn(Limits.margin),
            gap = page.gap.coerceIn(Limits.gap),
            rows = page.rows.coerceIn(Limits.rows),
            pages = page.pages.coerceIn(Limits.pages),
        ),
        header = header.copy(
            height = header.height.coerceIn(Limits.headerHeight),
            titleSize = header.titleSize.coerceIn(Limits.titleSize),
            descSize = header.descSize.coerceIn(Limits.descSize),
            imageSize = header.imageSize.coerceIn(Limits.headerImage),
        ),
        strips = strips.copy(
            count = strips.count.coerceIn(Limits.stripCount),
            length = strips.length.coerceIn(Limits.stripLength),
            padding = strips.padding.coerceIn(Limits.padding),
            fontSize = strips.fontSize.coerceIn(Limits.fontSize),
            imageSize = strips.imageSize.coerceIn(Limits.stripImage),
            qrSize = strips.qrSize.coerceIn(Limits.qrSize),
        ),
    )
}

/** Allowed ranges, the same as the web app's input limits. */
object Limits {
    val margin = 0f..30f
    val gap = 0f..40f
    val rows = 1..20
    val pages = 1..50
    val headerHeight = 10f..250f
    val titleSize = 6f..72f
    val descSize = 5f..40f
    val headerImage = 5f..190f
    val stripCount = 1..30
    val stripLength = 15f..200f
    val padding = 0f..10f
    val fontSize = 4f..40f
    val stripImage = 2f..100f
    val qrSize = 5f..60f
}

private fun JSONObject.float(key: String, def: Float): Float {
    val v = optDouble(key, Double.NaN)
    return if (v.isNaN()) def else v.toFloat()
}

private fun JSONObject.path(key: String, def: String?): String? {
    if (!has(key)) return def
    if (isNull(key)) return null
    // Data URLs from web presets can't be used as file paths
    return optString(key).takeIf { it.startsWith("/") } ?: def
}

private inline fun <reified E : Enum<E>> JSONObject.enum(key: String, def: E): E {
    val v = optString(key, "").uppercase()
    return enumValues<E>().firstOrNull { it.name == v } ?: def
}

private fun JSONObject.font(key: String, def: FontChoice): FontChoice {
    val v = optString(key, "").lowercase()
    return when {
        v.isEmpty() -> def
        v in FontChoice.entries.map { it.name.lowercase() } -> FontChoice.valueOf(v.uppercase())
        // The web app stores CSS font stacks
        "mono" in v -> FontChoice.MONO
        "cursive" in v || "comic" in v -> FontChoice.HAND
        "serif" in v && "sans" !in v -> FontChoice.SERIF
        else -> FontChoice.SANS
    }
}

private fun JSONObject.color(key: String, def: Int): Int {
    val v = optString(key, "")
    if (!Regex("#[0-9a-fA-F]{6}").matches(v)) return def
    return (0xFF000000 or v.substring(1).toLong(16)).toInt()
}

fun colorToHex(c: Int): String = "#%06x".format(c and 0xFFFFFF)
