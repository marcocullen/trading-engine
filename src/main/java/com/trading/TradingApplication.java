package com.trading;

import com.trading.config.DatabaseConfig;
import com.trading.data.collector.YahooFinanceCollector;
import com.trading.data.repository.IndicatorRepository;        // ← ADD THIS IMPORT
import com.trading.data.repository.MarketDataRepository;
import com.trading.domain.MarketData;
import com.trading.service.IndicatorService;                  // ← ADD THIS IMPORT
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.time.LocalDate;
import java.util.List;

/**
 * Main application entry point
 */
public class TradingApplication {

    private static final Logger logger = LoggerFactory.getLogger(TradingApplication.class);

    // FTSE 100 stocks to monitor
    private static final List<String> FTSE_STOCKS = List.of(
            "SHEL.L",    // Shell
            "AZN.L",     // AstraZeneca
            "HSBA.L",    // HSBC
            "ULVR.L",    // Unilever
            "BP.L",      // BP
            "DGE.L",     // Diageo
            "GSK.L",     // GSK
            "RIO.L",     // Rio Tinto
            "BATS.L",    // British American Tobacco
            "NG.L"       // National Grid
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
            for (String symbol : FTSE_STOCKS) {
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
            indicatorService.calculateForSymbols(FTSE_STOCKS);
            // ====================================================================

            // ========== ADD THESE LINES (Step 3: Display Summaries) ==========
            logger.info("\n=== Indicator Summaries ===");
            for (String symbol : FTSE_STOCKS) {
                try {
                    IndicatorService.IndicatorSummary summary =
                            indicatorService.getLatestIndicatorSummary(symbol);
                    summary.print();
                } catch (Exception e) {
                    logger.error("Failed to get summary for {}: {}", symbol, e.getMessage());
                }
            }
            // ================================================================

            logger.info("\n✅ Trading system initialized successfully!");

        } catch (Exception e) {
            logger.error("Application error: {}", e.getMessage(), e);
            System.exit(1);
        }
    }
}
