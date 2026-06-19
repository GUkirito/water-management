package com.example.watermanagement.controller;

import com.example.watermanagement.dto.ApiResponse;
import com.example.watermanagement.dto.PaymentRequest;
import com.example.watermanagement.entity.Payment;
import com.example.watermanagement.entity.WaterBill;
import com.example.watermanagement.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 收费管理 Controller
 * <p>
 * 提供未缴清账单查询、合并缴费、缴费历史查询等功能。
 */
@Tag(name = "收费管理", description = "水费/材料费缴费")
@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @Operation(summary = "获取未缴清水费账单",
            description = "返回指定水表下所有未缴清的水费账单（支持多选合并缴费）")
    @GetMapping("/pending-water")
    public ApiResponse<List<WaterBill>> getPendingWater(
            @Parameter(description = "水表编号") @RequestParam String waterMeterId) {
        return ApiResponse.ok(paymentService.getPendingWaterBills(waterMeterId));
    }

    @Operation(summary = "缴费",
            description = "水费支持合并缴纳多个月份（billType=water, billIds=[1,2,3]），材料费逐笔缴纳")
    @PostMapping("/pay")
    public ApiResponse<List<Payment>> pay(@Valid @RequestBody PaymentRequest request) {
        List<Payment> payments = paymentService.pay(request);
        return ApiResponse.ok("缴费成功，共 " + payments.size() + " 笔", payments);
    }

    @Operation(summary = "获取缴费历史", description = "按水表编号查询所有缴费记录（含水费和材料费）")
    @GetMapping("/history")
    public ApiResponse<List<Payment>> getHistory(
            @Parameter(description = "水表编号") @RequestParam String waterMeterId) {
        return ApiResponse.ok(paymentService.getHistory(waterMeterId));
    }

    @Operation(summary = "获取所有水费账单（含已缴清）", description = "用于缴费历史页面展示账单年月信息")
    @GetMapping("/all-water-bills")
    public ApiResponse<List<WaterBill>> getAllWaterBills(
            @Parameter(description = "水表编号") @RequestParam String waterMeterId) {
        return ApiResponse.ok(paymentService.getAllWaterBills(waterMeterId));
    }
}
