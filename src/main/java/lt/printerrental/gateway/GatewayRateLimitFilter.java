package lt.printerrental.gateway;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

public class GatewayRateLimitFilter extends OncePerRequestFilter {
  private static final Logger log = LoggerFactory.getLogger(GatewayRateLimitFilter.class);
  private static final long WINDOW_MILLIS = 60_000L;
  private static final int MAX_TRACKED_CLIENTS = 10_000;

  private final int maxRequestsPerMinute;
  private final Clock clock;
  private final ConcurrentHashMap<String, WindowCounter> counters = new ConcurrentHashMap<>();
  private final AtomicLong lastEvictionMillis;

  public GatewayRateLimitFilter(int maxRequestsPerMinute) {
    this(maxRequestsPerMinute, Clock.systemUTC());
  }

  GatewayRateLimitFilter(int maxRequestsPerMinute, Clock clock) {
    this.maxRequestsPerMinute = maxRequestsPerMinute;
    this.clock = clock;
    this.lastEvictionMillis = new AtomicLong(clock.millis());
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return maxRequestsPerMinute <= 0 || "/health".equals(request.getRequestURI());
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    var now = clock.millis();
    evictExpiredIfNeeded(now);

    var clientIp = extractClientIp(request);
    if (!tryAcquire(clientIp, now)) {
      log.warn("Rate limit exceeded for client IP {}", clientIp);
      response.setStatus(429);
      response.setHeader("Retry-After", "60");
      response.setContentType("application/problem+json");
      response
          .getWriter()
          .write(
              "{\"status\":429,\"title\":\"Too Many Requests\",\"detail\":\"Rate limit exceeded; retry later\"}");
      return;
    }

    filterChain.doFilter(request, response);
  }

  static String extractClientIp(HttpServletRequest request) {
    var forwarded = request.getHeader("X-Forwarded-For");
    if (forwarded != null && !forwarded.isBlank()) {
      var parts = forwarded.split(",");
      for (int i = parts.length - 1; i >= 0; i--) {
        var candidate = parts[i].strip();
        if (!candidate.isEmpty()) return candidate;
      }
    }
    var remote = request.getRemoteAddr();
    return remote == null || remote.isBlank() ? "unknown" : remote;
  }

  private void evictExpiredIfNeeded(long now) {
    var last = lastEvictionMillis.get();
    if (now - last >= WINDOW_MILLIS && lastEvictionMillis.compareAndSet(last, now)) {
      var currentWindow = now / WINDOW_MILLIS;
      counters.entrySet().removeIf(entry -> entry.getValue().isExpired(currentWindow));
    }
  }

  private boolean tryAcquire(String clientIp, long now) {
    var currentWindow = now / WINDOW_MILLIS;
    if (counters.size() >= MAX_TRACKED_CLIENTS && !counters.containsKey(clientIp)) {
      counters.entrySet().removeIf(entry -> entry.getValue().isExpired(currentWindow));
      if (counters.size() >= MAX_TRACKED_CLIENTS) {
        counters.entrySet().removeIf(entry -> entry.getValue().currentCount() <= 1);
      }
      if (counters.size() >= MAX_TRACKED_CLIENTS) {
        return false;
      }
    }
    var allowed = new boolean[1];
    counters.compute(
        clientIp,
        (key, existing) -> {
          if (existing == null) {
            allowed[0] = true;
            return new WindowCounter(currentWindow, 1);
          }
          var updated = existing.increment(currentWindow);
          allowed[0] = updated <= maxRequestsPerMinute;
          return existing;
        });
    return allowed[0];
  }

  private static final class WindowCounter {
    private volatile long window;
    private volatile int count;

    private WindowCounter(long window, int count) {
      this.window = window;
      this.count = count;
    }

    private boolean isExpired(long currentWindow) {
      return window < currentWindow;
    }

    private int increment(long currentWindow) {
      if (window != currentWindow) {
        window = currentWindow;
        count = 1;
      } else {
        count++;
      }
      return count;
    }

    private int currentCount() {
      return count;
    }
  }
}
