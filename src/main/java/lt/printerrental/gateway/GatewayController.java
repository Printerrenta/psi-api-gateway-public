package lt.printerrental.gateway;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.*;
import lt.printerrental.transport.ServiceHttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
public class GatewayController {
  private static final int MAX_REQUEST_BYTES = 6 * 1024 * 1024;

  private final GatewayRoutes routes;
  private final ServiceHttpClient client;
  private final boolean maintenance;

  public GatewayController(
      GatewayRoutes routes,
      ServiceHttpClient client,
      @Value("${GATEWAY_MAINTENANCE:false}") boolean maintenance) {
    this.routes = routes;
    this.client = client;
    this.maintenance = maintenance;
  }

  @GetMapping("/")
  public Map<String, String> root() {
    return Map.of("service", "printer-rental-gateway", "health", "/health");
  }

  @RequestMapping(
      path = "/api/**",
      method = {
        RequestMethod.GET,
        RequestMethod.POST,
        RequestMethod.PATCH,
        RequestMethod.PUT,
        RequestMethod.DELETE
      })
  public ResponseEntity<byte[]> forward(HttpServletRequest request) throws IOException {
    if (maintenance) return ResponseEntity.status(503).header("Retry-After", "60").build();
    var destination = routes.destination(request.getRequestURI());
    if (destination == null) return ResponseEntity.notFound().build();
    if (request.getContentLengthLong() > MAX_REQUEST_BYTES) return ResponseEntity.status(413).build();
    var rawBody = request.getInputStream().readNBytes(MAX_REQUEST_BYTES + 1);
    if (rawBody.length > MAX_REQUEST_BYTES) return ResponseEntity.status(413).build();
    var method = request.getMethod();
    var body =
        rawBody.length == 0 && ("GET".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method))
            ? null
            : rawBody;
    var path =
        request.getRequestURI()
            + (request.getQueryString() == null ? "" : "?" + request.getQueryString());
    var response =
        client.send(
            destination,
            path,
            method,
            body,
            request.getContentType(),
            request.getHeader("Authorization"));
    var result = ResponseEntity.status(response.statusCode());
    for (var header :
        List.of("Content-Type", "Content-Disposition", "Location", "WWW-Authenticate"))
      response.headers().firstValue(header).ifPresent(v -> result.header(header, v));
    // CORS is owned by the gateway. Do not cache user-specific API responses.
    result.header("Cache-Control", "no-store");
    return result.body(response.body());
  }
}
