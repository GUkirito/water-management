package com.example.watermanagement.service;

import com.example.watermanagement.dto.MaterialRecordRequest;
import com.example.watermanagement.entity.MaterialPayment;
import com.example.watermanagement.entity.MaterialRecord;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public interface MaterialRecordService {

    Page<MaterialRecord> list(String villageName, String status, String keyword,
                              LocalDate paidDateFrom, LocalDate paidDateTo, Pageable pageable);

    MaterialRecord getById(Long id);

    MaterialRecord create(MaterialRecordRequest request);

    MaterialRecord update(Long id, MaterialRecordRequest request);

    void delete(Long id);

    void batchDelete(List<Long> ids);

    Map<String, Object> importFromExcel(InputStream inputStream, String defaultCollector);

    void exportToExcel(String villageName, String status, String keyword,
                       LocalDate paidDateFrom, LocalDate paidDateTo,
                       HttpServletResponse response) throws IOException;

    MaterialPayment collect(Long recordId, BigDecimal amount, LocalDate paidDate,
                            String collector, String note);

    List<MaterialPayment> getPayments(Long recordId);
}
