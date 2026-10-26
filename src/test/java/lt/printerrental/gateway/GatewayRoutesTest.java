package lt.printerrental.gateway;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

class GatewayRoutesTest {
  @Test
  void attachmentsAlwaysGoToDocumentsAndInternalPathsCannotBeRouted() {
    var routes =
        new GatewayRoutes(
            "identity", "rental", "documents", "notifications", "metering", "billing");
    assertThat(routes.destination("/api/contracts/123/attachments/456")).isEqualTo("documents");
    assertThat(routes.destination("/api/contracts/123/activate")).isEqualTo("rental");
    assertThat(routes.destination("/api/auth/me")).isEqualTo("identity");
    assertThat(routes.destination("/api/admin/users")).isEqualTo("identity");
    assertThat(routes.destination("/api/notifications")).isEqualTo("notifications");
    assertThat(routes.destination("/internal/events")).isNull();
  }

  @Test
  void repairRequestsRouteOnlyToRental() {
    var routes =
        new GatewayRoutes(
            "identity", "rental", "documents", "notifications", "metering", "billing");
    assertThat(routes.destination("/api/repair-requests")).isEqualTo("rental");
    assertThat(routes.destination("/api/repair-requests/123/status")).isEqualTo("rental");
    assertThat(routes.destination("/api/repair-requests-other")).isNull();
    assertThat(routes.destination("/internal/repair-requests")).isNull();
  }

  @Test
  void accountingPathsAreExplicitAndOptional() {
    var enabled =
        new GatewayRoutes(
            "identity", "rental", "documents", "notifications", "metering", "billing");
    assertThat(enabled.destination("/api/metering/contracts/123/readings")).isEqualTo("metering");
    assertThat(enabled.destination("/api/billing/payments")).isEqualTo("billing");
    assertThat(enabled.destination("/api/billing-other")).isNull();
    assertThat(enabled.destination("/api/metering-other")).isNull();
    var disabled = new GatewayRoutes("identity", "rental", "documents", "notifications", "", "");
    assertThat(disabled.destination("/api/metering/contracts/123")).isNull();
    assertThat(disabled.destination("/api/contracts/123")).isEqualTo("rental");
  }
}
