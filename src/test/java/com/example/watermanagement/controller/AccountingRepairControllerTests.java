package com.example.watermanagement.controller;

import com.example.watermanagement.dto.AccountingRepairPreview;
import com.example.watermanagement.dto.AccountingRepairResult;
import com.example.watermanagement.service.AccountingControlService;
import com.example.watermanagement.service.AccountingHealthService;
import com.example.watermanagement.service.AccountingRepairService;
import com.example.watermanagement.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AccountingRepairControllerTests {

    private AccountingRepairService repairService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        repairService = mock(AccountingRepairService.class);
        AccountingHealthController controller = new AccountingHealthController(
                mock(AccountingHealthService.class), mock(AccountingControlService.class), repairService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void previewEndpointReturnsRepairPlan() throws Exception {
        when(repairService.preview(any())).thenReturn(new AccountingRepairPreview(
                "PAYMENT_TOTAL_MISMATCH", "water_bill", 1L, true, "可安全修复",
                Map.of(), Map.of(), List.of(), true));

        mockMvc.perform(post("/api/accounting/health/repair/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"issueType":"PAYMENT_TOTAL_MISMATCH","refType":"water_bill","refId":1}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.repairable").value(true));
    }

    @Test
    void executeRejectsBlankOperatorWithChineseMessage() throws Exception {
        mockMvc.perform(post("/api/accounting/health/repair/execute")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"issueType":"PAYMENT_TOTAL_MISMATCH","refType":"water_bill","refId":1,
                                 "operator":" ","reason":"修复错绑流水"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("操作人不能为空")));
    }

    @Test
    void executeRejectsBlankReasonWithChineseMessage() throws Exception {
        mockMvc.perform(post("/api/accounting/health/repair/execute")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"issueType":"PAYMENT_TOTAL_MISMATCH","refType":"water_bill","refId":1,
                                 "operator":"测试员","reason":" "}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("修复原因不能为空")));
    }

    @Test
    void executeEndpointReturnsRepairResult() throws Exception {
        when(repairService.execute(any())).thenReturn(new AccountingRepairResult(
                "accounting-repair-test.db", 9L, Map.of(), Map.of(), List.of()));

        mockMvc.perform(post("/api/accounting/health/repair/execute")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"issueType":"PAYMENT_TOTAL_MISMATCH","refType":"water_bill","refId":1,
                                 "operator":"测试员","reason":"修复错绑流水"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("账务修复完成"))
                .andExpect(jsonPath("$.data.snapshotName").value("accounting-repair-test.db"))
                .andExpect(jsonPath("$.data.auditId").value(9));
    }
}
