package com.trading.analysis;

import com.trading.config.DatabaseConfig;
import com.trading.data.repository.IndicatorRepository;
import com.trading.data.repository.MarketDataRepository;
import com.trading.domain.Price;
import com.trading.domain.TradingSignal;
import com.trading.service.IndicatorService;
import com.trading.service.SignalScorer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Validates signal quality by testing historical signals against actual outcomes
 */
public class SignalValidator {
    
    private static final Logger logger = LoggerFactory.getLogger(SignalValidator.class);
    
    private static final List<String> TEST_SYMBOLS = List.of(
        "VUSA.L", "VWRL.L", "SGLN.L", "HSBA.L", "BP.L",
        "AZN.L", "GLEN.L", "RIO.L", "IITU.L", "INRG.L"
    );
    
    public static void main(String[] args) {
        logger.info("Starting Signal Validation...");
        
        DataSource dataSource = DatabaseConfig.getDataSource();
        MarketDataRepository marketDataRepo = new MarketDataRepository(dataSource);
        IndicatorRepository indicatorRepo = new IndicatorRepository(dataSource);
        
        // Test different lookback periods
        testPeriod(marketDataRepo, indicatorRepo, 30, "30 days ago");
        testPeriod(marketDataRepo, indicatorRepo, 60, "60 days ago");
        
        // Test different score thresholds
        logger.info("\n=== Threshold Sensitivity Analysis ===");
        for (int threshold = 55; threshold <= 75; threshold += 5) {
            testThreshold(marketDataRepo, indicatorRepo, 30, threshold);
        }
    }
    
