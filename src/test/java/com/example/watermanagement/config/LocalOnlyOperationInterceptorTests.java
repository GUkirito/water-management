package com.example.watermanagement.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class LocalOnlyOperationInterceptorTests {

    private final LocalOnlyOperationInterceptor interceptor = new LocalOnlyOperationInterceptor();

    @Test
    void loopbackRequestIsAllowed() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/settings/backup/restore");
        request.setRemoteAddr("127.0.0.1");

        assertThat(interceptor.preHandle(request, new MockHttpServletResponse(), new Object())).isTrue();
    }

    @Test
    void lanRequestIsRejectedWith403ApiResponse() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/settings/backup/restore");
        request.setRemoteAddr("192.168.1.20");
        request.addHeader("X-Forwarded-For", "127.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(interceptor.preHandle(request, response, new Object())).isFalse();
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("仅允许在本机执行");
    }

    @Test
    void ipv6LoopbackRequestIsAllowed() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("DELETE", "/api/accounting/month-locks");
        request.setRemoteAddr("0:0:0:0:0:0:0:1");

        assertThat(interceptor.preHandle(request, new MockHttpServletResponse(), new Object())).isTrue();
    }
}
