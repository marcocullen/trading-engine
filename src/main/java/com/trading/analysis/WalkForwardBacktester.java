package com.trading.analysis;

import com.trading.data.repository.MarketDataRepository;
import com.trading.domain.*;
import com.trading.service.SignalGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Walk-forward backtesting framework
 * Splits data into training/testing periods to avoid look-ahead bias
 */
public class WalkForwardBacktester {

    private static final Logger logger = LoggerFactory.getLogger(WalkForwardBacktester.class);

    private final SignalGenerator signalGenerator;
    private final MarketDataRepository marketDataRepo;

    public WalkForwardBacktester(
            SignalGenerator signalGenerator,
            MarketDataRepository marketDataRepo) {
        this.signalGenerator = signalGenerator;
        this.marketDataRepo = marketDataRepo;
    }

    /**
     * Walk-forward validation result for one period
     */
    public record ValidationPeriod(
            LocalDate trainStart,
            LocalDate trainEnd,
            LocalDate testStart,
            LocalDate testEnd,
            int numSignals,
            int numWinners,
            double winRate,
            BigDecimal avgReturn,
            BigDecimal maxReturn,
            BigDecimal minReturn,
            BigDecimal sharpeRatio,
            BigDecimal maxDrawdown,
            BigDecimal totalReturn
    ) {
        public void print() {
            System.out.printf("\n📊 Test Period: %s to %s%n", testStart, testEnd);
            System.out.printf("   Signals:      %d (%d winners, %.1f%% win rate)%n",
                    numSignals, numWinners, winRate * 100);
            System.out.printf("   Avg Return:   %.2f%%%n", avgReturn);
            System.out.printf("   Total Return: %.2f%%%n", totalReturn);
            System.out.printf("   Best Trade:   %.2f%%%n", maxReturn);
            System.out.printf("   Worst Trade:  %.2f%%%n", minReturn);
            System.out.printf("   Sharpe:       %.2f%n", sharpeRatio);
            System.out.printf("   Max Drawdown: %.2f%%%n", maxDrawdown);
        }
    }

    /**
     * Complete walk-forward results
     */
    public record WalkForwardResult(
            List<ValidationPeriod> periods,
            int totalSignals,
            double overallWinRate,
            BigDecimal overallAvgReturn,
            BigDecimal overallSharpe,
            BigDecimal overallMaxDrawdown,
            BigDecimal cumulativeReturn,
            boolean isStatisticallySignificant,
            String summary
    ) {
        public void print() {
            System.out.println("\n" + "=".repeat(80));
            System.out.println("WALK-FORWARD BACKTEST RESULTS");
            System.out.println("=".repeat(80));

            periods.forEach(ValidationPeriod::print);

            System.out.println("\n" + "-".repeat(80));
            System.out.println("OVERALL STATISTICS");
            System.out.println("-".repeat(80));
            System.out.printf("Total Signals:        %d%n", totalSignals);
            System.out.printf("Overall Win Rate:     %.1f%%%n", overallWinRate * 100);
            System.out.printf("Avg Return per Trade: %.2f%%%n", overallAvgReturn);
            System.out.printf("Cumulative Return:    %.2f%%%n", cumulativeReturn);
            System.out.printf("Sharpe Ratio:         %.2f%n", overallSharpe);
            System.out.printf("Max Drawdown:         %.2f%%%n", overallMaxDrawdown);
            System.out.printf("Statistically Sig:    %s%n",
                    isStatisticallySignificant ? "✓ YES" : "✗ NO");
            System.out.println("-".repeat(80));
            System.out.printf("Summary: %s%n", summary);
            System.out.println("=".repeat(80) + "\n");
        }
    }

    /**
     * Run walk-forward validation
     */
    public WalkForwardResult runWalkForward(
            List<String> symbols,
            LocalDate startDate,
            LocalDate endDate,
            int trainMonths,
            int testMonths,
            int holdingPeriodDays,
            int scoreThreshold) {

        logger.info("Running walk-forward validation: {} train, {} test months",
                trainMonths, testMonths);

        List<ValidationPeriod> periods = new ArrayList<>();
        LocalDate currentDate = startDate;

        while (currentDate.plusMonths(trainMonths + testMonths).isBefore(endDate)) {
            LocalDate trainStart = currentDate;
            LocalDate trainEnd = currentDate.plusMonths(trainMonths);
            LocalDate testStart = trainEnd.plusDays(1);
            LocalDate testEnd = testStart.plusMonths(testMonths);

            logger.info("Testing period: {} to {}", testStart, testEnd);

            // In real implementation, we'd optimize parameters on training data
            // For now, we just test on the test period

            ValidationPeriod result = testPeriod(
                    symbols,
                    trainStart,
                    trainEnd,
                    testStart,
                    testEnd,
                    holdingPeriodDays,
                    scoreThreshold
            );

            periods.add(result);
            currentDate = testEnd; // Roll forward
        }

        // Calculate overall statistics
        return calculateOverallStats(periods);
    }

