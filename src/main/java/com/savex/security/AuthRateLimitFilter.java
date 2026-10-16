package com.savex.security;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.time.Clock;
import java.util.*;

/** Bounded single-instance abuse protection. Never trusts caller-supplied forwarding headers. */
public class AuthRateLimitFilter extends OncePerRequestFilter {
    private record Bucket(long expiresAt,int attempts) {}
    private final Map<String,Bucket> buckets=new HashMap<>();
    private final int loginLimit,registerLimit,maxBuckets;
    private final long windowMillis;
    private final Clock clock;
    public AuthRateLimitFilter(int loginLimit,int registerLimit,int maxBuckets,long windowSeconds,Clock clock) {
        if(loginLimit<1 || registerLimit<1 || maxBuckets<1 || windowSeconds<1 || windowSeconds>86400)
            throw new IllegalArgumentException("Invalid authentication rate limits");
        this.loginLimit=loginLimit;this.registerLimit=registerLimit;this.maxBuckets=maxBuckets;
        this.windowMillis=windowSeconds*1000;this.clock=clock;
    }
    private synchronized long retryAfter(String key,int limit) {
        long now=clock.millis();
        Bucket bucket=buckets.get(key);
        if(bucket==null || bucket.expiresAt()<=now) {
            buckets.entrySet().removeIf(e->e.getValue().expiresAt()<=now);
            if(buckets.size()>=maxBuckets) return Math.max(1,(buckets.values().stream().mapToLong(Bucket::expiresAt).min().orElse(now+windowMillis)-now+999)/1000);
            buckets.put(key,new Bucket(now+windowMillis,1));return 0;
        }
        if(bucket.attempts()>=limit) return Math.max(1,(bucket.expiresAt()-now+999)/1000);
        buckets.put(key,new Bucket(bucket.expiresAt(),bucket.attempts()+1));return 0;
    }
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)
            throws ServletException,IOException {
        String path=request.getServletPath();
        boolean login=Set.of("/api/v1/auth/login","/api/v1/auth/refresh","/api/v1/auth/logout").contains(path),register="/api/v1/auth/register".equals(path);
        if("POST".equals(request.getMethod()) && (login || register)) {
            long retry=retryAfter(path+":"+request.getRemoteAddr(),login?loginLimit:registerLimit);
            if(retry>0) {
                response.setStatus(429);response.setContentType("application/json");
                response.setHeader("Retry-After",Long.toString(retry));response.setHeader("Cache-Control","no-store");
                response.getWriter().write("{\"success\":false,\"message\":\"Too many sign-in or registration requests. Please wait before trying again.\"}");return;
            }
        }
        chain.doFilter(request,response);
    }
}
