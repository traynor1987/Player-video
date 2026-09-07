package com.traynor.player.data.network

import org.junit.Assert.assertEquals
import org.junit.Test

class XtreamUrlsTest {
    @Test fun acceptsABareServerUrl() = assertEquals("https://tv.example:8080", XtreamUrls.normaliseServer("https://tv.example:8080/"))
    @Test fun removesAPastedPlayerApiPath() = assertEquals("https://tv.example:8080", XtreamUrls.normaliseServer("https://tv.example:8080/player_api.php?username=private&password=private"))
    @Test fun acceptsServerWithoutScheme() = assertEquals("https://tv.example:8080", XtreamUrls.normaliseServer("tv.example:8080"))
}
