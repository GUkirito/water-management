package com.example.watermanagement.service;

import com.example.watermanagement.dto.HouseholdRequest;
import com.example.watermanagement.entity.Household;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

/**
 * 村民管理 Service
 */
public interface HouseholdService {

    /**
     * 分页查询村民列表（支持按村名、水表编号筛选）
     *
     * @param villageNames  村名列表（多选）
     * @param waterMeterId  水表编号模糊搜索
     * @param pageable      分页参数
     * @return 分页结果
     */
    Page<Household> list(List<String> villageNames, String waterMeterId, Pageable pageable);

    /**
     * 根据 ID 查询
     */
    Household getById(Long id);

    /**
     * 新增村民（同时创建材料费账单）
     */
    Household add(HouseholdRequest request);

    /**
     * 更新村民信息
     */
    Household update(Long id, HouseholdRequest request);

    /**
     * 软删除（将 is_active 置为 false）
     */
    void delete(Long id);

    /**
     * 查询所有活跃用户（用于抄表模板导出等）
     */
    List<Household> findAllActive();

    /**
     * 导出村民数据到 Excel
     */
    void exportToExcel(List<String> villageNames, String waterMeterId,
                       jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException;

    /**
     * 从 Excel 批量导入村民
     * @return 导入结果 {total, message} 或 {conflicts, message}
     */
    java.util.Map<String, Object> importFromExcel(java.io.InputStream inputStream);
}
