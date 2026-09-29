package io.github.kjly.brna.storage

import io.github.kjly.brna.storage.Workspaces.Workspace
import org.junit.Assert.assertEquals
import org.junit.Test

class WorkspacesTest {

    private val drive = "content://com.google.android.apps.docs.storage/tree/acc%3D1%3Bdoc%3Dencoded%3Dabc"
    private val tablet = "content://com.android.externalstorage.documents/tree/primary%3ASchule"

    @Test
    fun `workspaces survive being written out and read back`() {
        val list = listOf(
            Workspace(drive, "Schule – \"Drive\"", Workspaces.COLORS[4]),
            Workspace(tablet, "Tablet", Workspaces.COLORS[0])
        )
        assertEquals(list, Workspaces.decode(Workspaces.encode(list)))
    }

    @Test
    fun `a damaged list reads as empty rather than failing`() {
        assertEquals(emptyList<Workspace>(), Workspaces.decode("{not json"))
        assertEquals(emptyList<Workspace>(), Workspaces.decode(null))
        assertEquals(emptyList<Workspace>(), Workspaces.decode(""))
    }

    @Test
    fun `an entry without a folder is dropped, one without name or colour gets defaults`() {
        val read = Workspaces.decode("""[{"name":"no uri"},{"uri":"$tablet"}]""")
        assertEquals(listOf(Workspace(tablet, "Workspace", Workspaces.COLORS[0])), read)
    }

    @Test
    fun `a new workspace takes the first colour not yet in use`() {
        assertEquals(Workspaces.COLORS[0], Workspaces.nextColor(emptyList()))
        assertEquals(
            Workspaces.COLORS[2],
            Workspaces.nextColor(listOf(Workspace(drive, "a", Workspaces.COLORS[0]), Workspace(tablet, "b", Workspaces.COLORS[1])))
        )
        assertEquals(
            Workspaces.COLORS[0],
            Workspaces.nextColor(listOf(Workspace(drive, "a", Workspaces.COLORS[1])))
        )
    }

    @Test
    fun `with every colour taken they come round again`() {
        val all = Workspaces.COLORS.mapIndexed { i, c -> Workspace("content://x/tree/$i", "$i", c) }
        assertEquals(Workspaces.COLORS[0], Workspaces.nextColor(all))
        assertEquals(Workspaces.COLORS[1], Workspaces.nextColor(all + Workspace("content://x/tree/9", "9", Workspaces.COLORS[0])))
    }
}
