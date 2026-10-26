package lt.printerrental.gateway;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class GatewayRoutes {
  private final String identity, rental, documents, notifications, metering, billing;

  public GatewayRoutes(
      @Value("${IDENTITY_SERVICE_URL}") String identity,
      @Value("${RENTAL_SERVICE_URL}") String rental,
      @Value("${DOCUMENT_SERVICE_URL}") String documents,
      @Value("${NOTIFICATION_SERVICE_URL}") String notifications,
      @Value("${METERING_SERVICE_URL:}") String metering,
      @Value("${BILLING_SERVICE_URL:}") String billing) {
    this.identity = identity;
    this.rental = rental;
    this.documents = documents;
    this.notifications = notifications;
    this.metering = metering;
    this.billing = billing;
  }

  public String destination(String path) {
    if (path.equals("/api/metering") || path.startsWith("/api/metering/"))
      return metering.isBlank() ? null : metering;
    if (path.equals("/api/billing") || path.startsWith("/api/billing/"))
      return billing.isBlank() ? null : billing;
    if (path.matches("/api/contracts/[^/]+/attachments(?:/.*)?")) return documents;
    if (path.equals("/api/auth") || path.startsWith("/api/auth/") || path.startsWith("/api/admin/"))
      return identity;
    if (path.equals("/api/notifications") || path.startsWith("/api/notifications/"))
      return notifications;
    if (path.equals("/api/repair-requests")
        || path.startsWith("/api/repair-requests/")
        || path.equals("/api/contracts")
        || path.startsWith("/api/contracts/")
        || path.equals("/api/printers")
        || path.startsWith("/api/printers/")
        || path.equals("/api/rental-requests")
        || path.startsWith("/api/rental-requests/")) return rental;
    return null;
  }
}
