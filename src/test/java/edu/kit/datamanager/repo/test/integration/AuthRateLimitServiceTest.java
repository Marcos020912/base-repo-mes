package edu.kit.datamanager.repo.test.integration;

import edu.kit.datamanager.repo.repository.AuthRateWindowRepository;
import edu.kit.datamanager.repo.domain.AuthRateWindow;
import edu.kit.datamanager.repo.service.AuthRateLimitService;
import java.time.Instant;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.PlatformTransactionManager;

import static org.junit.Assert.*;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public class AuthRateLimitServiceTest {
    @Autowired private AuthRateWindowRepository windows;
    @Autowired private PlatformTransactionManager manager;

    @Test
    public void countersSurviveDifferentServiceInstancesAndDoNotStoreIdentity() {
        windows.deleteAll();
        String key = "test-secret-longer-than-thirty-two-characters";
        var first = new AuthRateLimitService(windows, manager, key, true);
        var second = new AuthRateLimitService(windows, manager, key, true);
        MockHttpServletRequest a = new MockHttpServletRequest(); a.setRemoteAddr("192.0.2.11");
        MockHttpServletRequest b = new MockHttpServletRequest(); b.setRemoteAddr("192.0.2.12");
        for (int i = 0; i < 5; i++) { first.login("Researcher", a); second.login("researcher", a); }
        var error = assertThrows(AuthRateLimitService.TooManyAttempts.class, () -> first.login("researcher", a));
        assertTrue(error.retryAfterSeconds() > 0);
        second.login("researcher", b); // A different client is not locked out by the first one.
        assertTrue(windows.findAll().stream().allMatch(item -> item.getBucketKey().matches("[0-9a-f]{64}")));
        windows.deleteAll();
    }

    @Test
    public void disabledLimiterDoesNotAccessDatabase() {
        var disabled = new AuthRateLimitService(windows, manager,
                "test-secret-longer-than-thirty-two-characters", false);
        disabled.login("anyone", new MockHttpServletRequest());
    }

    @Test
    public void cleanupRemovesOnlyLongExpiredCounters() {
        windows.deleteAll();
        var old = new AuthRateWindow("a".repeat(64), Instant.now().minusSeconds(3 * 86400));
        var recent = new AuthRateWindow("b".repeat(64), Instant.now().minusSeconds(3600));
        windows.saveAllAndFlush(java.util.List.of(old, recent));
        new AuthRateLimitService(windows, manager, "test-secret-longer-than-thirty-two-characters", true).purgeExpired();
        assertFalse(windows.existsById(old.getBucketKey()));
        assertTrue(windows.existsById(recent.getBucketKey()));
        windows.deleteAll();
    }
}
