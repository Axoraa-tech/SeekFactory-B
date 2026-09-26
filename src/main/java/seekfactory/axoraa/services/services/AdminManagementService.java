package seekfactory.axoraa.services.services;

import seekfactory.axoraa.dto.Response.admin.AdminManagementDtos.ManufacturerDetail;
import seekfactory.axoraa.dto.Response.admin.AdminManagementDtos.ManufacturerRow;
import seekfactory.axoraa.dto.Response.admin.AdminManagementDtos.Page;
import seekfactory.axoraa.dto.Response.admin.AdminManagementDtos.PlanRequest;
import seekfactory.axoraa.dto.Response.admin.AdminManagementDtos.PlanRow;
import seekfactory.axoraa.dto.Response.admin.AdminManagementDtos.RfqRow;
import seekfactory.axoraa.dto.Response.admin.AdminManagementDtos.UserRow;

import java.util.List;

public interface AdminManagementService {

    Page<UserRow> listUsers(String q, String role, String status, int page, int size);

    void setUserActive(String userId, boolean active, String currentAdminId);

    Page<ManufacturerRow> listManufacturers(String q, String verified, int page, int size);

    /** Full application for the manufacturer review screen. */
    ManufacturerDetail getManufacturer(String manufacturerId);

    /** Approve or reject a manufacturer; a reason is required when rejecting. */
    void reviewManufacturer(String manufacturerId, boolean approve, String reason, String currentAdminId);

    void setManufacturerVerified(String manufacturerId, boolean verified);

    void setManufacturerPlan(String manufacturerId, String planId);

    Page<RfqRow> listRfqs(String q, String status, int page, int size);

    void setRfqStatus(String rfqId, String status);

    List<PlanRow> listPlans();

    void createPlan(PlanRequest request);

    void updatePlan(String planId, PlanRequest request);

    void deletePlan(String planId);
}
