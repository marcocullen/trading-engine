package com.trading;

import com.trading.config.DatabaseConfig;
import com.trading.data.collector.YahooFinanceCollector;
import com.trading.data.repository.MarketDataRepository;
import com.trading.domain.MarketData;
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
            
            // Initialize repositories and collectors
            MarketDataRepository marketDataRepo = new MarketDataRepository(dataSource);
            YahooFinanceCollector yahooCollector = new YahooFinanceCollector();
            
            // Fetch historical data for FTSE stocks
            LocalDate endDate = LocalDate.now();
            LocalDate startDate = endDate.minusMonths(1); // Last month of data
            
            logger.info("Fetching market data from {} to {}", startDate, endDate);
            
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
            
            // Display summary
            logger.info("Data collection complete");
            displaySummary(marketDataRepo);
            
        } catch (Exception e) {
            logger.error("Application error: {}", e.getMessage(), e);
            System.exit(1);
        }
    }
    
    private static void displaySummary(MarketDataRepository repo) {
        logger.info("\n=== Market Data Summary ===");
        
        for (String symbol : FTSE_STOCKS) {
            repo.getLatestPrice(symbol).ifPresent(price -> 
                logger.info("{}: Latest price = {}", symbol, price)
            );
        }
    }
}