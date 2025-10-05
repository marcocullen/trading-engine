package com.trading.data.collector;

import com.trading.domain.MarketData;
import com.trading.domain.Price;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Collects market data from Yahoo Finance API
 * Uses the unofficial Yahoo Finance v8 API
 */
public class YahooFinanceCollector implements DataCollector {
    
    private static final Logger logger = LoggerFactory.getLogger(YahooFinanceCollector.class);
    private static final String BASE_URL = "https://query1.finance.yahoo.com/v8/finance/chart/";
    
    private final OkHttpClient httpClient;
    
    public YahooFinanceCollector() {
        this.httpClient = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build();
    }
    
    /**
     * Fetch historical market data for a symbol
     * 
     * @param symbol The ticker symbol (use .L suffix for LSE stocks, e.g., "SHEL.L")
     * @param startDate Start date for historical data
     * @param endDate End date for historical data
     * @return List of MarketData objects
     */
    @Override
    public List<MarketData> fetchHistoricalData(String symbol, LocalDate startDate, LocalDate endDate) {
        logger.info("Fetching historical data for {} from {} to {}", symbol, startDate, endDate);
        
        try {
            long period1 = startDate.atStartOfDay(ZoneId.of("Europe/London"))
                                   .toEpochSecond();
            long period2 = endDate.atStartOfDay(ZoneId.of("Europe/London"))
                                 .toEpochSecond();
            
            String url = String.format("%s%s?period1=%d&period2=%d&interval=1d&events=history",
                                      BASE_URL, symbol, period1, period2);
            
            Request request = new Request.Builder()
                .url(url)
                .addHeader("User-Agent", "Mozilla/5.0")
                .build();
            
            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    throw new IOException("Unexpected response code: " + response.code());
                }
                
                String responseBody = response.body().string();
                return parseYahooResponse(symbol, responseBody);
            }
            
        } catch (Exception e) {
            logger.error("Failed to fetch data for {}: {}", symbol, e.getMessage());
            throw new RuntimeException("Failed to fetch market data for " + symbol, e);
        }
    }
    
    /**
     * Fetch the latest market data (most recent trading day)
     */
    @Override
    public MarketData fetchLatestData(String symbol) {
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(7); // Get last week to ensure we have data
        
        List<MarketData> data = fetchHistoricalData(symbol, startDate, endDate);
        
        if (data.isEmpty()) {
            throw new RuntimeException("No market data available for " + symbol);
        }
        
        return data.get(data.size() - 1); // Return most recent
    }
    
    /**
     * Parse Yahoo Finance JSON response into MarketData objects
     */
    private List<MarketData> parseYahooResponse(String symbol, String jsonResponse) {
        List<MarketData> marketDataList = new ArrayList<>();
        
        try {
            JsonObject root = JsonParser.parseString(jsonResponse).getAsJsonObject();
            JsonObject chart = root.getAsJsonObject("chart");
            JsonArray results = chart.getAsJsonArray("result");
            
            if (results.size() == 0) {
                logger.warn("No data returned for symbol: {}", symbol);
                return marketDataList;
            }
            
            JsonObject result = results.get(0).getAsJsonObject();
            
            // Get timestamp array
            JsonArray timestamps = result.getAsJsonArray("timestamp");
            
            // Get indicators
            JsonObject indicators = result.getAsJsonObject("indicators");
            JsonArray quotes = indicators.getAsJsonArray("quote");
            JsonObject quote = quotes.get(0).getAsJsonObject();
            
            JsonArray opens = quote.getAsJsonArray("open");
            JsonArray highs = quote.getAsJsonArray("high");
            JsonArray lows = quote.getAsJsonArray("low");
            JsonArray closes = quote.getAsJsonArray("close");
            JsonArray volumes = quote.getAsJsonArray("volume");
            
            // Get adjusted close if available
            JsonArray adjustedCloses = null;
            if (indicators.has("adjclose")) {
                JsonArray adjcloseArray = indicators.getAsJsonArray("adjclose");
                if (adjcloseArray.size() > 0) {
                    adjustedCloses = adjcloseArray.get(0).getAsJsonObject().getAsJsonArray("adjclose");
                }
            }
            
            // Parse each data point
            for (int i = 0; i < timestamps.size(); i++) {
                // Skip if any value is null
                if (opens.get(i).isJsonNull() || highs.get(i).isJsonNull() || 
                    lows.get(i).isJsonNull() || closes.get(i).isJsonNull()) {
                    continue;
                }
                
                long timestamp = timestamps.get(i).getAsLong();
                LocalDate date = Instant.ofEpochSecond(timestamp)
                                       .atZone(ZoneId.of("Europe/London"))
                                       .toLocalDate();
                
                Price open = Price.of(opens.get(i).getAsDouble());
                Price high = Price.of(highs.get(i).getAsDouble());
                Price low = Price.of(lows.get(i).getAsDouble());
                Price close = Price.of(closes.get(i).getAsDouble());
                long volume = volumes.get(i).isJsonNull() ? 0L : volumes.get(i).getAsLong();
                
                // Use adjusted close if available, otherwise use regular close
                Price adjustedClose = close;
                if (adjustedCloses != null && !adjustedCloses.get(i).isJsonNull()) {
                    adjustedClose = Price.of(adjustedCloses.get(i).getAsDouble());
                }
                
                MarketData marketData = new MarketData(
                    symbol, date, open, high, low, close, adjustedClose, volume
                );
                
                marketDataList.add(marketData);
            }
            
            logger.info("Successfully parsed {} data points for {}", marketDataList.size(), symbol);
            
        } catch (Exception e) {
            logger.error("Failed to parse Yahoo Finance response: {}", e.getMessage());
            throw new RuntimeException("Failed to parse market data", e);
        }
        
        return marketDataList;
    }
}

/**
 * Interface for data collectors
 */
interface DataCollector {
    List<MarketData> fetchHistoricalData(String symbol, LocalDate startDate, LocalDate endDate);
    MarketData fetchLatestData(String symbol);
}