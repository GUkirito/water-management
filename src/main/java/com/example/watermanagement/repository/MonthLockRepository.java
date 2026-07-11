package com.example.watermanagement.repository;

import com.example.watermanagement.entity.MonthLock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MonthLockRepository extends JpaRepository<MonthLock, Long> {

    @Query("select (count(m) > 0) from MonthLock m where m.billYear = :billYear and m.billMonth = :billMonth and (m.locked = true or m.locked is null)")
    boolean existsByBillYearAndBillMonth(Integer billYear, Integer billMonth);

    Optional<MonthLock> findByBillYearAndBillMonth(Integer billYear, Integer billMonth);

    void deleteByBillYearAndBillMonth(Integer billYear, Integer billMonth);

    @Query("select m from MonthLock m where m.locked = true or m.locked is null order by m.billYear desc, m.billMonth desc")
    List<MonthLock> findAllByOrderByBillYearDescBillMonthDesc();
}
