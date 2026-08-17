package app.ownplay.player.source.xtream

import app.ownplay.player.source.SourceError
import app.ownplay.player.source.SourceResult
import app.ownplay.player.source.credential.XtreamCredentials
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class XtreamClientTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun validateAccount_parsesAuthenticatedResponseWithoutRetainingCredentials() = runBlocking {
        server.enqueue(
            MockResponse.Builder()
                .body(
                    """
                    {
                      "user_info": {
                        "username": "provider-echoed-user",
                        "password": "provider-echoed-password",
                        "auth": 1,
                        "status": "Active",
                        "exp_date": "1893456000",
                        "is_trial": "0",
                        "max_connections": "2",
                        "allowed_output_formats": ["m3u8", "ts"]
                      },
                      "server_info": {
                        "server_protocol": "https",
                        "timezone": "Europe/Tirane"
                      }
                    }
                    """.trimIndent(),
                )
                .build(),
        )
        val client = XtreamClient(allowCleartext = true)
        val credentials = XtreamCredentials("fixture-user", "fixture-password")

        val result = client.validateAccount(server.url("/").toString(), credentials)

        assertTrue(result is SourceResult.Success)
        val account = (result as SourceResult.Success).value
        assertEquals("Active", account.status)
        assertEquals(2, account.maxConnections)
        assertEquals(false, account.isTrial)
        assertEquals(listOf("m3u8", "ts"), account.allowedOutputFormats)
        assertEquals("Europe/Tirane", account.serverInfo?.timezone)

        val request = server.takeRequest()
        assertEquals("/player_api.php", request.url.encodedPath)
        assertEquals("fixture-user", request.url.queryParameter("username"))
        assertEquals("fixture-password", request.url.queryParameter("password"))
    }

    @Test
    fun validateAccount_mapsAuthFalseToAuthenticationFailure() = runBlocking {
        server.enqueue(
            MockResponse.Builder()
                .body("""{"user_info":{"auth":0}}""")
                .build(),
        )
        val client = XtreamClient(allowCleartext = true)

        val result = client.validateAccount(
            server.url("/").toString(),
            XtreamCredentials("fixture-user", "fixture-password"),
        )

        assertEquals(
            SourceResult.Failure(SourceError.AuthenticationFailed),
            result,
        )
    }

    @Test
    fun getLiveCategories_toleratesStringAndNumericParentIds() = runBlocking {
        server.enqueue(
            MockResponse.Builder()
                .body(
                    """
                    [
                      {"category_id":"10","category_name":"News","parent_id":0},
                      {"category_id":"20","category_name":"Sports","parent_id":"1"}
                    ]
                    """.trimIndent(),
                )
                .build(),
        )
        val client = XtreamClient(allowCleartext = true)

        val result = client.getLiveCategories(
            server.url("/").toString(),
            XtreamCredentials("fixture-user", "fixture-password"),
        )

        assertTrue(result is SourceResult.Success)
        val categories = (result as SourceResult.Success).value
        assertEquals(2, categories.size)
        assertEquals("0", categories[0].parentId)
        assertEquals("1", categories[1].parentId)
        assertEquals(
            "get_live_categories",
            server.takeRequest().url.queryParameter("action"),
        )
    }

    @Test
    fun cleartextIsRejectedBeforeNetworkByDefault() = runBlocking {
        val client = XtreamClient()

        val result = client.validateAccount(
            "http://example.com",
            XtreamCredentials("fixture-user", "fixture-password"),
        )

        assertEquals(
            SourceResult.Failure(SourceError.CleartextTransportRequiresOptIn),
            result,
        )
    }
}
