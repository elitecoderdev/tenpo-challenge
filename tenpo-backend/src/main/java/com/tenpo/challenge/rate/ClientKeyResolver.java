package com.tenpo.challenge.rate;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class ClientKeyResolver {

    public String resolve(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwardedFor)) {
            return forwardedFor.split(",")[0].trim();
        }

        String remoteAddress = request.getRemoteAddr();
        return StringUtils.hasText(remoteAddress) ? remoteAddress : "anonymous-client";
    }
}
