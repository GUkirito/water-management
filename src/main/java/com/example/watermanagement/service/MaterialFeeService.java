package com.example.watermanagement.service;

import com.example.watermanagement.dto.MaterialCollectRequest;
import com.example.watermanagement.dto.MaterialFeeRow;
import com.example.watermanagement.entity.Payment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

/**
 * 材料费管理 Service
 */
public interface MaterialFeeService {

    /**
     * 按村名分页查询材料费列表
     * <p>
     * 以 households 为主表，LEFT JOIN 语义：显示该村所有户，
     * 即使没有 material_bills 记录也返回默认数据。
     */
    Page<MaterialFeeRow> listByVillage(String villageName, Pageable pageable);

    /**
     * 收取材料费
     * <p>
     * 更新 material_bills（actualPaid, status, paidAt, collector）
     * 同时写入 payments 表。
     *
     * @return 缴费记录
     */
    Payment collect(MaterialCollectRequest request);

    /**
     * 查询某水表的材料费缴费历史
     */
    List<Payment> getHistory(String waterMeterId);
}
