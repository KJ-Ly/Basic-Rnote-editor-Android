package io.github.kjly.brna.model

import java.util.UUID

data class NoteDocument(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "Untitled Note",
    val createdAt: Long = System.currentTimeMillis(),
    val modifiedAt: Long = System.currentTimeMillis(),
    val paperStyle: PaperStyle = PaperStyle(),
    val strokes: List<Stroke> = emptyList(),
    /** Non-stroke elements (text, shapes, bitmaps) from an imported .rnote file.
     *  Read-only for now; preserved on save-back to .rnote. */
    val nativeElements: List<NativeCanvasElement> = emptyList()
)
