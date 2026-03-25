package com.trading.analysis;

import com.trading.config.DatabaseConfig;
import com.trading.data.repository.IndicatorRepository;
import com.trading.data.repository.MarketDataRepository;
import com.trading.service.IndicatorService;
import com.trading.service.SignalGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.time.LocalDate;
import java.util.List;

/**
 * Main entry point for statistical analysis and validation
 * Runs comprehensive analysis to validate signal quality
 */
public class StatisticalAnalysisRunner {
    
    private static final Logger logger = LoggerFactory.getLogger(StatisticalAnalysisRunner.class);
    
    // Same universe as TradingApplication
    private static final List<String> ANALYSIS_UNIVERSE = List.of(
            "SHEL.L", "BP.L", "HSBA.L", "LLOY.L", "BARC.L", "AZN.L", "GSK.L",
            "ULVR.L", "RIO.L", "GLEN.L", "VOD.L", "VUSA.L", "VWRL.L", 
            "SGLN.L", "IITU.L", "INRG.L"
    );
    
    public static void main(String[] args) {
        logger.info("Starting Statistical Analysis...");
        
        try {
            DataSource dataSource = DatabaseConfig.getDataSource();
            
            MarketDataRepository marketDataRepo = new MarketDataRepository(dataSource);
            IndicatorRepository indicatorRepo = new IndicatorRepository(dataSource);
            IndicatorService indicatorService = new IndicatorService(marketDataRepo, indicatorRepo);
            SignalGenerator signalGenerator = new SignalGenerator(indicatorService, marketDataRepo);
            
            // Analysis period: last 12 months
            LocalDate endDate = LocalDate.now();
            LocalDate startDate = endDate.minusMonths(12);
            
            System.out.println("\n" + "=".repeat(80));
            System.out.println("STATISTICAL ANALYSIS OF TRADING SIGNALS");
            System.out.println("Period: " + startDate + " to " + endDate);
            System.out.println("Universe: " + ANALYSIS_UNIVERSE.size() + " symbols");
            System.out.println("=".repeat(80));
            
            // ========== 1. THRESHOLD ANALYSIS ==========
            System.out.println("\n📊 PHASE 1: EMPIRICAL THRESHOLD ANALYSIS");
            System.out.println("-".repeat(80));
            
            ThresholdAnalyzer thresholdAnalyzer = new ThresholdAnalyzer(
                    marketDataRepo, indicatorRepo
            );
            
            // Find optimal RSI oversold threshold
            logger.info("Analyzing RSI oversold threshold...");
            ThresholdAnalyzer.ThresholdAnalysisResult rsiResult = 
                    thresholdAnalyzer.findOptimalRSIOversold(
                            ANALYSIS_UNIVERSE, 
                            startDate, 
                            endDate, 
                            10  // 10-day holding period
                    );
            
            if (rsiResult != null) {
                rsiResult.print();
            } else {
                System.out.println("⚠️  Could not find statistically significant RSI threshold");
            }
            
            // Find optimal ROC threshold
            logger.info("Analyzing ROC momentum threshold...");
            ThresholdAnalyzer.ThresholdAnalysisResult rocResult = 
                    thresholdAnalyzer.findOptimalROCThreshold(
                            ANALYSIS_UNIVERSE, 
                            startDate, 
                            endDate, 
                            10
                    );
            
            if (rocResult != null) {
                rocResult.print();
            } else {
                System.out.println("⚠️  Could not find statistically significant ROC threshold");
            }
            
            // ========== 2. WEIGHT OPTIMIZATION ==========
            System.out.println("\n⚖️  PHASE 2: OPTIMAL WEIGHT CALCULATION");
            System.out.println("-".repeat(80));
            
            WeightOptimizer weightOptimizer = new WeightOptimizer(
                    marketDataRepo, indicatorRepo, indicatorService
            );
            
            logger.info("Optimizing signal component weights...");
            WeightOptimizer.OptimalWeights optimalWeights = 
                    weightOptimizer.findOptimalWeights(
                            ANALYSIS_UNIVERSE,
                            startDate,
                            endDate,
                            10  // 10-day holding period
                    );
            
            optimalWeights.print();
            
            // ========== 3. WALK-FORWARD VALIDATION ==========
            System.out.println("\n🔄 PHASE 3: WALK-FORWARD BACKTEST");
            System.out.println("-".repeat(80));
            
            WalkForwardBacktester backtester = new WalkForwardBacktester(
                    signalGenerator, marketDataRepo
            );
            
            logger.info("Running walk-forward validation...");
            WalkForwardBacktester.WalkForwardResult backtest = 
                    backtester.runWalkForward(
                            ANALYSIS_UNIVERSE,
                            startDate,
                            endDate,
                            3,  // 3 months training
                            1,  // 1 month testing
                            10, // 10-day holding period
                            60  // Score threshold
                    );
            
            backtest.print();
            
            // ========== 4. SENSITIVITY ANALYSIS ==========
            System.out.println("\n🎯 PHASE 4: THRESHOLD SENSITIVITY ANALYSIS");
            System.out.println("-".repeat(80));
            
            logger.info("Testing different score thresholds...");
            System.out.println("\nScore Threshold | Win Rate | Avg Return | Sharpe | # Signals");
            System.out.println("-".repeat(70));
            
            for (int threshold = 50; threshold <= 80; threshold += 5) {
                WalkForwardBacktester.WalkForwardResult result = 
                        backtester.runWalkForward(
                                ANALYSIS_UNIVERSE,
                                startDate.plusMonths(6), // Last 6 months only for speed
                                endDate,
                                2, 1, 10, threshold
                        );
                
                System.out.printf("      %d       |  %.1f%%   |   %.2f%%   |  %.2f  |    %d%n",
                        threshold,
                        result.overallWinRate() * 100,
                        result.overallAvgReturn(),
                        result.overallSharpe(),
                        result.totalSignals()
                );
            }
            
            // ========== 5. FINAL RECOMMENDATIONS ==========
            System.out.println("\n" + "=".repeat(80));
            System.out.println("📋 FINAL RECOMMENDATIONS");
            System.out.println("=".repeat(80));
            
            // Compare current settings vs optimal
            System.out.println("\nCURRENT SYSTEM:");
            System.out.println("  RSI Oversold:    < 30 (textbook value)");
            System.out.println("  ROC Strong:      > 10% (textbook value)");
            System.out.println("  Weights:         30/30/30/10 (equal)");
            System.out.println("  Score Threshold: 60");
            
            System.out.println("\nOPTIMAL (DATA-DRIVEN):");
            if (rsiResult != null) {
                System.out.printf("  RSI Oversold:    < %.0f (%.2f%% avg return, Sharpe %.2f)%n",
                        rsiResult.optimalThreshold(),
                        rsiResult.avgForwardReturn(),
                        rsiResult.sharpeRatio());
            }
            if (rocResult != null) {
                System.out.printf("  ROC Strong:      > %.0f%% (%.2f%% avg return, Sharpe %.2f)%n",
                        rocResult.optimalThreshold(),
                        rocResult.avgForwardReturn(),
                        rocResult.sharpeRatio());
            }
            System.out.printf("  Weights:         %.0f/%.0f/%.0f/%.0f (regression-derived, R²=%.2f)%n",
                    optimalWeights.trendWeight(),
                    optimalWeights.momentumWeight(),
                    optimalWeights.valueWeight(),
                    optimalWeights.confluenceWeight(),
                    optimalWeights.rSquared());
            
            // Overall assessment
            System.out.println("\n" + "-".repeat(80));
            System.out.println("OVERALL ASSESSMENT:");
            
            if (backtest.isStatisticallySignificant() && 
                backtest.overallWinRate() > 0.55 && 
                backtest.overallSharpe().doubleValue() > 0.5) {
                
                System.out.println("✅ SYSTEM SHOWS PROMISE");
                System.out.println("   - Statistically significant results");
                System.out.println("   - Positive risk-adjusted returns");
                System.out.println("   - Consider paper trading with these parameters");
                
            } else if (backtest.overallWinRate() > 0.5) {
                System.out.println("⚠️  MARGINAL EDGE DETECTED");
                System.out.println("   - Slight positive expectancy");
                System.out.println("   - High uncertainty - proceed with extreme caution");
                System.out.println("   - Consider additional features or different approach");
                
            } else {
                System.out.println("❌ CURRENT APPROACH NOT VIABLE");
                System.out.println("   - Negative or random results");
                System.out.println("   - No statistical edge detected");
                System.out.println("   - Recommend fundamental redesign");
            }
            
            System.out.println("\nNEXT STEPS:");
            System.out.println("1. Update SignalScorer with optimal thresholds from analysis");
            System.out.println("2. Implement regression-based weights instead of equal weighting");
            System.out.println("3. Add risk adjustment (volatility, correlation)");
            System.out.println("4. Paper trade for 3 months before using real capital");
            System.out.println("5. Continuously monitor performance and re-calibrate quarterly");
            
            System.out.println("\n" + "=".repeat(80));
            logger.info("Statistical analysis complete!");
            
        } catch (Exception e) {
            logger.error("Analysis failed: {}", e.getMessage(), e);
            System.exit(1);
        }
    }
}