package com.example.watermanagement.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

    @Test
    void lanCannotPreviewAccountingRepair() throws Exception {
        assertRepairRequestRejected("/api/accounting/health/repair/preview");
    }

    @Test
    void lanCannotExecuteAccountingRepair() throws Exception {
        assertRepairRequestRejected("/api/accounting/health/repair/execute");
    }

    @Test
    void loopbackCanPreviewAndExecuteAccountingRepair() throws Exception {
        for (String path : new String[]{
                "/api/accounting/health/repair/preview",
                "/api/accounting/health/repair/execute"}) {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
            request.setRemoteAddr("127.0.0.1");
            assertThat(interceptor.preHandle(request, new MockHttpServletResponse(), new Object())).isTrue();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/accounting/health/repair/preview;x=1",
            "/api/accounting/health/repair/execute;x=1"})
    void lanCannotBypassRepairProtectionWithMatrixParameter(String path) throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new RepairEndpointController())
                .addInterceptors(interceptor)
                .build();

        mockMvc.perform(post(path).with(request -> {
                    request.setRemoteAddr("10.10.10.20");
                    return request;
                }))
                .andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @MethodSource("existingSensitiveMatrixPaths")
    void matrixParametersCannotBypassExistingSensitiveRules(String method, String path) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setRemoteAddr("10.10.10.20");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(interceptor.preHandle(request, response, new Object())).isFalse();
        assertThat(response.getStatus()).isEqualTo(403);
    }

    private void assertRepairRequestRejected(String path) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.setRemoteAddr("10.10.10.20");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(interceptor.preHandle(request, response, new Object())).isFalse();
        assertThat(response.getStatus()).isEqualTo(403);
    }

    private static Stream<Arguments> existingSensitiveMatrixPaths() {
        return Stream.of(
                Arguments.of("GET", "/api/settings/backup/download;x=1"),
                Arguments.of("POST", "/api/settings/backup/restore;x=1"),
                Arguments.of("POST", "/api/households/batch-delete;x=1"),
                Arguments.of("DELETE", "/api/households;x=1/123;x=2"),
                Arguments.of("POST", "/api/material-records/batch-delete;x=1"),
                Arguments.of("DELETE", "/api/material-records;x=1/123;x=2"),
                Arguments.of("DELETE", "/api/accounting/month-locks;x=1"),
                Arguments.of("POST", "/api/accounting;x=1/adjustments;x=2/water;x=3"));
    }

    @RestController
    static class RepairEndpointController {
        @PostMapping({
                "/api/accounting/health/repair/preview",
                "/api/accounting/health/repair/execute"})
        String repair() {
            return "handler reached";
        }
    }
}
