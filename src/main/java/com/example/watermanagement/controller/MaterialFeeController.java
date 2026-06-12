package com.example.watermanagement.controller;

import com.example.watermanagement.dto.ApiResponse;
import com.example.watermanagement.dto.MaterialCollectRequest;
import com.example.watermanagement.dto.MaterialFeeRow;
import com.example.watermanagement.entity.Payment;
import com.example.watermanagement.service.MaterialFeeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 材料费管理 Controller
 * <p>
 * 提供材料费按村查询、收取、缴费历史等功能。
 */
@Tag(name = "材料费管理", description = "材料费查询与收取")
@RestController
@RequestMapping("/api/material-fee")
@RequiredArgsConstructor
public class MaterialFeeController {

    private final MaterialFeeService materialFeeService;

    @Operation(summary = "按村分页查询材料费列表",
            description = "返回指定村组所有户的材料费信息（含未创建账单的户，默认显示未收状态）")
    @GetMapping("/list")
    public ApiResponse<Page<MaterialFeeRow>> list(
            @Parameter(description = "村名") @RequestParam(required = false) String villageName,
            @Parameter(description = "页码") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ApiResponse.ok(materialFeeService.listByVillage(villageName, pageable));
    }

    @Operation(summary = "收取材料费",
            description = "更新 material_bills 的 actualPaid/status/paidAt/collector，同时写入 payments 表")
    @PostMapping("/collect")
    public ApiResponse<Payment> collect(@Valid @RequestBody MaterialCollectRequest request) {
        return ApiResponse.ok("收费成功", materialFeeService.collect(request));
    }

    @Operation(summary = "查询材料费缴费历史", description = "按水表编号查询该户的材料费收款记录")
    @GetMapping("/history")
    public ApiResponse<List<Payment>> getHistory(
            @Parameter(description = "水表编号") @RequestParam String waterMeterId) {
        return ApiResponse.ok(materialFeeService.getHistory(waterMeterId));
    }
}
