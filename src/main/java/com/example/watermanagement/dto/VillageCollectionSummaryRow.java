package com.example.watermanagement.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VillageCollectionSummaryRow {

    private String villageName;

    private long householdCount;

    private BigDecimal waterCharge;

    private BigDecimal actualWaterPaid;

    private BigDecimal unpaidAmount;

    private long unpaidHouseholdCount;

    private BigDecimal collectionRate;

    private long abnormalReadingCount;
}
