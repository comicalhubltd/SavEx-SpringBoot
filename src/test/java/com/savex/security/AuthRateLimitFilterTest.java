package com.savex.security;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import java.time.*;
import static org.assertj.core.api.Assertions.*;
class AuthRateLimitFilterTest {
    static class MutableClock extends Clock {
        long millis;
        public ZoneId getZone(){return ZoneOffset.UTC;}
        public Clock withZone(ZoneId zone){return this;}
        public Instant instant(){return Instant.ofEpochMilli(millis);}
        public long millis(){return millis;}
    }
    private MockHttpServletResponse request(AuthRateLimitFilter filter,String path,String address,String forwarded) throws Exception {
        var request=new MockHttpServletRequest("POST",path);request.setServletPath(path);request.setRemoteAddr(address);
        if(forwarded!=null)request.addHeader("X-Forwarded-For",forwarded);
        var response=new MockHttpServletResponse();filter.doFilter(request,response,(req,res)->((jakarta.servlet.http.HttpServletResponse)res).setStatus(204));return response;
    }
    @Test void blocksBeforeAuthenticationAndExpiresWithoutTrustingForwardedHeader() throws Exception {
        var clock=new MutableClock();var filter=new AuthRateLimitFilter(2,1,10,60,clock);
        assertThat(request(filter,"/api/v1/auth/login","127.0.0.1",null).getStatus()).isEqualTo(204);
        assertThat(request(filter,"/api/v1/auth/login","127.0.0.1","1.1.1.1").getStatus()).isEqualTo(204);
        var blocked=request(filter,"/api/v1/auth/login","127.0.0.1","2.2.2.2");
        assertThat(blocked.getStatus()).isEqualTo(429);assertThat(blocked.getHeader("Retry-After")).isEqualTo("60");
        assertThat(request(filter,"/api/v1/auth/login","127.0.0.2",null).getStatus()).isEqualTo(204);
        assertThat(request(filter,"/api/v1/money/webhook/flutterwave","127.0.0.1",null).getStatus()).isEqualTo(204);
        clock.millis=60000;assertThat(request(filter,"/api/v1/auth/login","127.0.0.1",null).getStatus()).isEqualTo(204);
    }
    @Test void registrationHasSeparateLimitAndStorageIsBounded() throws Exception {
        var filter=new AuthRateLimitFilter(2,1,2,60,Clock.systemUTC());
        assertThat(request(filter,"/api/v1/auth/register","a",null).getStatus()).isEqualTo(204);
        assertThat(request(filter,"/api/v1/auth/register","a",null).getStatus()).isEqualTo(429);
        assertThat(request(filter,"/api/v1/auth/login","a",null).getStatus()).isEqualTo(204);
        assertThat(request(filter,"/api/v1/auth/login","b",null).getStatus()).isEqualTo(429);
    }
}
