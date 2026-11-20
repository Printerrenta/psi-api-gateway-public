package lt.printerrental.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class GatewayRateLimitFilterTest {
  @Test
  void resetsQuotaInNextMinuteWindow() throws Exception {
    var clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
    var filter = new GatewayRateLimitFilter(1, clock);

    var first = new MockHttpServletResponse();
    filter.doFilter(requestFor("/api/contracts", "192.0.2.5"), first, new MockFilterChain());
    assertThat(first.getStatus()).isEqualTo(200);

    var limited = new MockHttpServletResponse();
    filter.doFilter(requestFor("/api/contracts", "192.0.2.5"), limited, new MockFilterChain());
    assertThat(limited.getStatus()).isEqualTo(429);
    assertThat(limited.getHeader("Retry-After")).isEqualTo("60");

    clock.advanceSeconds(60);

    var nextWindow = new MockHttpServletResponse();
    filter.doFilter(requestFor("/api/contracts", "192.0.2.5"), nextWindow, new MockFilterChain());
    assertThat(nextWindow.getStatus()).isEqualTo(200);
  }

  @Test
  void disablesRateLimitingWhenConfiguredToZero() throws Exception {
    var filter = new GatewayRateLimitFilter(0);
    for (int i = 0; i < 5; i++) {
      var response = new MockHttpServletResponse();
      filter.doFilter(requestFor("/api/contracts", "192.0.2.5"), response, new MockFilterChain());
      assertThat(response.getStatus()).isEqualTo(200);
    }
  }

  @Test
  void extractsRightmostForwardedIpOrRemoteAddr() {
    var direct = requestFor("/api/contracts", "192.0.2.7");
    assertThat(GatewayRateLimitFilter.extractClientIp(direct)).isEqualTo("192.0.2.7");

    var proxied = requestFor("/api/contracts", "10.0.0.1");
    proxied.addHeader("X-Forwarded-For", "198.51.100.1, 203.0.113.42");
    assertThat(GatewayRateLimitFilter.extractClientIp(proxied)).isEqualTo("203.0.113.42");
  }

  private static MockHttpServletRequest requestFor(String uri, String remoteAddr) {
    var request = new MockHttpServletRequest("GET", uri);
    request.setRemoteAddr(remoteAddr);
    return request;
  }

  private static final class MutableClock extends Clock {
    private Instant now;

    private MutableClock(Instant initial) {
      this.now = initial;
    }

    private void advanceSeconds(long seconds) {
      this.now = this.now.plusSeconds(seconds);
    }

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return now;
    }
  }
}
