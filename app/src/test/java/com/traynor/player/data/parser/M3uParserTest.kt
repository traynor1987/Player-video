package com.traynor.player.data.parser

import org.junit.Assert.*
import org.junit.Test

class M3uParserTest {
    private val parser = M3uParser()
    @Test fun parsesCommonMetadata() {
        val item = parser.parseEntry("#EXTINF:-1 tvg-id=\"bbc.one\" tvg-name=\"BBC One\" tvg-logo=\"https://img/logo.png\" group-title=\"UK\",BBC One HD", "https://example.test/live/1.m3u8")
        assertNotNull(item); assertEquals("BBC One HD", item?.name); assertEquals("bbc.one", item?.tvgId); assertEquals("UK", item?.group)
    }
    @Test fun rejectsNonHttpStreams() = assertNull(parser.parseEntry("#EXTINF:-1,Unsafe", "file:///private/video.ts"))
    @Test fun acceptsUnquotedName() = assertEquals("Channel", parser.parseEntry("#EXTINF:-1,Channel", "http://localhost/live")?.name)
}