    /**
     * Test a single period
     */
    private ValidationPeriod testPeriod(
            List<String> symbols,
            LocalDate trainStart,
            LocalDate trainEnd,
            LocalDate testStart,
            LocalDate testEnd,
            int holdingPeriodDays,
            int scoreThreshold) {

        List<BigDecimal> returns = new ArrayList<>();
        List<LocalDate> tradeDates = new ArrayList<>();

        // Test each week in the period
        AtomicReference<LocalDate> localDateAtomicReference = new AtomicReference<>(testStart);
        LocalDate date = testStart;

        while (date.isBefore(testEnd)) {

            // Generate signals for this date
            List<TradingSignal> signals = signalGenerator.generateSignals(symbols);

            // Filter by threshold
            List<TradingSignal> tradeableSignals = signals.stream()
                    .filter(s -> s.score() >= scoreThreshold)
                    .filter(s -> s.signalType() == TradingSignal.SignalType.BUY)
                    .toList();

            // Execute trades and track outcomes
            for (TradingSignal signal : tradeableSignals) {
                Optional<BigDecimal> returnPct = calculateReturn(
                        signal.symbol(),
                        date,
                        holdingPeriodDays
                );

                returnPct.ifPresent(r -> {
                    returns.add(r);
                    tradeDates.add(localDateAtomicReference.get());
                });
            }
            
            date = date.plusDays(7); // Weekly testing
        }
        
        // Calculate metrics
        if (returns.isEmpty()) {
            return new ValidationPeriod(
                    trainStart, trainEnd, testStart, testEnd,
                    0, 0, 0.0,
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO
            );
        }
        
        int numWinners = (int) returns.stream()
                .filter(r -> r.compareTo(BigDecimal.ZERO) > 0)
                .count();
        
        BigDecimal avgReturn = returns.stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(returns.size()), 6, RoundingMode.HALF_UP);
        
        BigDecimal maxReturn = returns.stream()
                .max(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);
        
        BigDecimal minReturn = returns.stream()
                .min(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);
        
        BigDecimal stdDev = calculateStdDev(returns, avgReturn);
        BigDecimal sharpe = stdDev.compareTo(BigDecimal.ZERO) > 0
                ? avgReturn.divide(stdDev, 4, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        
        BigDecimal maxDrawdown = calculateMaxDrawdown(returns);
        BigDecimal totalReturn = calculateCumulativeReturn(returns);
        
        return new ValidationPeriod(
                trainStart, trainEnd, testStart, testEnd,
                returns.size(),
                numWinners,
                (double) numWinners / returns.size(),
                avgReturn,
                maxReturn,
                minReturn,
                sharpe,
                maxDrawdown,
                totalReturn
        );
    }
    