    private static void testPeriod(
            MarketDataRepository marketDataRepo,
            IndicatorRepository indicatorRepo,
            int daysAgo,
            String label) {
        
        logger.info("\n=== Testing Signals from {} ===", label);
        
        LocalDate signalDate = LocalDate.now().minusDays(daysAgo);
        LocalDate outcomeDate = signalDate.plusDays(10); // 10-day forward return
        
        List<SignalOutcome> outcomes = new ArrayList<>();
        SignalScorer scorer = new SignalScorer();
        
        for (String symbol : TEST_SYMBOLS) {
            try {
                // Get historical price at signal date
                Optional<Price> entryPrice = getPriceOnDate(marketDataRepo, symbol, signalDate);
                if (entryPrice.isEmpty()) continue;
                
                // Generate signal as of that date (using indicators from that date)
                IndicatorService.IndicatorSummary indicators = 
                    getHistoricalIndicators(indicatorRepo, marketDataRepo, symbol, signalDate);
                
                TradingSignal signal = scorer.generateSignal(symbol, indicators, entryPrice.get());
                
                // Get actual outcome 10 days later
                Optional<Price> exitPrice = getPriceOnDate(marketDataRepo, symbol, outcomeDate);
                if (exitPrice.isEmpty()) continue;
                
                // Calculate return
                BigDecimal returnPct = exitPrice.get().value()
                    .subtract(entryPrice.get().value())
                    .divide(entryPrice.get().value(), 6, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
                
                outcomes.add(new SignalOutcome(
                    symbol, signal.signalType(), signal.score(), returnPct
                ));
                
            } catch (Exception e) {
                logger.error("Failed to test {}: {}", symbol, e.getMessage());
            }
        }
        
        // Analyze results
        analyzeOutcomes(outcomes, 60); // Using score threshold of 60
    }
    
    private static void testThreshold(
            MarketDataRepository marketDataRepo,
            IndicatorRepository indicatorRepo,
            int daysAgo,
            int scoreThreshold) {
        
        LocalDate signalDate = LocalDate.now().minusDays(daysAgo);
        LocalDate outcomeDate = signalDate.plusDays(10);
        
        List<SignalOutcome> outcomes = new ArrayList<>();
        SignalScorer scorer = new SignalScorer();
        
        for (String symbol : TEST_SYMBOLS) {
            try {
                Optional<Price> entryPrice = getPriceOnDate(marketDataRepo, symbol, signalDate);
                if (entryPrice.isEmpty()) continue;
                
                IndicatorService.IndicatorSummary indicators = 
                    getHistoricalIndicators(indicatorRepo, marketDataRepo, symbol, signalDate);
                
                TradingSignal signal = scorer.generateSignal(symbol, indicators, entryPrice.get());
                
                Optional<Price> exitPrice = getPriceOnDate(marketDataRepo, symbol, outcomeDate);
                if (exitPrice.isEmpty()) continue;
                
                BigDecimal returnPct = exitPrice.get().value()
                    .subtract(entryPrice.get().value())
                    .divide(entryPrice.get().value(), 6, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
                
                outcomes.add(new SignalOutcome(
                    symbol, signal.signalType(), signal.score(), returnPct
                ));
                
            } catch (Exception e) {
                // Silent fail for threshold testing
            }
        }
        
        analyzeOutcomes(outcomes, scoreThreshold);
    }
    
    private static void analyzeOutcomes(List<SignalOutcome> outcomes, int scoreThreshold) {
        // Filter to BUY signals above threshold
        List<SignalOutcome> buySignals = outcomes.stream()
            .filter(o -> o.signalType == TradingSignal.SignalType.BUY)
            .filter(o -> o.score >= scoreThreshold)
            .toList();
        
        if (buySignals.isEmpty()) {
            logger.warn("No BUY signals with score ≥{}", scoreThreshold);
            return;
        }
        
        // Calculate metrics
        long winners = buySignals.stream()
            .filter(o -> o.returnPct.compareTo(BigDecimal.ZERO) > 0)
            .count();
        
        BigDecimal avgReturn = buySignals.stream()
            .map(o -> o.returnPct)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .divide(BigDecimal.valueOf(buySignals.size()), 2, RoundingMode.HALF_UP);
        
        BigDecimal avgWin = buySignals.stream()
            .filter(o -> o.returnPct.compareTo(BigDecimal.ZERO) > 0)
            .map(o -> o.returnPct)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .divide(BigDecimal.valueOf(Math.max(winners, 1)), 2, RoundingMode.HALF_UP);
        
        long losers = buySignals.size() - winners;
        BigDecimal avgLoss = buySignals.stream()
            .filter(o -> o.returnPct.compareTo(BigDecimal.ZERO) <= 0)
            .map(o -> o.returnPct)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .divide(BigDecimal.valueOf(Math.max(losers, 1)), 2, RoundingMode.HALF_UP);
        
        double winRate = (double) winners / buySignals.size() * 100;
        
        // Simple Sharpe-like ratio (avg return / volatility)
        BigDecimal variance = buySignals.stream()
            .map(o -> o.returnPct.subtract(avgReturn).pow(2))
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .divide(BigDecimal.valueOf(buySignals.size()), 6, RoundingMode.HALF_UP);
        double stdDev = Math.sqrt(variance.doubleValue());
        double sharpe = stdDev > 0 ? avgReturn.doubleValue() / stdDev : 0;
        
        // Output
        logger.info("Threshold ≥{}: {} signals, {:.1f}% win rate, avg return {:.2f}%, " +
                    "avg win {:.2f}%, avg loss {:.2f}%, Sharpe-like {:.2f}",
            scoreThreshold, buySignals.size(), winRate, avgReturn, avgWin, avgLoss, sharpe);
        
        // Quality assessment
        if (winRate >= 60 && avgReturn.compareTo(BigDecimal.valueOf(1)) > 0) {
            logger.info("✅ GOOD threshold - high win rate and positive returns");
        } else if (winRate >= 50 && avgReturn.compareTo(BigDecimal.ZERO) > 0) {
            logger.info("⚠️ ACCEPTABLE threshold - marginal edge");
        } else {
            logger.warn("❌ POOR threshold - losing strategy at this level");
        }
    }
    
    private static Optional<Price> getPriceOnDate(
            MarketDataRepository repo, 
            String symbol, 
            LocalDate date) {
        
        return repo.findBySymbolAndDate(symbol, date)
            .map(md -> md.close());
    }
    
    private static IndicatorService.IndicatorSummary getHistoricalIndicators(
            IndicatorRepository indicatorRepo,
            MarketDataRepository marketDataRepo,
            String symbol,
            LocalDate date) {
        
        // Get indicators closest to this date
        // Note: In a real system, you'd recalculate indicators as of that date
        // For now, we'll use the closest available
        
        var sma20 = indicatorRepo.getLatest(symbol, "SMA_20");
        var sma50 = indicatorRepo.getLatest(symbol, "SMA_50");
        var sma200 = indicatorRepo.getLatest(symbol, "SMA_200");
        var rsi = indicatorRepo.getLatest(symbol, "RSI_14");
        var macd = indicatorRepo.getLatest(symbol, "MACD_12_26_9");
        var roc10 = indicatorRepo.getLatest(symbol, "ROC_10");
        var roc5 = indicatorRepo.getLatest(symbol, "ROC_5");
        
        Long volume = marketDataRepo.getLatestVolume(symbol).orElse(0L);
        Long avgVol = marketDataRepo.getAverageVolume(symbol, 20).orElse(0L);
        
        return new IndicatorService.IndicatorSummary(
            symbol, sma20, sma50, sma200, rsi, macd, roc10, roc5, volume, avgVol
        );
    }
    
    record SignalOutcome(
        String symbol,
        TradingSignal.SignalType signalType,
        int score,
        BigDecimal returnPct
    ) {}
}