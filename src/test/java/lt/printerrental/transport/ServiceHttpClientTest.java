package lt.printerrental.transport;

import static org.assertj.core.api.Assertions.*;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class ServiceHttpClientTest {
  @Test
  void keepsUserJwtSeparateFromGoogleServiceIdentity() throws Exception {
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/api/test",
        exchange -> {
          var text =
              exchange.getRequestMethod()
                  + "|"
                  + exchange.getRequestHeaders().getFirst("Authorization")
                  + "|"
                  + exchange.getRequestHeaders().getFirst("X-Serverless-Authorization")
                  + "|"
                  + exchange.getRequestURI().getRawQuery()
                  + "|"
                  + new String(exchange.getRequestBody().readAllBytes());
          var bytes = text.getBytes();
          exchange.sendResponseHeaders(201, bytes.length);
          exchange.getResponseBody().write(bytes);
          exchange.close();
        });
    server.start();
    try {
      var credentials =
          new ServiceCredentials() {
            public String identityToken(String audience) {
              return "google-token";
            }

            public String accessToken() {
              return null;
            }
          };
      var result =
          new ServiceHttpClient(credentials)
              .send(
                  "http://127.0.0.1:" + server.getAddress().getPort(),
                  "/api/test?size=1",
                  "POST",
                  "body".getBytes(),
                  "text/plain",
                  "Bearer neon-jwt");
      assertThat(result.statusCode()).isEqualTo(201);
      assertThat(new String(result.body()))
          .isEqualTo("POST|Bearer neon-jwt|Bearer google-token|size=1|body");
    } finally {
      server.stop(0);
    }
  }

  @Test
  void normalizesTrailingSlashesAndUsesNoBodyForGet() throws Exception {
    var observed = new AtomicReference<String>();
    var audienceSeen = new AtomicReference<String>();
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/api/test",
        exchange -> {
          var bodyLen = exchange.getRequestBody().readAllBytes().length;
          observed.set(
              exchange.getRequestMethod()
                  + "|"
                  + exchange.getRequestURI().toString()
                  + "|"
                  + exchange.getRequestHeaders().getFirst("Content-Length")
                  + "|"
                  + bodyLen);
          var bytes = "ok".getBytes();
          exchange.sendResponseHeaders(200, bytes.length);
          exchange.getResponseBody().write(bytes);
          exchange.close();
        });
    server.start();
    try {
      var baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
      var credentials =
          new ServiceCredentials() {
            public String identityToken(String audience) {
              audienceSeen.set(audience);
              return null;
            }

            public String accessToken() {
              return null;
            }
          };
      var result =
          new ServiceHttpClient(credentials)
              .send(baseUrl + "///", "/api/test", "GET", null, null, null);
      assertThat(result.statusCode()).isEqualTo(200);
      assertThat(new String(result.body())).isEqualTo("ok");
      assertThat(result.request().uri().toString()).isEqualTo(baseUrl + "/api/test");
      assertThat(result.request().bodyPublisher()).isEmpty();
      assertThat(audienceSeen.get()).isEqualTo(baseUrl);
      assertThat(observed.get()).isEqualTo("GET|/api/test|null|0");
    } finally {
      server.stop(0);
    }
  }

  @Test
  void rejectsUpstreamResponseExceedingMaxSize() throws Exception {
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/api/exact",
        exchange -> {
          var bytes = new byte[1024];
          exchange.sendResponseHeaders(200, bytes.length);
          exchange.getResponseBody().write(bytes);
          exchange.close();
        });
    server.createContext(
        "/api/large",
        exchange -> {
          var bytes = new byte[2048];
          exchange.sendResponseHeaders(200, bytes.length);
          exchange.getResponseBody().write(bytes);
          exchange.close();
        });
    server.start();
    try {
      var baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
      var credentials =
          new ServiceCredentials() {
            public String identityToken(String audience) {
              return null;
            }

            public String accessToken() {
              return null;
            }
          };
      var client = new ServiceHttpClient(credentials, 5, 1024);
      var ok = client.send(baseUrl, "/api/exact", "GET", null, null, null);
      assertThat(ok.statusCode()).isEqualTo(200);
      assertThat(ok.body()).hasSize(1024);
      assertThatThrownBy(() -> client.send(baseUrl, "/api/large", "GET", null, null, null))
          .isInstanceOf(UpstreamUnavailableException.class);
    } finally {
      server.stop(0);
    }
  }
}
