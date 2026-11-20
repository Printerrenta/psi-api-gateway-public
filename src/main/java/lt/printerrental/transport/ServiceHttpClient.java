package lt.printerrental.transport;

import java.io.InputStream;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.Optional;
import javax.net.ssl.SSLSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ServiceHttpClient {
  private static final Logger log = LoggerFactory.getLogger(ServiceHttpClient.class);
  private static final int DEFAULT_TIMEOUT_SECONDS = 45;
  private static final int DEFAULT_MAX_RESPONSE_BYTES = 10 * 1024 * 1024;

  private final ServiceCredentials credentials;
  private final Duration requestTimeout;
  private final int maxResponseBytes;
  private final HttpClient client =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

  public ServiceHttpClient(ServiceCredentials credentials) {
    this(credentials, DEFAULT_TIMEOUT_SECONDS, DEFAULT_MAX_RESPONSE_BYTES);
  }

  @Autowired
  public ServiceHttpClient(
      ServiceCredentials credentials,
      @Value("${GATEWAY_UPSTREAM_TIMEOUT_SECONDS:45}") int timeoutSeconds) {
    this(credentials, timeoutSeconds, DEFAULT_MAX_RESPONSE_BYTES);
  }

  ServiceHttpClient(ServiceCredentials credentials, int timeoutSeconds, int maxResponseBytes) {
    this.credentials = credentials;
    this.requestTimeout = Duration.ofSeconds(Math.max(1, timeoutSeconds));
    this.maxResponseBytes = Math.max(1, maxResponseBytes);
  }

  public HttpResponse<byte[]> send(
      String serviceUrl,
      String path,
      String method,
      byte[] body,
      String contentType,
      String bearer) {
    var baseUrl = serviceUrl == null ? "" : serviceUrl.replaceAll("/+$", "");
    try {
      var request = HttpRequest.newBuilder(URI.create(baseUrl + path)).timeout(requestTimeout);
      if (contentType != null) request.header("Content-Type", contentType);
      if (bearer != null) request.header("Authorization", bearer);
      var cloud = credentials.identityToken(baseUrl);
      if (cloud != null) request.header("X-Serverless-Authorization", "Bearer " + cloud);
      if ("GET".equalsIgnoreCase(method) && (body == null || body.length == 0)) {
        request.GET();
      } else {
        var bodyPublisher =
            body == null || (body.length == 0 && "HEAD".equalsIgnoreCase(method))
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofByteArray(body);
        request.method(method, bodyPublisher);
      }
      var rawResponse = client.send(request.build(), HttpResponse.BodyHandlers.ofInputStream());
      byte[] responseBody;
      try (var stream = rawResponse.body()) {
        responseBody = stream == null ? new byte[0] : stream.readNBytes(maxResponseBytes + 1);
      }
      if (responseBody.length > maxResponseBytes) {
        log.warn(
            "Upstream response from {} {}{} exceeded max size of {} bytes",
            method,
            baseUrl,
            safePath(path),
            maxResponseBytes);
        throw new UpstreamUnavailableException();
      }
      if (rawResponse.statusCode() >= 500) {
        log.warn(
            "Upstream returned status {} for {} {}{}",
            rawResponse.statusCode(),
            method,
            baseUrl,
            safePath(path));
      }
      return new BufferedHttpResponse(rawResponse, responseBody);
    } catch (UpstreamUnavailableException error) {
      throw error;
    } catch (HttpTimeoutException error) {
      log.warn(
          "Upstream request timed out after {}s for {} {}{}",
          requestTimeout.toSeconds(),
          method,
          baseUrl,
          safePath(path));
      throw new UpstreamUnavailableException();
    } catch (InterruptedException error) {
      Thread.currentThread().interrupt();
      log.warn("Upstream request interrupted for {} {}{}", method, baseUrl, safePath(path));
      throw new UpstreamUnavailableException();
    } catch (Exception error) {
      log.warn(
          "Upstream request failed for {} {}{}: {} ({})",
          method,
          baseUrl,
          safePath(path),
          error.getClass().getSimpleName(),
          error.getMessage());
      throw new UpstreamUnavailableException();
    }
  }

  private static String safePath(String path) {
    if (path == null) return "";
    var query = path.indexOf('?');
    return query >= 0 ? path.substring(0, query) : path;
  }

  private static final class BufferedHttpResponse implements HttpResponse<byte[]> {
    private final HttpResponse<InputStream> delegate;
    private final byte[] body;

    private BufferedHttpResponse(HttpResponse<InputStream> delegate, byte[] body) {
      this.delegate = delegate;
      this.body = body;
    }

    @Override
    public int statusCode() {
      return delegate.statusCode();
    }

    @Override
    public HttpRequest request() {
      return delegate.request();
    }

    @Override
    public Optional<HttpResponse<byte[]>> previousResponse() {
      return Optional.empty();
    }

    @Override
    public HttpHeaders headers() {
      return delegate.headers();
    }

    @Override
    public byte[] body() {
      return body;
    }

    @Override
    public Optional<SSLSession> sslSession() {
      return delegate.sslSession();
    }

    @Override
    public URI uri() {
      return delegate.uri();
    }

    @Override
    public HttpClient.Version version() {
      return delegate.version();
    }
  }
}