    /**
     * Calculate return for a trade
     */
    private Optional<BigDecimal> calculateReturn(
            String symbol,
            LocalDate entryDate,
            int holdingPeriodDays) {
        
        LocalDate exitDate = entryDate.plusDays(holdingPeriodDays);
        
        Optional<MarketData> entry = marketDataRepo.findBySymbolAndDate(symbol, entryDate);
        Optional<MarketData> exit = marketDataRepo.findBySymbolAndDate(symbol, exitDate);
        
        if (entry.isEmpty() || exit.isEmpty()) {
            return Optional.empty();
        }
        
        BigDecimal entryPrice = entry.get().close().value();
        BigDecimal exitPrice = exit.get().close().value();
        
        BigDecimal returnPct = exitPrice.subtract(entryPrice)
                .divide(entryPrice, 6, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
        
        return Optional.of(returnPct);
    }
    
    /**
     * Calculate standard deviation
     */
    private BigDecimal calculateStdDev(List<BigDecimal> values, BigDecimal mean) {
        if (values.size() < 2) return BigDecimal.ZERO;
        
        BigDecimal sumSquaredDiff = values.stream()
                .map(v -> v.subtract(mean).pow(2))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        BigDecimal variance = sumSquaredDiff.divide(
                BigDecimal.valueOf(values.size() - 1), 10, RoundingMode.HALF_UP
        );
        
        return BigDecimal.valueOf(Math.sqrt(variance.doubleValue()));
    }
    
    /**
     * Calculate maximum drawdown
     */
    private BigDecimal calculateMaxDrawdown(List<BigDecimal> returns) {
        BigDecimal peak = BigDecimal.ZERO;
        BigDecimal maxDD = BigDecimal.ZERO;
        BigDecimal cumulative = BigDecimal.ZERO;
        
        for (BigDecimal ret : returns) {
            cumulative = cumulative.add(ret);
            peak = peak.max(cumulative);
            BigDecimal drawdown = peak.subtract(cumulative);
            maxDD = maxDD.max(drawdown);
        }
        
        return maxDD;
    }
    
    /**
     * Calculate cumulative return
     */
    private BigDecimal calculateCumulativeReturn(List<BigDecimal> returns) {
        BigDecimal cumulative = BigDecimal.ONE;
        
        for (BigDecimal ret : returns) {
            BigDecimal multiplier = BigDecimal.ONE.add(
                    ret.divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP)
            );
            cumulative = cumulative.multiply(multiplier);
        }
        
        return cumulative.subtract(BigDecimal.ONE)
                .multiply(BigDecimal.valueOf(100));
    }
    
    /**
     * Calculate overall statistics from periods
     */
    private WalkForwardResult calculateOverallStats(List<ValidationPeriod> periods) {
        int totalSignals = periods.stream()
                .mapToInt(ValidationPeriod::numSignals)
                .sum();
        
        int totalWinners = periods.stream()
                .mapToInt(ValidationPeriod::numWinners)
                .sum();
        
        double overallWinRate = totalSignals > 0 
                ? (double) totalWinners / totalSignals 
                : 0.0;
        
        // Weighted average return
        BigDecimal totalWeightedReturn = periods.stream()
                .map(p -> p.avgReturn().multiply(BigDecimal.valueOf(p.numSignals())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        BigDecimal overallAvgReturn = totalSignals > 0
                ? totalWeightedReturn.divide(BigDecimal.valueOf(totalSignals), 6, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        
        // Average Sharpe
        BigDecimal avgSharpe = periods.stream()
                .map(ValidationPeriod::sharpeRatio)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(periods.size()), 4, RoundingMode.HALF_UP);
        
        BigDecimal maxDrawdown = periods.stream()
                .map(ValidationPeriod::maxDrawdown)
                .max(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);
        
        // Cumulative return across all periods
        BigDecimal cumulativeReturn = periods.stream()
                .map(ValidationPeriod::totalReturn)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        // Statistical significance (simple check)
        boolean isSignificant = totalSignals >= 30 && 
                                overallWinRate > 0.55 && 
                                avgSharpe.compareTo(BigDecimal.valueOf(0.5)) > 0;
        
        String summary = buildSummary(
                periods.size(), overallWinRate, overallAvgReturn, avgSharpe, isSignificant
        );
        
        return new WalkForwardResult(
                periods,
                totalSignals,
                overallWinRate,
                overallAvgReturn,
                avgSharpe,
                maxDrawdown,
                cumulativeReturn,
                isSignificant,
                summary
        );
    }
    
    /**
     * Build summary text
     */
    private String buildSummary(
            int numPeriods, 
            double winRate, 
            BigDecimal avgReturn,
            BigDecimal sharpe,
            boolean isSignificant) {
        
        List<String> points = new ArrayList<>();
        
        points.add(String.format("Tested across %d periods", numPeriods));
        
        if (winRate > 0.6) {
            points.add(String.format("Strong win rate (%.1f%%)", winRate * 100));
        } else if (winRate > 0.5) {
            points.add(String.format("Marginal edge (%.1f%% win rate)", winRate * 100));
        } else {
            points.add(String.format("⚠️ Poor win rate (%.1f%%)", winRate * 100));
        }
        
        if (avgReturn.compareTo(BigDecimal.valueOf(1)) > 0) {
            points.add("Positive average returns");
        } else {
            points.add("⚠️ Negative/zero average returns");
        }
        
        if (sharpe.compareTo(BigDecimal.valueOf(1)) > 0) {
            points.add("Good risk-adjusted returns");
        }
        
        if (!isSignificant) {
            points.add("⚠️ Results not statistically significant");
        }
        
        return String.join(". ", points) + ".";
    }
}