package com.trading.indicators;

import com.trading.domain.IndicatorResult;
import com.trading.domain.MarketData;
import com.trading.domain.Price;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Simple Moving Average (SMA) Indicator
 * 
 * Formula: SMA = (P1 + P2 + ... + Pn) / n
 * 
 * The SMA smooths out price data by creating a constantly updated average price.
 * It's a lagging indicator that helps identify trend direction.
 */
public class SimpleMovingAverage implements Indicator {
    
    private final int period;
    
    /**
     * Create an SMA indicator with the specified period
     * @param period Number of periods to average (e.g., 20, 50, 200)
     */
    public SimpleMovingAverage(int period) {
        if (period < 2) {
            throw new IllegalArgumentException("SMA period must be at least 2");
        }
        this.period = period;
    }
    
    @Override
    public List<IndicatorResult> calculate(List<MarketData> marketData) {
        if (marketData.size() < period) {
            throw new IllegalArgumentException(
                String.format("Need at least %d data points, got %d", period, marketData.size())
            );
        }
        
        List<IndicatorResult> results = new ArrayList<>();
        String symbol = marketData.get(0).symbol();
        
        // Start calculating SMA once we have enough data points
        for (int i = period - 1; i < marketData.size(); i++) {
            BigDecimal sum = BigDecimal.ZERO;
            
            // Sum the last 'period' closing prices
            for (int j = i - period + 1; j <= i; j++) {
                sum = sum.add(marketData.get(j).close().value());
            }
            
            // Calculate average
            BigDecimal sma = sum.divide(
                BigDecimal.valueOf(period), 
                6, 
                RoundingMode.HALF_UP
            );
            
            results.add(IndicatorResult.of(
                symbol,
                marketData.get(i).date(),
                getName(),
                sma
            ));
        }
        
        return results;
    }
    
    @Override
    public String getName() {
        return "SMA_" + period;
    }
    
    @Override
    public int getMinDataPoints() {
        return period;
    }
    
    /**
     * Utility: Calculate single SMA value from a list of prices
     */
    public static BigDecimal calculateSingle(List<Price> prices, int period) {
        if (prices.size() < period) {
            throw new IllegalArgumentException("Not enough prices for SMA calculation");
        }
        
        BigDecimal sum = BigDecimal.ZERO;
        for (int i = prices.size() - period; i < prices.size(); i++) {
            sum = sum.add(prices.get(i).value());
        }
        
        return sum.divide(BigDecimal.valueOf(period), 6, RoundingMode.HALF_UP);
    }
}