package seekfactory.axoraa.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Slf4j
@Component

@Order(Ordered.HIGHEST_PRECEDENCE)
public class ApiLoggingFilter extends OncePerRequestFilter {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        ContentCachingRequestWrapper reqWrapper = new ContentCachingRequestWrapper(request, 1024 * 1024);
        ContentCachingResponseWrapper resWrapper = new ContentCachingResponseWrapper(response);

        long startTime = System.currentTimeMillis();

        try {
            filterChain.doFilter(reqWrapper, resWrapper);
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            logApiDetails(reqWrapper, resWrapper, duration);
            resWrapper.copyBodyToResponse();
        }
    }

    private void logApiDetails(ContentCachingRequestWrapper req, ContentCachingResponseWrapper res, long duration) {
        String method = req.getMethod();
        String uri = req.getRequestURI();
        String queryString = req.getQueryString() != null ? "?" + req.getQueryString() : "";
        int status = res.getStatus();

        String reqBody = formatJson(getPayload(req.getContentAsByteArray()));
        String resBody = formatJson(getPayload(res.getContentAsByteArray()));

        StringBuilder sb = new StringBuilder();
        sb.append("\n========================= [API CALL START] =========================");
        sb.append(String.format("\n👉 HTTP:     %s %s%s", method, uri, queryString));
        sb.append(String.format("\n⏱️ TIME:     %d ms", duration));
        sb.append(String.format("\n📡 STATUS:   %d", status));

        if (!reqBody.isEmpty()) {
            sb.append("\n📥 REQUEST BODY:\n").append(reqBody);
        }

        if (!resBody.isEmpty()) {
            sb.append("\n📤 RESPONSE BODY:\n").append(resBody);
        }
        sb.append("\n========================== [API CALL END] ==========================\n");

        if (status >= 400) {
            log.warn(sb.toString());
        } else {
            log.info(sb.toString());
        }
    }

    private String getPayload(byte[] buf) {
        if (buf == null || buf.length == 0) return "";
        try {
            return new String(buf, StandardCharsets.UTF_8).trim();
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * Converts single-line JSON string into indented, multi-line pretty JSON.
     */
    private String formatJson(String rawJson) {
        if (rawJson == null || rawJson.isBlank()) return "";
        try {
            Object jsonObject = objectMapper.readValue(rawJson, Object.class);
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonObject);
        } catch (Exception e) {
            return rawJson; // Fallback to raw string if not valid JSON
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/swagger-ui") || path.startsWith("/api-docs") || path.startsWith("/actuator");
    }
}