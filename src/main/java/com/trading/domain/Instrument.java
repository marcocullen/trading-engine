package com.trading.domain;

import java.util.Objects;

/**
 * Represents a tradeable instrument (stock, ETF, etc.)
 */
public record Instrument(
    String symbol,
    String name,
    String exchange,
    AssetType assetType,
    String sector,
    String currency
) {
    public Instrument {
        Objects.requireNonNull(symbol, "Symbol cannot be null");
        Objects.requireNonNull(name, "Name cannot be null");
        Objects.requireNonNull(assetType, "Asset type cannot be null");
    }
    
    public enum AssetType {
        STOCK, ETF, INDEX
    }
}