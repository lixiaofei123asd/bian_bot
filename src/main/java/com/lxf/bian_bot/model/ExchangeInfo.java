package com.lxf.bian_bot.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExchangeInfo {

    private String symbol;
    private String status;
    private String baseAsset;
    private String quoteAsset;
    private Long pricePrecision;
    private Long quantityPrecision;

}
