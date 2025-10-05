package com.trading.service;

import com.trading.data.repository.IndicatorRepository;
import com.trading.data.repository.MarketDataRepository;
import com.trading.domain.IndicatorResult;
import com.trading.domain.MarketData;
import com.trading.indicators.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Service for calculating and persisting technical indicators
 */
public class IndicatorService {
    
    private static final Logger logger = LoggerFactory.getLogger(IndicatorService.class);
    
    private final MarketDataRepository marketDataRepo;
    private final IndicatorRepository indicatorRepo;
    
    // Standard indicators we calculate
    private final List<Indicator> indicators;
    
    public IndicatorService(MarketDataRepository marketDataRepo, IndicatorRepository indicatorRepo) {
        this.marketDataRepo = marketDataRepo;
        this.indicatorRepo = indicatorRepo;
        
        // Initialize standard indicators
        this.indicators = List.of(
            new SimpleMovingAverage(20),   // 20-day SMA
            new SimpleMovingAverage(50),   // 50-day SMA
            new SimpleMovingAverage(200),  // 200-day SMA
            new ExponentialMovingAverage(12),
            new ExponentialMovingAverage(26),
            new ExponentialMovingAverage(50),
            new MACD(),                    // Standard MACD (12,26,9)
            new RSI()                      // Standard RSI (14)
        );
    }
    
    /**
     * Calculate all indicators for a symbol and persist results
     */
    public void calculateAndStoreIndicators(String symbol) {
        logger.info("Calculating indicators for {}", symbol);
        
        try {
            // Get historical data (last 250 days for 200-day SMA)
            LocalDate endDate = LocalDate.now();
            LocalDate startDate = endDate.minusDays(250);
            
            List<MarketData> marketData = marketDataRepo.findBySymbolBetweenDates(
                symbol, startDate, endDate
            );
            
            if (marketData.isEmpty()) {
                logger.warn("No market data found for {}", symbol);
                return;
            }
            
            logger.info("Loaded {} data points for {}", marketData.size(), symbol);
            
            // Calculate each indicator
            for (Indicator indicator : indicators) {
                try {
                    if (marketData.size() >= indicator.getMinDataPoints()) {
                        List<IndicatorResult> results = indicator.calculate(marketData);
                        indicatorRepo.saveBatch(results);
                        logger.info("Calculated and stored {} for {} ({} values)", 
                            indicator.getName(), symbol, results.size());
                    } else {
                        logger.warn("Not enough data for {} on {} (need {}, have {})",
                            indicator.getName(), symbol, 
                            indicator.getMinDataPoints(), marketData.size());
                    }
                } catch (Exception e) {
                    logger.error("Failed to calculate {} for {}: {}", 
                        indicator.getName(), symbol, e.getMessage());
                }
            }
            
        } catch (Exception e) {
            logger.error("Failed to calculate indicators for {}: {}", symbol, e.getMessage());
            throw new RuntimeException("Indicator calculation failed", e);
        }
    }
    
    /**
     * Calculate indicators for multiple symbols
     */
    public void calculateForSymbols(List<String> symbols) {
        for (String symbol : symbols) {
            calculateAndStoreIndicators(symbol);
        }
    }
    
    /**
     * Get latest indicator summary for a symbol
     */
    public IndicatorSummary getLatestIndicatorSummary(String symbol) {
        IndicatorResult sma20 = indicatorRepo.getLatest(symbol, "SMA_20");
        IndicatorResult sma50 = indicatorRepo.getLatest(symbol, "SMA_50");
        IndicatorResult sma200 = indicatorRepo.getLatest(symbol, "SMA_200");
        IndicatorResult rsi = indicatorRepo.getLatest(symbol, "RSI_14");
        IndicatorResult macd = indicatorRepo.getLatest(symbol, "MACD_12_26_9");
        
        return new IndicatorSummary(symbol, sma20, sma50, sma200, rsi, macd);
    }
    
    /**
     * Summary of key indicators for a symbol
     */
    public record IndicatorSummary(
        String symbol,
        IndicatorResult sma20,
        IndicatorResult sma50,
        IndicatorResult sma200,
        IndicatorResult rsi,
        IndicatorResult macd
    ) {
        /**
         * Get a simple trend signal based on moving averages
         */
        public String getTrendSignal() {
            if (sma20 == null || sma50 == null) {
                return "INSUFFICIENT_DATA";
            }
            
            // Golden Cross: SMA20 > SMA50 → Bullish
            // Death Cross: SMA20 < SMA50 → Bearish
            if (sma20.value().compareTo(sma50.value()) > 0) {
                return "BULLISH";
            } else if (sma20.value().compareTo(sma50.value()) < 0) {
                return "BEARISH";
            } else {
                return "NEUTRAL";
            }
        }
        
        /**
         * Get RSI interpretation
         */
        public String getRSISignal() {
            if (rsi == null) {
                return "INSUFFICIENT_DATA";
            }
            return RSI.interpret(rsi.value());
        }
        
        /**
         * Print summary to console
         */
        public void print() {
            System.out.println("\n=== Indicator Summary: " + symbol + " ===");
            if (sma20 != null) {
                System.out.printf("SMA(20):  %.2f%n", sma20.value());
            }
            if (sma50 != null) {
                System.out.printf("SMA(50):  %.2f%n", sma50.value());
            }
            if (sma200 != null) {
                System.out.printf("SMA(200): %.2f%n", sma200.value());
            }
            if (rsi != null) {
                System.out.printf("RSI(14):  %.2f [%s]%n", rsi.value(), getRSISignal());
            }
            if (macd != null) {
                System.out.printf("MACD:     %.4f%n", macd.value());
            }
            System.out.printf("Trend:    %s%n", getTrendSignal());
            System.out.println("================================");
        }
    }
}