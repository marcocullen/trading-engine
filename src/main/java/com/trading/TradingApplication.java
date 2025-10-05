package com.trading;

import com.trading.config.DatabaseConfig;
import com.trading.data.collector.YahooFinanceCollector;
import com.trading.data.repository.IndicatorRepository;        // ← ADD THIS IMPORT
import com.trading.data.repository.MarketDataRepository;
import com.trading.domain.MarketData;
import com.trading.domain.TradingSignal;
import com.trading.service.*;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Main application entry point
 */
public class TradingApplication {

    private static final Logger logger = LoggerFactory.getLogger(TradingApplication.class);

    // FTSE 100 stocks to monitor
    private static final List<String> TRADING_UNIVERSE = List.of(
            // ========== UK LARGE-CAPS (FTSE 100) ==========
            "SHEL.L",    // Shell (Energy)
            "AZN.L",     // AstraZeneca (Healthcare)
            "HSBA.L",    // HSBC (Banking)
            "ULVR.L",    // Unilever (Consumer)
            "BP.L",      // BP (Energy)
            "DGE.L",     // Diageo (Consumer)
            "GSK.L",     // GSK (Healthcare)
            "RIO.L",     // Rio Tinto (Mining)
            "BATS.L",    // British American Tobacco (Consumer)
            "NG.L",      // National Grid (Utilities)

            // ========== BROAD MARKET ETFS ==========
            "VUKE.L",    // Vanguard FTSE 100 (UK large-cap)
            "VMID.L",    // Vanguard FTSE 250 (UK mid-cap growth)
            "VUSA.L",    // Vanguard S&P 500 (US exposure)
            "VWRL.L",    // Vanguard All-World (global diversification)
            "VEUR.L",    // Vanguard FTSE Europe (European exposure)

            // ========== SECTOR/THEMATIC ETFS ==========
            "IITU.L",    // iShares MSCI World Tech (technology)
            "IUKD.L",    // iShares UK Dividend (income/defensive)
            "INRG.L",    // iShares Clean Energy (thematic growth)
            "IUHC.L",    // iShares Healthcare (defensive sector)

            // ========== COMMODITIES & REAL ASSETS ==========
            "SGLN.L",    // iShares Physical Gold (safe haven)
            "IGLT.L",    // iShares UK Gilts (bonds, defensive)
            "IPRP.L",    // iShares UK Property REIT (real estate)
            "CRUD.L",    // WisdomTree Crude Oil (energy commodity)

            // ========== UK GROWTH STOCKS ==========
            "OCDO.L",    // Ocado (tech/retail innovation)
            "AUTO.L",    // Auto Trader (digital marketplace)
            "LSEG.L",    // London Stock Exchange (fintech)
            "EXPN.L",    // Experian (data analytics)
            "REL.L",     // RELX (information/tech)
            "RKT.L",     // Reckitt (consumer health)
            "DCC.L",     // DCC (diversified growth)
            "MNDI.L",    // Mondi (packaging/materials)

            // ========== INTERNATIONAL QUALITY ==========
            "BRBY.L",    // Burberry (luxury consumer)
            "AAL.L",     // Anglo American (mining diversification)
            "BARC.L",    // Barclays (banking alternative to HSBC)
            "VOD.L"      // Vodafone (telecoms)
    );

