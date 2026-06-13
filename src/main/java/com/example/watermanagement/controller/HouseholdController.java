package com.example.watermanagement.controller;

import com.example.watermanagement.dto.ApiResponse;
import com.example.watermanagement.dto.HouseholdRequest;
import com.example.watermanagement.entity.Household;
import com.example.watermanagement.service.HouseholdService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * 村民管理 Controller
 * <p>
 * 提供村民的增删改查，支持按村名多选筛选、水表编号模糊查询、分页。
 */
@Tag(name = "村民管理", description = "村民/水表信息的增删改查")
@RestController
@RequestMapping("/api/households")
@RequiredArgsConstructor
public class HouseholdController {

    private final HouseholdService householdService;

    @Operation(summary = "分页获取村民列表", description = "支持按村名多选筛选、水表编号模糊查询")
    @GetMapping("/list")
    public ApiResponse<Page<Household>> list(
            @Parameter(description = "村名列表（多选）") @RequestParam(required = false) List<String> villageNames,
            @Parameter(description = "水表编号（模糊搜索）") @RequestParam(required = false) String waterMeterId,
            @Parameter(description = "页码（从0开始）") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ApiResponse.ok(householdService.list(villageNames, waterMeterId, pageable));
    }

    @Operation(summary = "根据ID查询村民")
    @GetMapping("/{id}")
    public ApiResponse<Household> getById(@Parameter(description = "村民ID") @PathVariable Long id) {
        return ApiResponse.ok(householdService.getById(id));
    }

    @Operation(summary = "新增村民", description = "校验水表编号唯一性，同时自动创建材料费账单")
    @PostMapping("/add")
    public ApiResponse<Household> add(@Valid @RequestBody HouseholdRequest request) {
        return ApiResponse.ok("新增成功", householdService.add(request));
    }

    @Operation(summary = "更新村民信息", description = "支持换绑水表编号，同步更新材料费账单")
    @PutMapping("/update/{id}")
    public ApiResponse<Household> update(
            @Parameter(description = "村民ID") @PathVariable Long id,
            @Valid @RequestBody HouseholdRequest request) {
        return ApiResponse.ok("更新成功", householdService.update(id, request));
    }

    @Operation(summary = "删除村民（软删除）", description = "将 is_active 置为 0，数据不物理删除")
    @DeleteMapping("/delete/{id}")
    public ApiResponse<Void> delete(@Parameter(description = "村民ID") @PathVariable Long id) {
        householdService.delete(id);
        return ApiResponse.ok("删除成功", null);
    }

    @Operation(summary = "批量删除村民")
    @PostMapping("/batch-delete")
    public ApiResponse<Void> batchDelete(@RequestBody Map<String, List<Long>> body) {
        householdService.batchDelete(body.get("ids"));
        return ApiResponse.ok("批量删除成功", null);
    }

    @Operation(summary = "按村名删除该村所有村民")
    @DeleteMapping("/delete-by-village")
    public ApiResponse<Void> deleteByVillage(@RequestParam String villageName) {
        householdService.deleteByVillage(villageName);
        return ApiResponse.ok("已删除村组: " + villageName, null);
    }

    @Operation(summary = "批量修改村民的村组名称")
    @PutMapping("/batch-update-village")
    public ApiResponse<Void> batchUpdateVillage(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Long> ids = ((List<Number>) body.get("ids")).stream()
                .map(Number::longValue).toList();
        String villageName = body.get("villageName").toString();
        householdService.batchUpdateVillage(ids, villageName);
        return ApiResponse.ok("已更新 " + ids.size() + " 户的村组为 " + villageName, null);
    }

    @Operation(summary = "导出村民信息到Excel", description = "按筛选条件导出")
    @GetMapping("/export")
    public void export(@Parameter(description = "村名列表") @RequestParam(required = false) List<String> villageNames,
                       @Parameter(description = "水表编号") @RequestParam(required = false) String waterMeterId,
                       HttpServletResponse response) throws IOException {
        householdService.exportToExcel(villageNames, waterMeterId, response);
    }

    @Operation(summary = "批量导入村民", description = "上传Excel文件，全量唯一性校验后批量写入")
    @PostMapping("/import")
    public ApiResponse<Map<String, Object>> importExcel(
            @Parameter(description = "Excel文件") @RequestParam("file") MultipartFile file) throws IOException {
        Map<String, Object> result = householdService.importFromExcel(file.getInputStream());
        if (result.containsKey("conflicts")) {
            return ApiResponse.fail(result.get("message").toString());
        }
        return ApiResponse.ok(result.get("message").toString(), result);
    }

    @Operation(summary = "从水费登记册导入村民",
            description = "上传 水费登记册.xlsx，解析第2行提取村名，第4行起提取户主姓名/表号/电话，表号已存在则跳过")
    @PostMapping("/import-from-register")
    public ApiResponse<Map<String, Object>> importFromRegister(
            @Parameter(description = "水费登记册 Excel 文件") @RequestParam("file") MultipartFile file) throws IOException {
        Map<String, Object> result = householdService.importFromWaterRegister(file.getInputStream());
        return ApiResponse.ok("导入完成", result);
    }
}
