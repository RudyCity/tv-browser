package io.rudycity.tvbrowser;

import org.junit.Test;
import static org.junit.Assert.*;

public class UrlToolsTest {
    @Test public void acceptsHttpsAndLanHttp() {
        assertEquals("https://example.com", UrlTools.navigate("example.com"));
        assertEquals("http://192.168.1.1:8080/", UrlTools.navigate("http://192.168.1.1:8080/"));
        assertTrue(UrlTools.isWebUrl("https://example.com/a?q=x"));
    }
    @Test public void turnsSearchWordsIntoQuery() {
        assertEquals("https://www.google.com/search?q=hello+tv", UrlTools.navigate("hello tv"));
        assertEquals("https://www.google.com/search?q=weather", UrlTools.navigate("weather"));
        assertNull(UrlTools.navigate("  "));
    }
    @Test public void malformedExplicitWebAddressesNeverBecomeSearchQueries() {
        for (String address : new String[]{
                "https://example.com/my secret",
                "https://example.com/?auth=private token",
                "https://alice:secret@example.com/private",
                "http:example.com",
                "https://example.com/%oops",
                "https://example.com/a\\nb"
        }) {
            try {
                String target = UrlTools.navigate(address);
                fail("Must not send explicit malformed address to search: " + target);
            } catch (IllegalArgumentException expected) {
                assertEquals("Invalid HTTP/HTTPS address", expected.getMessage());
            }
        }
        // Searching for text which merely mentions a URL is still allowed.
        assertEquals("https://www.google.com/search?q=search+https%3A%2F%2Fexample.com",
                UrlTools.navigate("search https://example.com"));
    }

    @Test public void rejectsNonBrowserSchemes() {
        for (String url : new String[]{"javascript:alert(1)", "file:///etc/passwd", "intent://example"}) {
            try {
                UrlTools.navigate(url);
                fail("Should reject " + url);
            } catch (IllegalArgumentException expected) {
                // Correct.
            }
        }
        assertFalse(UrlTools.isWebUrl("https://alice:secret@example.com"));
        assertFalse(UrlTools.isWebUrl("file:///private"));
    }
}
