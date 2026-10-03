package seekfactory.axoraa.controller;

import org.junit.jupiter.api.Test;
import seekfactory.axoraa.enums.RfqStatus;

import static org.assertj.core.api.Assertions.assertThat;

/** Admin option lists come straight from the enums. */
class AdminMetaControllerTest {

    @Test
    void metaMirrorsBackendEnums() {
        AdminMetaController.AdminMeta meta = new AdminMetaController().meta().getBody().getData();

        assertThat(meta.roles()).containsExactly("BUYER", "SUPPLIER", "ADMIN");
        assertThat(meta.rfqStatuses()).hasSize(RfqStatus.values().length).contains("SUBMITTED", "CANCELLED");
        assertThat(meta.paymentStatuses()).containsExactly("PENDING", "APPROVED", "REJECTED");
        assertThat(meta.showcaseModes()).contains("DUAL", "SIDEBAR");
    }
}
