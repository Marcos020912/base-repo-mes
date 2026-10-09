package edu.kit.datamanager.repo.security;

import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import org.springframework.web.util.UriUtils;

/** Use the container's decoded canonical servlet path, not a raw encoded URI. */
public final class ResourceRequestPath {
    private ResourceRequestPath() {}
    public static String path(HttpServletRequest request) {
        String servlet = request.getServletPath();
        if (servlet != null && !servlet.isEmpty()) return servlet;
        String uri = request.getRequestURI();
        String context = request.getContextPath();
        if (context != null && !context.isEmpty() && uri.startsWith(context)) uri = uri.substring(context.length());
        return UriUtils.decode(uri,StandardCharsets.UTF_8);
    }
}
