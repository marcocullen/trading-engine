package com.trading.service;

import com.trading.domain.TradingSignal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Calculates position sizes based on risk management rules
 */
public class PositionSizer {
    
    private static final Logger logger = LoggerFactory.getLogger(PositionSizer.class);
    
    private final BigDecimal portfolioValue;
    private final PositionSizingStrategy strategy;
    private final RiskParameters riskParams;
    
    public PositionSizer(
            BigDecimal portfolioValue, 
            PositionSizingStrategy strategy,
            RiskParameters riskParams) {
        this.portfolioValue = portfolioValue;
        this.strategy = strategy;
        this.riskParams = riskParams;
    }
    
    /**
     * Calculate position size for a trading signal
     */
    public PositionSize calculatePositionSize(TradingSignal signal) {
        BigDecimal positionValue = switch (strategy) {
            case FIXED_PERCENTAGE -> calculateFixedPercentage(signal);
            case RISK_BASED -> calculateRiskBased(signal);
            case SIGNAL_STRENGTH -> calculateSignalStrength(signal);
            case EQUAL_WEIGHT -> calculateEqualWeight();
        };
        
        // Apply position limits
        positionValue = applyLimits(positionValue, signal);
        
        // Calculate number of shares
        BigDecimal shares = positionValue.divide(
            signal.currentPrice(), 
            0, 
            RoundingMode.DOWN  // Always round down to avoid over-investing
        );
        
        // Recalculate actual investment (shares * price)
        BigDecimal actualInvestment = shares.multiply(signal.currentPrice());
        
        // Calculate stop loss
        BigDecimal stopLossPrice = calculateStopLoss(signal);
        BigDecimal riskAmount = shares.multiply(signal.currentPrice().subtract(stopLossPrice));
        
        return new PositionSize(
            signal.symbol(),
            shares,
            signal.currentPrice(),
            actualInvestment,
            stopLossPrice,
            riskAmount,
            actualInvestment.divide(portfolioValue, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))  // Portfolio %
        );
    }
    
    /**
     * Fixed percentage of portfolio per position
     */
    private BigDecimal calculateFixedPercentage(TradingSignal signal) {
        return portfolioValue.multiply(riskParams.maxPositionPercent())
            .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }
    
    /**
     * Risk-based sizing: risk only X% of portfolio on this trade
     */
    private BigDecimal calculateRiskBased(TradingSignal signal) {
        BigDecimal riskPerTrade = portfolioValue
            .multiply(riskParams.riskPerTradePercent())
            .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        
        // Calculate stop loss distance
        BigDecimal stopLoss = calculateStopLoss(signal);
        BigDecimal stopLossDistance = signal.currentPrice().subtract(stopLoss);
        
        if (stopLossDistance.compareTo(BigDecimal.ZERO) <= 0) {
            // Fallback to fixed percentage if stop loss calculation fails
            return calculateFixedPercentage(signal);
        }
        
        // Position size = Risk Amount / Stop Loss Distance
        return riskPerTrade.divide(stopLossDistance, 2, RoundingMode.HALF_UP);
    }
    
    /**
     * Scale position size based on signal strength
     */
    private BigDecimal calculateSignalStrength(TradingSignal signal) {
        BigDecimal basePosition = portfolioValue
            .multiply(riskParams.maxPositionPercent())
            .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        
        // Scale based on score
        // Score 60 = 60% of max position
        // Score 100 = 100% of max position
        BigDecimal scaleFactor = BigDecimal.valueOf(signal.score())
            .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        
        return basePosition.multiply(scaleFactor);
    }
    
    /**
     * Equal weight across all positions
     */
    private BigDecimal calculateEqualWeight() {
        // Assume max 10 positions
        return portfolioValue.divide(
            BigDecimal.valueOf(10), 
            2, 
            RoundingMode.HALF_UP
        );
    }
    
    /**
     * Apply position size limits
     */
    private BigDecimal applyLimits(BigDecimal positionValue, TradingSignal signal) {
        // Maximum position size (% of portfolio)
        BigDecimal maxPosition = portfolioValue
            .multiply(riskParams.maxPositionPercent())
            .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        
        // Minimum position size (to avoid tiny trades)
        BigDecimal minPosition = riskParams.minPositionValue();
        
        // Apply limits
        if (positionValue.compareTo(maxPosition) > 0) {
            logger.warn("Position size {} exceeds max {}, limiting", positionValue, maxPosition);
            return maxPosition;
        }
        
        if (positionValue.compareTo(minPosition) < 0) {
            logger.warn("Position size {} below minimum {}, skipping", positionValue, minPosition);
            return BigDecimal.ZERO;  // Signal to skip this trade
        }
        
        return positionValue;
    }
    
    /**
     * Calculate stop loss price
     * For long positions: price - (price * stop loss %)
     */
    private BigDecimal calculateStopLoss(TradingSignal signal) {
        BigDecimal stopLossPercent = riskParams.stopLossPercent();
        
        return signal.currentPrice().subtract(
            signal.currentPrice()
                .multiply(stopLossPercent)
                .divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP)
        );
    }
    
    /**
     * Position sizing strategy
     */
    public enum PositionSizingStrategy {
        FIXED_PERCENTAGE,   // Fixed % of portfolio (e.g., 5%)
        RISK_BASED,         // Based on stop loss distance (risk 2% per trade)
        SIGNAL_STRENGTH,    // Scale with signal score
        EQUAL_WEIGHT        // Equal allocation across positions
    }
}

