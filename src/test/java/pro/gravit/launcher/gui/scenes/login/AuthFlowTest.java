package pro.gravit.launcher.gui.scenes.login;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AuthFlowTest {
    @Test
    void parsesRequestedFactorIndexes() {
        assertEquals(List.of(1, 2), AuthFlow.parseFactorIndexes("auth.require.factor.1.2", 3));
    }

    @Test
    void combinesMoreThanTwoFactorsWithoutMutatingEarlierCredentials() {
        var first = new AuthFlow.LoginAndPasswordResult("user", new pro.gravit.launcher.core.api.method.password.AuthPlainPassword("secret"));
        var second = new AuthFlow.LoginAndPasswordResult(null, new pro.gravit.launcher.core.api.method.password.AuthTotpPassword("123456"));
        var combined = AuthFlow.merge(first, second);
        var third = AuthFlow.merge(combined, second);
        assertEquals(2, ((pro.gravit.launcher.core.api.method.password.AuthChainPassword) combined.password()).list().size());
        assertEquals(3, ((pro.gravit.launcher.core.api.method.password.AuthChainPassword) third.password()).list().size());
        assertEquals("user", third.login());
    }

    @Test
    void rejectsMalformedOrOutOfRangeFactorIndexes() {
        assertThrows(IllegalArgumentException.class, () -> AuthFlow.parseFactorIndexes("auth.require.factor.x", 3));
        assertThrows(IllegalArgumentException.class, () -> AuthFlow.parseFactorIndexes("auth.require.factor.3", 3));
        assertThrows(IllegalArgumentException.class, () -> AuthFlow.parseFactorIndexes("auth.require.factor.", 3));
    }
}
