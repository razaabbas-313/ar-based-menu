package com.atelier.media.security;

import com.atelier.media.config.AtelierProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ClientIpResolver {

    private final AtelierProperties props;

    public ClientIpResolver(AtelierProperties props) {
        this.props = props;
    }

    public String resolveClientIp(HttpServletRequest request) {
        if (request == null) {
            return "unknown";
        }
        String remoteAddr = request.getRemoteAddr();
        List<String> trustedProxies = props.trustedProxies();
        if (trustedProxies != null && !trustedProxies.isEmpty() && trustedProxies.contains(remoteAddr)) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                String[] parts = forwarded.split(",");
                return parts[0].trim();
            }
        }
        return remoteAddr != null && !remoteAddr.isBlank() ? remoteAddr : "unknown";
    }
}
