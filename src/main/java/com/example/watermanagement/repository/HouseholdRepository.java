package com.example.watermanagement.repository;

import com.example.watermanagement.entity.Household;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 村民/水表信息 Repository
 */
@Repository
public interface HouseholdRepository extends JpaRepository<Household, Long>,
        JpaSpecificationExecutor<Household> {

    /**
     * 根据水表编号查找（用于唯一性校验）
     */
    Optional<Household> findByWaterMeterId(String waterMeterId);

    /**
     * 按村名列表 + 水表编号模糊搜索 + 仅活跃（分页）
     */
    Page<Household> findByVillageNameInAndWaterMeterIdContainingAndIsActiveTrue(
            List<String> villageNames, String waterMeterId, Pageable pageable);

    @Query("""
            SELECT h FROM Household h
            WHERE h.isActive = true
              AND h.villageName IN :villageNames
              AND (h.householdName LIKE :keyword
                   OR h.waterMeterId LIKE :keyword
                   OR h.villageName LIKE :keyword)
            """)
    Page<Household> searchActiveByVillagesAndKeyword(
            @Param("villageNames") List<String> villageNames,
            @Param("keyword") String keyword,
            Pageable pageable);

    /**
     * 按村名列表筛选活跃用户（分页）
     */
    Page<Household> findByVillageNameInAndIsActiveTrue(
            List<String> villageNames, Pageable pageable);

    /**
     * 按水表编号模糊搜索活跃用户（分页）
     */
    Page<Household> findByWaterMeterIdContainingAndIsActiveTrue(
            String waterMeterId, Pageable pageable);

    @Query("""
            SELECT h FROM Household h
            WHERE h.isActive = true
              AND (h.householdName LIKE :keyword
                   OR h.waterMeterId LIKE :keyword
                   OR h.villageName LIKE :keyword)
            """)
    Page<Household> searchActiveByKeyword(@Param("keyword") String keyword, Pageable pageable);

    /**
     * 分页查所有活跃用户
     */
    Page<Household> findByIsActiveTrue(Pageable pageable);

    /**
     * 查询所有活跃用户（不分页，用于 Excel 导出）
     */
    List<Household> findByIsActiveTrue();

    /**
     * 按村名列表查所有活跃用户（不分页，用于 Excel 导出）
     */
    List<Household> findByVillageNameInAndIsActiveTrue(List<String> villageNames);

    /**
     * 判断指定水表编号是否已存在（用于新增时唯一性校验）
     */
    boolean existsByWaterMeterId(String waterMeterId);
}
