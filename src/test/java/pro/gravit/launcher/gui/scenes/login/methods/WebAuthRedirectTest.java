package pro.gravit.launcher.gui.scenes.login.methods;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebAuthRedirectTest {
    @Test
    void validatesRedirectSchemeAuthorityAndPathPrefix() {
        assertTrue(WebAuthMethod.WebAuthOverlay.matchesRedirect("https://login.example/callback", "https://login.example/callback?token=secret"));
        assertTrue(WebAuthMethod.WebAuthOverlay.matchesRedirect("https://login.example/callback", "https://login.example/callback/complete"));
        assertFalse(WebAuthMethod.WebAuthOverlay.matchesRedirect("https://login.example/callback", "http://login.example/callback"));
        assertFalse(WebAuthMethod.WebAuthOverlay.matchesRedirect("https://login.example/callback", "https://login.example.evil/callback"));
        assertFalse(WebAuthMethod.WebAuthOverlay.matchesRedirect("https://login.example/callback", "https://login.example/callback-evil"));
        assertFalse(WebAuthMethod.WebAuthOverlay.matchesRedirect("device:code:user", "https://login.example/callback"));
    }
}
