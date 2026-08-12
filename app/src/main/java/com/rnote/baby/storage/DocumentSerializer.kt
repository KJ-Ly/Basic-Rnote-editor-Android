package com.rnote.baby.storage

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.rnote.baby.model.InkPoint
import com.rnote.baby.model.PageSize
import com.rnote.baby.model.PaperPattern
import com.rnote.baby.model.PaperStyle
import com.rnote.baby.model.NoteDocument
import com.rnote.baby.model.Stroke
import com.rnote.baby.model.ToolType
import org.json.JSONArray
import org.json.JSONObject

object DocumentSerializer {

    /**
     * Serializes an RnoteDocument object into a clean JSON string for saving.
     */
    fun toJson(document: NoteDocument): String {
        val root = JSONObject()
        root.put("id", document.id)
        root.put("title", document.title)
        root.put("createdAt", document.createdAt)
        root.put("modifiedAt", System.currentTimeMillis())

        // Paper Style
        val paperObj = JSONObject()
        paperObj.put("pattern", document.paperStyle.pattern.name)
        paperObj.put("isDarkMode", document.paperStyle.isDarkMode)
        paperObj.put("dotDensityDpi", document.paperStyle.dotDensityDpi)
        paperObj.put("pageSize", document.paperStyle.pageSize.name)
        root.put("paperStyle", paperObj)

        // Strokes Array
        val strokesArray = JSONArray()
        for (stroke in document.strokes) {
            val strokeObj = JSONObject()
            strokeObj.put("id", stroke.id)
            strokeObj.put("color", stroke.color.toArgb())
            strokeObj.put("width", stroke.strokeWidth.toDouble())
            strokeObj.put("toolType", stroke.toolType.name)
            strokeObj.put("alpha", stroke.alpha.toDouble())

            val pointsArray = JSONArray()
            for (pt in stroke.points) {
                val ptObj = JSONObject()
                ptObj.put("x", pt.x.toDouble())
                ptObj.put("y", pt.y.toDouble())
                ptObj.put("pressure", pt.pressure.toDouble())
                pointsArray.put(ptObj)
            }
            strokeObj.put("points", pointsArray)
            strokesArray.put(strokeObj)
        }
        root.put("strokes", strokesArray)

        return root.toString(2)
    }

    /**
     * Deserializes a saved JSON string back into an RnoteDocument object.
     */
    fun parseJson(jsonString: String): NoteDocument {
        val root = JSONObject(jsonString)
        val id = root.optString("id", "")
        val title = root.optString("title", "Untitled Note")
        val createdAt = root.optLong("createdAt", System.currentTimeMillis())
        val modifiedAt = root.optLong("modifiedAt", System.currentTimeMillis())

        // Paper Style
        var paperStyle = PaperStyle()
        if (root.has("paperStyle")) {
            val paperObj = root.getJSONObject("paperStyle")
            val patternName = paperObj.optString("pattern", PaperPattern.DOTS.name)
            val pattern = try {
                PaperPattern.valueOf(patternName)
            } catch (e: Exception) {
                PaperPattern.DOTS
            }
            val isDarkMode = paperObj.optBoolean("isDarkMode", true)
            val dotDensityDpi = paperObj.optInt("dotDensityDpi", 5)
            val pageSizeName = paperObj.optString("pageSize", PageSize.LETTER.name)
            val pageSize = try {
                PageSize.valueOf(pageSizeName)
            } catch (e: Exception) {
                PageSize.LETTER
            }
            paperStyle = PaperStyle(
                pattern = pattern,
                isDarkMode = isDarkMode,
                dotDensityDpi = dotDensityDpi,
                pageSize = pageSize
            )
        }

        // Strokes
        val strokesList = mutableListOf<Stroke>()
        if (root.has("strokes")) {
            val strokesArray = root.getJSONArray("strokes")
            for (i in 0 until strokesArray.length()) {
                val strokeObj = strokesArray.getJSONObject(i)
                val strokeId = strokeObj.optString("id", "")
                val colorInt = strokeObj.optInt("color", Color.White.toArgb())
                val width = strokeObj.optDouble("width", 6.0).toFloat()
                val toolTypeName = strokeObj.optString("toolType", ToolType.PEN.name)
                val toolType = try {
                    ToolType.valueOf(toolTypeName)
                } catch (e: Exception) {
                    ToolType.PEN
                }
                val alpha = strokeObj.optDouble("alpha", 1.0).toFloat()

                val pointsList = mutableListOf<InkPoint>()
                if (strokeObj.has("points")) {
                    val pointsArray = strokeObj.getJSONArray("points")
                    for (j in 0 until pointsArray.length()) {
                        val ptObj = pointsArray.getJSONObject(j)
                        val x = ptObj.optDouble("x", 0.0).toFloat()
                        val y = ptObj.optDouble("y", 0.0).toFloat()
                        val pressure = ptObj.optDouble("pressure", 1.0).toFloat()
                        val timestamp = ptObj.optLong("timestamp", System.currentTimeMillis())
                        pointsList.add(InkPoint(x, y, pressure))
                    }
                }

                strokesList.add(
                    Stroke(
                        id = strokeId,
                        points = pointsList,
                        color = Color(colorInt),
                        strokeWidth = width,
                        toolType = toolType,
                        alpha = alpha
                    )
                )
            }
        }

        return NoteDocument(
            id = id,
            title = title,
            createdAt = createdAt,
            modifiedAt = modifiedAt,
            paperStyle = paperStyle,
            strokes = strokesList
        )
    }
}
