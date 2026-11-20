package lt.printerrental.gateway;

import static org.mockito.Mockito.verifyNoInteractions;
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
      "GATEWAY_MAINTENANCE=true"
    })
@AutoConfigureMockMvc
class GatewayMaintenanceTest {
  @Autowired MockMvc mvc;

  @MockitoBean ServiceHttpClient upstream;

  @Test
  void returnsServiceUnavailableWithRetryAfterDuringMaintenance() throws Exception {
    mvc.perform(get("/api/contracts"))
        .andExpect(status().isServiceUnavailable())
        .andExpect(header().string("Retry-After", "60"));
    verifyNoInteractions(upstream);
  }

  @Test
  void keepsHealthAvailableDuringMaintenance() throws Exception {
    mvc.perform(get("/health")).andExpect(status().isOk());
  }
}
