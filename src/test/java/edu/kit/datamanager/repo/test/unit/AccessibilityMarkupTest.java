package edu.kit.datamanager.repo.test.unit;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.*;

/** Guards the shared keyboard-navigation baseline; it is not a WCAG conformance audit. */
public class AccessibilityMarkupTest {
    @Test public void everyEntryPageHasSkipLinkAndFocusableMainLandmark() throws Exception {
        for (String page : List.of("account", "create", "index", "login", "my-datasets",
                "public-resource", "public", "register", "resource", "review-access",
                "reviews", "users", "verify")) {
            String html;
            try (var stream = getClass().getResourceAsStream("/static/" + page + ".html")) {
                assertNotNull(page + " missing", stream);
                html = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            }
            assertTrue(page + " has no skip link", html.contains("href=\"#main-content\""));
            assertTrue(page + " has no focusable main", html.contains("<main id=\"main-content\" tabindex=\"-1\""));
            assertTrue(page + " has no language", html.contains("<html lang=\"es\""));
        }
    }

    @Test public void sharedStylesShowKeyboardFocusAndRespectReducedMotion() throws Exception {
        String css;
        try (var stream = getClass().getResourceAsStream("/static/styles.css")) {
            assertNotNull(stream);
            css = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertTrue(css.contains(".skip-link:focus"));
        assertTrue(css.contains(":focus-visible"));
        assertTrue(css.contains("prefers-reduced-motion:reduce"));
    }
}
