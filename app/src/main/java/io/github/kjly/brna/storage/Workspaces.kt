package io.github.kjly.brna.storage

import android.content.Context
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/**
 * Desktop Rnote's workspaces: folders the browser sidebar can switch between, each with a
 * name and a colour. On Android a folder is a document tree the user picked once and
 * this app keeps the grant for — which Drive gives, so a synced Drive folder works as
 * one. They live in the app's preferences, never in a note.
 */
object Workspaces {

    /** [uri] is the document tree's uri, as the folder picker returned it. */
    data class Workspace(val uri: String, val name: String, val color: Int)

    /**
     * Colours to tell workspaces apart by: Rnote's GNOME palette, one of each hue, in the
     * order new workspaces take them.
     */
    val COLORS = listOf(
        0xFF3584E4.toInt(), // blue
        0xFF33D17A.toInt(), // green
        0xFFF6D32D.toInt(), // yellow
        0xFFFF7800.toInt(), // orange
        0xFFE01B24.toInt(), // red
        0xFF9141AC.toInt(), // purple
        0xFF986A44.toInt(), // brown
        0xFF77767B.toInt()  // grey
    )

    private const val PREFS = "workspaces"
    private const val KEY_LIST = "list"
    private const val KEY_SELECTED = "selected"

    fun load(context: Context): List<Workspace> =
        decode(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_LIST, null))

    fun save(context: Context, workspaces: List<Workspace>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_LIST, encode(workspaces))
            .apply()
    }

    /** The uri of the workspace last shown, if any. */
    fun loadSelected(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_SELECTED, null)

    fun saveSelected(context: Context, uri: String?) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_SELECTED, uri)
            .apply()
    }

    /** The colour a workspace added to [existing] starts with: the first not yet in use. */
    fun nextColor(existing: List<Workspace>): Int =
        COLORS.firstOrNull { c -> existing.none { it.color == c } } ?: COLORS[existing.size % COLORS.size]

    internal fun encode(workspaces: List<Workspace>): String = JsonArray().apply {
        workspaces.forEach { w ->
            add(JsonObject().apply {
                addProperty("uri", w.uri)
                addProperty("name", w.name)
                addProperty("color", w.color)
            })
        }
    }.toString()

    /** Never throws: a list that can't be read is an empty one, not a crash at startup. */
    internal fun decode(json: String?): List<Workspace> = try {
        if (json.isNullOrBlank()) emptyList()
        else JsonParser.parseString(json).asJsonArray.mapNotNull { item ->
            val o = item.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
            val uri = o.get("uri")?.asString ?: return@mapNotNull null
            Workspace(uri, o.get("name")?.asString ?: "Workspace", o.get("color")?.asInt ?: COLORS[0])
        }
    } catch (e: Exception) {
        emptyList()
    }
}
