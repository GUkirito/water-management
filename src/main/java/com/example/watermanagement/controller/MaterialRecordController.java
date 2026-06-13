package com.example.watermanagement.controller;

import com.example.watermanagement.dto.ApiResponse;
import com.example.watermanagement.dto.MaterialRecordRequest;
import com.example.watermanagement.entity.MaterialPayment;
import com.example.watermanagement.entity.MaterialRecord;
import com.example.watermanagement.service.MaterialRecordService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Tag(name = "材料费管理", description = "独立材料费记录的增删改查、导入导出、收费")
@RestController
@RequestMapping("/api/material-records")
@RequiredArgsConstructor
public class MaterialRecordController {

    private final MaterialRecordService recordService;

    @GetMapping("/list")
    public ApiResponse<Page<MaterialRecord>> list(
            @RequestParam(required = false) String villageName,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate paidDateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate paidDateTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ApiResponse.ok(recordService.list(villageName, status, keyword,
                paidDateFrom, paidDateTo, pageable));
    }

    @GetMapping("/{id}")
    public ApiResponse<MaterialRecord> getById(@PathVariable Long id) {
        return ApiResponse.ok(recordService.getById(id));
    }

    @PostMapping
    public ApiResponse<MaterialRecord> create(@Valid @RequestBody MaterialRecordRequest request) {
        return ApiResponse.ok("新增成功", recordService.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<MaterialRecord> update(@PathVariable Long id,
                                               @Valid @RequestBody MaterialRecordRequest request) {
        return ApiResponse.ok("更新成功", recordService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        recordService.delete(id);
        return ApiResponse.ok("删除成功", null);
    }

    @PostMapping("/batch-delete")
    public ApiResponse<Void> batchDelete(@RequestBody Map<String, List<Long>> body) {
        recordService.batchDelete(body.get("ids"));
        return ApiResponse.ok("批量删除成功", null);
    }

    @PostMapping("/import")
    public ApiResponse<Map<String, Object>> importExcel(
            @RequestParam("file") MultipartFile file) throws IOException {
        return ApiResponse.ok("导入完成", recordService.importFromExcel(
                file.getInputStream(), "导入"));
    }

    @GetMapping("/export")
    public void exportExcel(
            @RequestParam(required = false) String villageName,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate paidDateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate paidDateTo,
            HttpServletResponse response) throws IOException {
        recordService.exportToExcel(villageName, status, keyword,
                paidDateFrom, paidDateTo, response);
    }

    @PostMapping("/{id}/collect")
    public ApiResponse<MaterialPayment> collect(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {
        BigDecimal amount = new BigDecimal(body.get("amount").toString());
        LocalDate paidDate = LocalDate.parse(body.get("paidDate").toString());
        String collector = body.get("collector") != null ? body.get("collector").toString() : "管理员";
        String note = body.get("note") != null ? body.get("note").toString() : null;
        return ApiResponse.ok("收费成功", recordService.collect(id, amount, paidDate, collector, note));
    }

    @GetMapping("/{id}/payments")
    public ApiResponse<List<MaterialPayment>> getPayments(@PathVariable Long id) {
        return ApiResponse.ok(recordService.getPayments(id));
    }
}
