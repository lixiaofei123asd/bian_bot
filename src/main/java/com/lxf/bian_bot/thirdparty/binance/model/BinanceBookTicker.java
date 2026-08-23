package com.lxf.bian_bot.thirdparty.binance.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import lombok.Data;

@Data
public class BinanceBookTicker {

    @JsonProperty("e")
    private String eventType;

    @JsonProperty("u")
    private Long updateId;

    @JsonProperty("s")
    private String symbol;

    @JsonProperty("ps")
    private String priceSymbol;

    @JsonProperty("b")
    private BigDecimal bidPrice;

    @JsonProperty("B")
    private BigDecimal bidQuantity;

    @JsonProperty("a")
    private BigDecimal askPrice;

    @JsonProperty("A")
    private BigDecimal askQuantity;

    @JsonProperty("T")
    private Long transactionTime;

    @JsonProperty("E")
    private Long eventTime;

    @JsonProperty("st")
    private Integer streamType;
}
