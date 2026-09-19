package seekfactory.axoraa.services.serviceImpl;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import seekfactory.axoraa.entity.ErrorLog;
import seekfactory.axoraa.repository.ErrorLogRepository;
import seekfactory.axoraa.services.services.ErrorLogService;
import seekfactory.axoraa.utils.SecurityUtils;

import java.io.PrintWriter;
import java.io.StringWriter;

@Slf4j
@Service
@RequiredArgsConstructor
public class ErrorLogServiceImpl implements ErrorLogService {

    private final ErrorLogRepository errorLogRepository;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW) // Saves even if parent transaction rolls back
    public void logError(Exception ex, HttpServletRequest request, int statusCode) {
        try {
            StringWriter sw = new StringWriter();
            ex.printStackTrace(new PrintWriter(sw));
            String stackTrace = sw.toString();

            String userId = null;
            try {
                userId = SecurityUtils.getCurrentUserId();
            } catch (Exception ignored) {
                // Unauthenticated request
            }

            ErrorLog errorLog = ErrorLog.builder()
                    .errorType(ex.getClass().getName())
                    .message(ex.getMessage() != null ? ex.getMessage() : "No message")
                    .stackTrace(stackTrace)
                    .httpMethod(request != null ? request.getMethod() : "UNKNOWN")
                    .endpoint(request != null ? request.getRequestURI() : "UNKNOWN")
                    .statusCode(statusCode)
                    .userId(userId)
                    .ipAddress(request != null ? request.getRemoteAddr() : "UNKNOWN")
                    .build();

            errorLogRepository.save(errorLog);
            log.info("Saved critical error to database: [{} - {}]", errorLog.getErrorType(), errorLog.getId());
        } catch (Exception e) {
            log.error("Failed to persist error log to database: {}", e.getMessage());
        }
    }
}