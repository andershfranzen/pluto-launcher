package dev.pluto.launcher.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RomMetadataTest {
    @Test fun readsNameAndImageFromAnEmulationStationGamelist() {
        val entries = RomMetadata.gamelist("""
            <?xml version="1.0"?>
            <gameList>
              <game>
                <path>./Spyro - Year of the Dragon (Europe).cue</path>
                <name>Spyro: Year of the Dragon</name>
                <desc>Dragons &amp; eggs</desc>
                <image>./media/boxart/Spyro - Year of the Dragon (Europe).png</image>
              </game>
              <folder><path>./Spyro 3</path><name>Spyro 3</name></folder>
              <game><name>No path</name></game>
            </gameList>
        """.trimIndent())
        assertEquals(1, entries.size)
        assertEquals(RomMetadata.Entry("./Spyro - Year of the Dragon (Europe).cue", "Spyro: Year of the Dragon",
            "./media/boxart/Spyro - Year of the Dragon (Europe).png"), entries.single())
    }

    @Test fun fallsBackToTheThumbnailWhenThereIsNoImageTag() {
        val entry = RomMetadata.gamelist("""
            <game><path>./A.gba</path><name>A</name><thumbnail>./media/thumbs/A.png</thumbnail></game>
        """.trimIndent()).single()
        assertEquals("./media/thumbs/A.png", entry.image)
        assertEquals("A", entry.name)
    }

    @Test fun decodesEntitiesThatFrontEndsEmit() {
        val entry = RomMetadata.gamelist("""
            <game><path>./A &amp; B (USA).nes</path><name>Rock &amp; Roll &#8211; Racer</name></game>
        """.trimIndent()).single()
        assertEquals("./A & B (USA).nes", entry.path)
        assertEquals("Rock & Roll – Racer", entry.name)
    }

    @Test fun resolvesReferencesRelativeToTheGamelistFolder() {
        assertEquals("ps1/spyro.png", RomMetadata.resolve("ps1", "./spyro.png"))
        assertEquals("ps1/media/boxart/spyro.png", RomMetadata.resolve("ps1", "./media/boxart/spyro.png"))
        assertEquals("spyro.png", RomMetadata.resolve("", "./spyro.png"))
        assertEquals("media/spyro.png", RomMetadata.resolve("ps1", "./../media/spyro.png"))
    }

    @Test fun normalizesPathsForLookup() {
        assertEquals("ps1/spyro.cue", RomMetadata.normalize("./PS1//Spyro.CUE"))
        assertEquals("spyro.cue", RomMetadata.normalize("PS1/../spyro.cue"))
    }
}
