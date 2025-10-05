package com.trading.service;

import com.trading.domain.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Calculates trading signals by scoring multiple technical indicators
 */
public class SignalScorer {
    
    private static final Logger logger = LoggerFactory.getLogger(SignalScorer.class);
    
    /**
     * Generate a trading signal from indicator summary
     */
    public TradingSignal generateSignal(
            String symbol,
            IndicatorService.IndicatorSummary indicators,
            Price currentPrice) {
        
        if (indicators == null) {
            logger.warn("No indicators available for {}", symbol);
            return createHoldSignal(symbol, currentPrice, "Insufficient data");
        }
        
        // Calculate individual components
        int trendScore = calculateTrendScore(indicators);
        int momentumScore = calculateMomentumScore(indicators);
        int valueScore = calculateValueScore(indicators);
        int confluenceBonus = calculateConfluenceBonus(trendScore, momentumScore, valueScore);
        
        SignalComponents components = new SignalComponents(
            trendScore, momentumScore, valueScore, confluenceBonus
        );
        
        int totalScore = components.totalScore();
        
        // Determine signal type and strength
        TradingSignal.SignalType signalType = determineSignalType(totalScore, indicators);
        TradingSignal.SignalStrength strength = TradingSignal.strengthFromScore(totalScore);
        
        // Build reasoning
        String reasoning = buildReasoning(indicators, components, totalScore);
        
        return new TradingSignal(
            symbol,
            indicators.sma20() != null ? indicators.sma20().date() : java.time.LocalDate.now(),
            signalType,
            totalScore,
            strength,
            currentPrice.value(),
            reasoning,
            components
        );
    }
    
    /**
     * Calculate trend score (0-30 points)
     */
    private int calculateTrendScore(IndicatorService.IndicatorSummary indicators) {
        int score = 0;
        
        if (indicators.sma20() == null || indicators.sma50() == null) {
            return 0;
        }
        
        BigDecimal sma20 = indicators.sma20().value();
        BigDecimal sma50 = indicators.sma50().value();
        BigDecimal sma200 = indicators.sma200() != null ? indicators.sma200().value() : null;
        
        // Strong uptrend: SMA(20) > SMA(50) > SMA(200)
        if (sma200 != null && sma20.compareTo(sma50) > 0 && sma50.compareTo(sma200) > 0) {
            score = 30;
        }
        // Moderate uptrend: SMA(20) > SMA(50)
        else if (sma20.compareTo(sma50) > 0) {
            score = 20;
        }
        // Weak bullish: just SMA(20) slightly above SMA(50)
        else if (sma20.compareTo(sma50.multiply(BigDecimal.valueOf(0.99))) > 0) {
            score = 10;
        }
        
        return score;
    }
    
    /**
     * Calculate momentum score (0-30 points)
     */
    private int calculateMomentumScore(IndicatorService.IndicatorSummary indicators) {
        if (indicators.macd() == null) {
            return 0;
        }
        
        BigDecimal histogram = indicators.macd().value();
        
        // Strong positive momentum
        if (histogram.compareTo(BigDecimal.ZERO) > 0) {
            // Check magnitude - if histogram is large relative to typical values
            if (histogram.abs().compareTo(BigDecimal.valueOf(10)) > 0) {
                return 30;  // Strong momentum
            } else {
                return 20;  // Moderate positive momentum
            }
        }
        // Slightly positive (early momentum)
        else if (histogram.compareTo(BigDecimal.valueOf(-5)) > 0) {
            return 10;
        }
        
        return 0;
    }
    
    /**
     * Calculate value score (0-30 points, can be negative for overbought)
     */
    private int calculateValueScore(IndicatorService.IndicatorSummary indicators) {
        if (indicators.rsi() == null) {
            return 0;
        }
        
        BigDecimal rsi = indicators.rsi().value();
        
        // Oversold - great value
        if (rsi.compareTo(BigDecimal.valueOf(30)) < 0) {
            return 30;
        }
        // Slightly oversold
        else if (rsi.compareTo(BigDecimal.valueOf(40)) < 0) {
            return 20;
        }
        // Neutral range
        else if (rsi.compareTo(BigDecimal.valueOf(60)) <= 0) {
            return 10;
        }
        // Overbought - avoid (negative score)
        else if (rsi.compareTo(BigDecimal.valueOf(70)) > 0) {
            return -20;  // Penalty for overbought
        }
        
        return 0;
    }
    
    /**
     * Calculate confluence bonus (0-10 points)
     * Awarded when all signals align
     */
    private int calculateConfluenceBonus(int trendScore, int momentumScore, int valueScore) {
        // All three components are positive (aligned bullish signals)
        if (trendScore > 0 && momentumScore > 0 && valueScore > 0) {
            return 10;
        }
        return 0;
    }
    
    /**
     * Determine signal type from score and indicators
     */
    private TradingSignal.SignalType determineSignalType(
            int score, 
            IndicatorService.IndicatorSummary indicators) {
        
        // Strong buy signals
        if (score >= 60) {
            return TradingSignal.SignalType.BUY;
        }
        
        // Sell signals (overbought or breaking down)
        if (indicators.rsi() != null && 
            indicators.rsi().value().compareTo(BigDecimal.valueOf(75)) > 0) {
            return TradingSignal.SignalType.SELL;
        }
        
        if (indicators.getTrendSignal().equals("BEARISH") && 
            score < 30) {
            return TradingSignal.SignalType.SELL;
        }
        
        return TradingSignal.SignalType.HOLD;
    }
    
    /**
     * Build human-readable reasoning
     */
    private String buildReasoning(
            IndicatorService.IndicatorSummary indicators,
            SignalComponents components,
            int totalScore) {
        
        List<String> reasons = new ArrayList<>();
        
        // Trend reasoning
        if (components.trendScore() >= 20) {
            reasons.add("Strong uptrend (SMA20 > SMA50)");
        } else if (components.trendScore() > 0) {
            reasons.add("Weak bullish trend");
        } else {
            reasons.add("Bearish or no clear trend");
        }
        
        // Momentum reasoning
        if (components.momentumScore() >= 20) {
            reasons.add("Positive momentum (MACD bullish)");
        } else if (components.momentumScore() > 0) {
            reasons.add("Early momentum building");
        } else {
            reasons.add("Negative or weak momentum");
        }
        
        // Value reasoning
        if (indicators.rsi() != null) {
            String rsiSignal = indicators.getRSISignal();
            if (rsiSignal.equals("OVERSOLD")) {
                reasons.add("Oversold - good value");
            } else if (rsiSignal.equals("OVERBOUGHT")) {
                reasons.add("OVERBOUGHT - caution!");
            } else {
                reasons.add("Neutral valuation");
            }
        }
        
        // Confluence
        if (components.confluenceBonus() > 0) {
            reasons.add("All signals aligned!");
        }
        
        return String.join(", ", reasons) + " | Score: " + components.breakdown();
    }
    
    /**
     * Create a default HOLD signal
     */
    private TradingSignal createHoldSignal(String symbol, Price price, String reason) {
        return new TradingSignal(
            symbol,
            java.time.LocalDate.now(),
            TradingSignal.SignalType.HOLD,
            0,
            TradingSignal.SignalStrength.AVOID,
            price.value(),
            reason,
            new SignalComponents(0, 0, 0, 0)
        );
    }
}