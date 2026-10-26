package lt.printerrental.gateway;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "IDENTITY_SERVICE_URL=http://identity.test",
      "RENTAL_SERVICE_URL=http://rental.test",
      "DOCUMENT_SERVICE_URL=http://documents.test",
      "NOTIFICATION_SERVICE_URL=http://notifications.test"
    })
@AutoConfigureMockMvc
class GatewayContextTest {
  @Autowired MockMvc mvc;
  @org.springframework.boot.test.web.server.LocalServerPort int port;

  @org.springframework.test.context.bean.override.mockito.MockitoBean
  lt.printerrental.transport.ServiceHttpClient upstream;

  @Test
  @SuppressWarnings("unchecked")
  void forwardsMultipartBytesWithoutConsumingTheFile() throws Exception {
    var response =
        (java.net.http.HttpResponse<byte[]>)
            org.mockito.Mockito.mock(java.net.http.HttpResponse.class);
    org.mockito.Mockito.when(response.statusCode()).thenReturn(201);
    org.mockito.Mockito.when(response.body()).thenReturn("{}".getBytes());
    org.mockito.Mockito.when(response.headers())
        .thenReturn(java.net.http.HttpHeaders.of(java.util.Map.of(), (a, b) -> true));
    org.mockito.Mockito.when(
            upstream.send(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any()))
        .thenReturn(response);
    String body =
        "--boundary\r\n"
            + "Content-Disposition: form-data; name=\"file\"; filename=\"test.txt\"\r\n"
            + "Content-Type: text/plain\r\n\r\n"
            + "original file bytes\r\n"
            + "--boundary--\r\n";
    var request =
        java.net.http.HttpRequest.newBuilder(
                java.net.URI.create("http://127.0.0.1:" + port + "/api/contracts/123/attachments"))
            .header("Content-Type", "multipart/form-data; boundary=boundary")
            .header("Authorization", "Bearer neon-token")
            .POST(java.net.http.HttpRequest.BodyPublishers.ofString(body))
            .build();
    var result =
        java.net.http.HttpClient.newHttpClient()
            .send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
    org.assertj.core.api.Assertions.assertThat(result.statusCode()).isEqualTo(201);
    var bytes = org.mockito.ArgumentCaptor.forClass(byte[].class);
    org.mockito.Mockito.verify(upstream)
        .send(
            org.mockito.ArgumentMatchers.eq("http://documents.test"),
            org.mockito.ArgumentMatchers.eq("/api/contracts/123/attachments"),
            org.mockito.ArgumentMatchers.eq("POST"),
            bytes.capture(),
            org.mockito.ArgumentMatchers.startsWith("multipart/form-data"),
            org.mockito.ArgumentMatchers.eq("Bearer neon-token"));
    org.assertj.core.api.Assertions.assertThat(new String(bytes.getValue())).isEqualTo(body);
  }

  @Test
  void startsWithoutDatabaseOrStorageAndProtectsInternalPaths() throws Exception {
    mvc.perform(get("/health")).andExpect(status().isOk());
    mvc.perform(get("/api/unknown")).andExpect(status().isNotFound());
    mvc.perform(get("/internal/events")).andExpect(status().isForbidden());
  }
}