    public static void main(String[] args) {
        logger.info("Starting UK Trading System...");

        try {
            // Initialize database
            DataSource dataSource = DatabaseConfig.getDataSource();
            logger.info("Database connection established");

            // ========== ADD THESE LINES ==========
            // Initialize repositories and services
            MarketDataRepository marketDataRepo = new MarketDataRepository(dataSource);
            IndicatorRepository indicatorRepo = new IndicatorRepository(dataSource);
            IndicatorService indicatorService = new IndicatorService(marketDataRepo, indicatorRepo);
            YahooFinanceCollector yahooCollector = new YahooFinanceCollector();
            // =====================================

            // CHANGE THIS: minusMonths(9) instead of minusMonths(1) for more data
            LocalDate endDate = LocalDate.now();
            LocalDate startDate = endDate.minusMonths(9); // ← CHANGE FROM 1 to 9 months

            logger.info("Fetching market data from {} to {}", startDate, endDate);

            // Step 1: Fetch market data
            for (String symbol : TRADING_UNIVERSE) {
                try {
                    logger.info("Processing {}", symbol);

                    List<MarketData> marketData = yahooCollector.fetchHistoricalData(
                            symbol, startDate, endDate
                    );

                    if (!marketData.isEmpty()) {
                        marketDataRepo.saveBatch(marketData);
                        logger.info("Saved {} data points for {}", marketData.size(), symbol);
                    }

                    // Rate limiting - be nice to Yahoo Finance
                    Thread.sleep(500);

                } catch (Exception e) {
                    logger.error("Failed to process {}: {}", symbol, e.getMessage());
                }
            }

            // ========== ADD THESE LINES (Step 2: Calculate Indicators) ==========
            logger.info("\n=== Calculating Technical Indicators ===");
            indicatorService.calculateForSymbols(TRADING_UNIVERSE);
            // ====================================================================

            // ========== ADD THESE LINES (Step 3: Display Summaries) ==========
            logger.info("\n=== Indicator Summaries ===");
            for (String symbol : TRADING_UNIVERSE) {
                try {
                    IndicatorService.IndicatorSummary summary =
                            indicatorService.getLatestIndicatorSummary(symbol);
                    summary.print();
                } catch (Exception e) {
                    logger.error("Failed to get summary for {}: {}", symbol, e.getMessage());
                }
            }
            // ================================================================

            // ========== ADD THESE LINES (Step 4: Generate Trading Signals) ==========
            logger.info("\n=== Generating Trading Signals ===");
            SignalGenerator signalGenerator = new SignalGenerator(indicatorService, marketDataRepo);

            // Generate signals for all stocks
            List<TradingSignal> allSignals = signalGenerator.generateSignals(TRADING_UNIVERSE);

            // Print full report
            signalGenerator.printSignalReport(allSignals);

            // Get top buy opportunities
            List<TradingSignal> topBuys = signalGenerator.getTopBuySignals(TRADING_UNIVERSE, 3);
            if (!topBuys.isEmpty()) {
                logger.info("\n🎯 Top 3 Buy Opportunities:");
                for (int i = 0; i < topBuys.size(); i++) {
                    TradingSignal signal = topBuys.get(i);
                    logger.info("  {}. {} - Score: {}/100 - {}",
                            i + 1, signal.symbol(), signal.score(), signal.reasoning());
                }
            }
            // =========================================================================

            // ========== ADD THESE LINES (Step 5: Calculate Position Sizes) ==========
            logger.info("\n=== Position Sizing for Top Opportunities ===");

            // Initialize position sizer with £20,000 portfolio
            BigDecimal portfolioValue = BigDecimal.valueOf(20000);
            RiskParameters riskParams = RiskParameters.moderate();  // Moderate risk
            PositionSizer positionSizer = new PositionSizer(
                    portfolioValue,
                    PositionSizer.PositionSizingStrategy.SIGNAL_STRENGTH,  // Scale with signal score
                    riskParams
            );

            // Calculate position sizes for tradeable signals
            List<TradingSignal> tradeableSignals = signalGenerator.getTradeableSignals(TRADING_UNIVERSE);
            logger.info("Found {} tradeable signals (score >= 60)", tradeableSignals.size());

            if (!tradeableSignals.isEmpty()) {
                System.out.println("\n" + "=".repeat(100));
                System.out.println("                         RECOMMENDED POSITIONS");
                System.out.println("=".repeat(100));

                BigDecimal totalInvestment = BigDecimal.ZERO;

                for (TradingSignal signal : tradeableSignals) {
                    PositionSize position = positionSizer.calculatePositionSize(signal);

                    if (position.isValid()) {
                        position.print();
                        totalInvestment = totalInvestment.add(position.investmentAmount());
                    }
                }

                System.out.println("\n" + "-".repeat(100));
                System.out.printf("Portfolio Summary:%n");
                System.out.printf("  Total Capital:    £%.2f%n", portfolioValue);
                System.out.printf("  Total Investment: £%.2f (%.2f%% deployed)%n",
                        totalInvestment,
                        totalInvestment.divide(portfolioValue, 4, BigDecimal.ROUND_HALF_UP)
                                .multiply(BigDecimal.valueOf(100)));
                System.out.printf("  Cash Remaining:   £%.2f%n", portfolioValue.subtract(totalInvestment));
                System.out.printf("  Positions:        %d%n", tradeableSignals.size());
                System.out.println("=".repeat(100) + "\n");
            } else {
                logger.info("⚠️  No tradeable signals at this time. Wait for better opportunities.");
            }
            // =========================================================================

            logger.info("\n✅ Trading system initialized successfully!");

        } catch (Exception e) {
            logger.error("Application error: {}", e.getMessage(), e);
            System.exit(1);
        }
    }
}