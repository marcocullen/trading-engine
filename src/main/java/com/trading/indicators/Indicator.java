package com.trading.indicators;

import com.trading.domain.IndicatorResult;
import com.trading.domain.MarketData;

import java.util.List;

/**
 * Base interface for all technical indicators
 */
public interface Indicator {
    
    /**
     * Calculate indicator values for a list of market data
     * @param marketData Historical price data (must be chronologically ordered)
     * @return List of indicator results
     */
    List<IndicatorResult> calculate(List<MarketData> marketData);
    
    /**
     * Get the name of this indicator
     */
    String getName();
    
    /**
     * Get the minimum number of data points required for calculation
     */
    int getMinDataPoints();
}

