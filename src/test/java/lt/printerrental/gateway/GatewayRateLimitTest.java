package lt.printerrental.gateway;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import lt.printerrental.transport.ServiceHttpClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = {
      "IDENTITY_SERVICE_URL=http://identity.test",
      "RENTAL_SERVICE_URL=http://rental.test",
      "DOCUMENT_SERVICE_URL=http://documents.test",
      "NOTIFICATION_SERVICE_URL=http://notifications.test",
      "GATEWAY_RATE_LIMIT_PER_MINUTE=2"
    })
@AutoConfigureMockMvc
class GatewayRateLimitTest {
  @Autowired MockMvc mvc;

  @MockitoBean ServiceHttpClient upstream;

  @Test
  void returnsTooManyRequestsWhenPerIpLimitIsExceededAndExemptsHealth() throws Exception {
    mvc.perform(get("/api/unknown").header("X-Forwarded-For", "203.0.113.10"))
        .andExpect(status().isNotFound());
    mvc.perform(get("/api/unknown").header("X-Forwarded-For", "203.0.113.10"))
        .andExpect(status().isNotFound());
    mvc.perform(get("/api/unknown").header("X-Forwarded-For", "203.0.113.10"))
        .andExpect(status().isTooManyRequests())
        .andExpect(header().string("Retry-After", "60"));

    // Spoofed leading X-Forwarded-For entry cannot bypass the rightmost proxy-appended IP.
    mvc.perform(get("/api/unknown").header("X-Forwarded-For", "198.51.100.99, 203.0.113.10"))
        .andExpect(status().isTooManyRequests())
        .andExpect(header().string("Retry-After", "60"));

    // Independent IP still has its own quota.
    mvc.perform(get("/api/unknown").header("X-Forwarded-For", "203.0.113.11"))
        .andExpect(status().isNotFound());

    // /health is always exempt even for a rate-limited IP.
    mvc.perform(get("/health").header("X-Forwarded-For", "203.0.113.10"))
        .andExpect(status().isOk());
  }
}
