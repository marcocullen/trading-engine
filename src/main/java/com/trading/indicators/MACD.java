package com.trading.indicators;

import com.trading.domain.IndicatorResult;
import com.trading.domain.MACDResult;
import com.trading.domain.MarketData;
import com.trading.domain.Price;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * MACD (Moving Average Convergence Divergence) Indicator
 * Components:
 * - MACD Line = EMA(12) - EMA(26)
 * - Signal Line = EMA(9) of MACD Line
 * - Histogram = MACD Line - Signal Line
 * <p>
 * Signals:
 * - MACD crosses above Signal → Bullish (BUY)
 * - MACD crosses below Signal → Bearish (SELL)
 * - Histogram > 0 → Bullish momentum
 * - Histogram < 0 → Bearish momentum
 */
public class MACD implements Indicator {
    
    private final int fastPeriod;    // Typically 12
    private final int slowPeriod;    // Typically 26
    private final int signalPeriod;  // Typically 9
    
    /**
     * Create MACD with standard parameters (12, 26, 9)
     */
    public MACD() {
        this(12, 26, 9);
    }
    
    /**
     * Create MACD with custom parameters
     */
    public MACD(int fastPeriod, int slowPeriod, int signalPeriod) {
        if (fastPeriod >= slowPeriod) {
            throw new IllegalArgumentException("Fast period must be less than slow period");
        }
        this.fastPeriod = fastPeriod;
        this.slowPeriod = slowPeriod;
        this.signalPeriod = signalPeriod;
    }
    
    @Override
    public List<IndicatorResult> calculate(List<MarketData> marketData) {
        int minPoints = slowPeriod + signalPeriod;
        if (marketData.size() < minPoints) {
            throw new IllegalArgumentException(
                String.format("Need at least %d data points for MACD, got %d", minPoints, marketData.size())
            );
        }
        
        String symbol = marketData.getFirst().symbol();
        
        // Extract prices
        List<Price> prices = marketData.stream()
            .map(MarketData::close)
            .toList();
        
        // Calculate fast and slow EMAs
        List<BigDecimal> fastEMA = ExponentialMovingAverage.calculateSeries(prices, fastPeriod);
        List<BigDecimal> slowEMA = ExponentialMovingAverage.calculateSeries(prices, slowPeriod);
        
        // Calculate MACD line (fast EMA - slow EMA)
        // Note: slowEMA is shorter, so we need to align them
        List<BigDecimal> macdLine = new ArrayList<>();
        int offset = slowPeriod - fastPeriod;
        
        for (int i = 0; i < slowEMA.size(); i++) {
            BigDecimal macd = fastEMA.get(i + offset).subtract(slowEMA.get(i));
            macdLine.add(macd);
        }
        
        // Calculate signal line (9-period EMA of MACD line)
        List<BigDecimal> signalLine = calculateEMAOfSeries(macdLine, signalPeriod);
        
        // Build results with histogram
        List<IndicatorResult> results = new ArrayList<>();
        int startIndex = slowPeriod + signalPeriod - 2;
        
        for (int i = 0; i < signalLine.size(); i++) {
            int dataIndex = startIndex + i;
            BigDecimal macd = macdLine.get(signalPeriod - 1 + i);
            BigDecimal signal = signalLine.get(i);
            BigDecimal histogram = macd.subtract(signal).setScale(6, RoundingMode.HALF_UP);
            
            // Store as JSON metadata
            String metadata = String.format(
                "{\"macdLine\":%.6f,\"signalLine\":%.6f,\"histogram\":%.6f}",
                macd, signal, histogram
            );
            
            results.add(IndicatorResult.of(
                symbol,
                marketData.get(dataIndex).date(),
                getName(),
                histogram,  // Store histogram as the primary value
                metadata
            ));
        }
        
        return results;
    }
    
    @Override
    public String getName() {
        return String.format("MACD_%d_%d_%d", fastPeriod, slowPeriod, signalPeriod);
    }
    
    @Override
    public int getMinDataPoints() {
        return slowPeriod + signalPeriod;
    }
    
    /**
     * Calculate EMA of a BigDecimal series (for signal line)
     */
    private List<BigDecimal> calculateEMAOfSeries(List<BigDecimal> series, int period) {
        if (series.size() < period) {
            throw new IllegalArgumentException("Not enough data for EMA calculation");
        }
        
        List<BigDecimal> emas = new ArrayList<>();
        BigDecimal alpha = BigDecimal.valueOf(2)
            .divide(BigDecimal.valueOf(period + 1), 10, RoundingMode.HALF_UP);
        
        // Initialize with SMA
        BigDecimal sum = BigDecimal.ZERO;
        for (int i = 0; i < period; i++) {
            sum = sum.add(series.get(i));
        }
        BigDecimal previousEMA = sum.divide(BigDecimal.valueOf(period), 10, RoundingMode.HALF_UP);
        emas.add(previousEMA);
        
        // Calculate EMA for rest
        for (int i = period; i < series.size(); i++) {
            BigDecimal ema = series.get(i).multiply(alpha)
                .add(previousEMA.multiply(BigDecimal.ONE.subtract(alpha)))
                .setScale(6, RoundingMode.HALF_UP);
            emas.add(ema);
            previousEMA = ema;
        }
        
        return emas;
    }
    
    /**
     * Get full MACD results with all components
     */
    public List<MACDResult> calculateDetailed(List<MarketData> marketData) {
        List<IndicatorResult> results = calculate(marketData);
        List<MACDResult> detailedResults = new ArrayList<>();
        
        for (IndicatorResult result : results) {
            // Parse metadata JSON (simplified - in production use a JSON library)
            String metadata = result.metadata();
            BigDecimal macdLine = extractValue(metadata, "macdLine");
            BigDecimal signalLine = extractValue(metadata, "signalLine");
            BigDecimal histogram = result.value();
            
            detailedResults.add(new MACDResult(result.date(), macdLine, signalLine, histogram));
        }
        
        return detailedResults;
    }
    
    private BigDecimal extractValue(String json, String key) {
        // Simple JSON value extraction - not production grade!
        String pattern = "\"" + key + "\":([0-9.-]+)";
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern);
        java.util.regex.Matcher m = p.matcher(json);
        if (m.find()) {
            return new BigDecimal(m.group(1));
        }
        return BigDecimal.ZERO;
    }
}