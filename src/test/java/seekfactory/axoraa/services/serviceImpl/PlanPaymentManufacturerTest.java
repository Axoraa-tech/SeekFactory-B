package seekfactory.axoraa.services.serviceImpl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import seekfactory.axoraa.entity.Manufacturer;
import seekfactory.axoraa.entity.User;
import seekfactory.axoraa.enums.ManufacturerPlan;
import seekfactory.axoraa.enums.UserRole;
import seekfactory.axoraa.exceptions.BadRequestException;
import seekfactory.axoraa.repository.ManufacturerRepository;
import seekfactory.axoraa.repository.UserRepository;
import seekfactory.axoraa.services.services.NotificationService;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PlanPaymentManufacturerTest {
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};

    private JdbcTemplate jdbc;
    private UserRepository users;
    private ManufacturerRepository manufacturers;
    private PlanPaymentServiceImpl service;
    private Manufacturer factory;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        users = mock(UserRepository.class);
        manufacturers = mock(ManufacturerRepository.class);
        service = new PlanPaymentServiceImpl(jdbc, users, mock(NotificationService.class), manufacturers);
        User owner = User.builder().name("Owner").email("owner@example.test").role(UserRole.ROLE_SUPPLIER).build();
        owner.setId("u1");
        factory = Manufacturer.builder().name("Laser Works").country("China").build();
        factory.setId("m1");
        when(users.findById("u1")).thenReturn(Optional.of(owner));
        when(manufacturers.findByUserId("u1")).thenReturn(Optional.of(factory));
    }

    private MockMultipartFile proof() {
        return new MockMultipartFile("file", "alipay.png", "image/png", PNG);
    }

    @Test
    void plansMatchThePlanCards() {
        assertThat(ManufacturerPlan.PREMIUM.priceUsd()).isEqualByComparingTo(new BigDecimal("1000"));
        assertThat(ManufacturerPlan.VIP.priceUsd()).isEqualByComparingTo(new BigDecimal("10000"));
        assertThat(ManufacturerPlan.STRATEGIC.priceUsd()).isEqualByComparingTo(new BigDecimal("20000"));
        assertThat(ManufacturerPlan.PREMIUM.productLimit()).isEqualTo(20);
        assertThat(ManufacturerPlan.VIP.productLimit()).isEqualTo(ProductLimitService.MAX_LIMIT);
        assertThat(ManufacturerPlan.parse("vip")).contains(ManufacturerPlan.VIP);
        assertThat(ManufacturerPlan.of(null)).isEqualTo(ManufacturerPlan.BASIC);
    }

    @Test
    void storesAFactoryPaymentPricedOnTheServerInUsd() {
        service.submit("u1", "premium", "china", "ALIPAY-1", proof());
        verify(jdbc).update(contains("'MANUFACTURER'"), anyId(), eq("u1"), eq("m1"), eq("PREMIUM"), eq("Premium"),
                eq("china"), eq(new BigDecimal("1000")), eq("ALIPAY-1"), any(), any(), any(), eq("Laser Works"), eq("China"),
                any(), eq(PNG), eq("image/png"), eq("alipay.png"), eq(PNG.length));
    }

    private static String anyId() {
        return argThat(id -> id != null && id.length() == 36);
    }

    @Test
    void refusesBasicUnknownSameAndLowerPlans() {
        assertThatThrownBy(() -> service.submit("u1", "basic", "china", null, proof()))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("does not need a payment");
        assertThatThrownBy(() -> service.submit("u1", "gold", "china", null, proof()))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("Unknown plan");
        factory.setMembershipPlan("VIP");
        assertThatThrownBy(() -> service.submit("u1", "vip", "china", null, proof()))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("already on the VIP Global Partner plan");
        assertThatThrownBy(() -> service.submit("u1", "premium", "china", null, proof()))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("higher plan");
        verify(jdbc, never()).update(contains("INSERT INTO plan_payments"), any(Object[].class));
    }

    @Test
    void approvalActivatesThePlanAndRaisesTheProductLimit() {
        Map<String, Object> row = new HashMap<>();
        row.put("user_id", "u1");
        row.put("account_type", "MANUFACTURER");
        row.put("manufacturer_plan", "PREMIUM");
        row.put("manufacturer_id", "m1");
        row.put("plan_name", "Premium");
        row.put("status", "PENDING");
        when(jdbc.queryForList(contains("FOR UPDATE"), eq("p1"))).thenReturn(List.of(row));
        when(jdbc.update(contains("UPDATE manufacturers"), any(), any(), any())).thenReturn(1);

        service.approve("p1", "admin");

        verify(jdbc).update(contains("product_limit = GREATEST(product_limit, ?)"), eq("PREMIUM"), eq(20), eq("m1"));
        verify(jdbc).update(contains("status = 'APPROVED'"), eq("admin"), eq("p1"));
    }
}
