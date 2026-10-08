package edu.kit.datamanager.repo.test.integration;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class VerificationAccessTest {
    @Autowired private MockMvc mvc;

    @Test public void unverifiedAccountCanReachVerificationRoutesWithoutToken() throws Exception {
        mvc.perform(post("/api/v1/auth/verify").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"missing@example.org\",\"code\":\"123456\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/auth/resend-verification").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"missing@example.org\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/auth/change-password").contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"old\",\"newPassword\":\"new\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test public void reviewerReadRouteIsAnonymousButManagementRequiresLogin() throws Exception {
        mvc.perform(get("/api/v1/reviewer/metadata")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/scientific/r1/review-links")
                .contentType(MediaType.APPLICATION_JSON).content("{\"hours\":48}"))
                .andExpect(status().isUnauthorized());
    }

    @Test public void orcidCallbackIsPublicButInitiationRequiresLogin() throws Exception {
        // The browser returns from ORCID without the local JWT header; state guards the callback.
        mvc.perform(get("/api/v1/scientific/orcid/callback").param("state", "invalid"))
                .andExpect(status().isServiceUnavailable()); // OAuth is disabled in the test profile.
        mvc.perform(post("/api/v1/scientific/r1/creators/1/orcid/start"))
                .andExpect(status().isUnauthorized());
    }
}
