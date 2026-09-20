package seekfactory.axoraa.services.serviceImpl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import seekfactory.axoraa.dto.Response.admin.AdminDashboardStatsResponse;
import seekfactory.axoraa.enums.RfqStatus;
import seekfactory.axoraa.repository.ManufacturerRepository;
import seekfactory.axoraa.repository.Rfqs.RfqRepository;
import seekfactory.axoraa.repository.UserRepository;
import seekfactory.axoraa.services.services.AdminDashboardService;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminDashboardServiceImpl implements AdminDashboardService {

    private final UserRepository userRepository;
    private final ManufacturerRepository manufacturerRepository;
    private final RfqRepository rfqRepository;

    @Override
    public AdminDashboardStatsResponse getDashboardStats() {
        long totalUsers = userRepository.count();
        long verifiedFactories = manufacturerRepository.countByVerifiedTrue();
        long pendingRfqs = rfqRepository.countByStatus(RfqStatus.SUBMITTED);

        return AdminDashboardStatsResponse.builder()
                .totalUsers(totalUsers)
                .verifiedFactories(verifiedFactories)
                .pendingRfqs(pendingRfqs)
                .build();
    }
}
