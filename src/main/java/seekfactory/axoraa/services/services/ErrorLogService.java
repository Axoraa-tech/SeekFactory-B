package seekfactory.axoraa.services.services;

import jakarta.servlet.http.HttpServletRequest;

public interface ErrorLogService {
    void logError(Exception ex, HttpServletRequest request, int statusCode);
}