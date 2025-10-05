package com.trading.data.repository;

import com.trading.domain.MarketData;
import com.trading.domain.Price;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Repository for market data persistence and retrieval
 */
public class MarketDataRepository {
    
    private final DataSource dataSource;
    
    public MarketDataRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }
    
    /**
     * Save market data for a given symbol and date
     */
    public void save(MarketData marketData) {
        String sql = """
            INSERT INTO market_data (symbol, trade_date, open_price, high_price, low_price, 
                                    close_price, adjusted_close, volume)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (symbol, trade_date) 
            DO UPDATE SET 
                open_price = EXCLUDED.open_price,
                high_price = EXCLUDED.high_price,
                low_price = EXCLUDED.low_price,
                close_price = EXCLUDED.close_price,
                adjusted_close = EXCLUDED.adjusted_close,
                volume = EXCLUDED.volume
            """;
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, marketData.symbol());
            stmt.setDate(2, Date.valueOf(marketData.date()));
            stmt.setBigDecimal(3, marketData.open().value());
            stmt.setBigDecimal(4, marketData.high().value());
            stmt.setBigDecimal(5, marketData.low().value());
            stmt.setBigDecimal(6, marketData.close().value());
            stmt.setBigDecimal(7, marketData.adjustedClose().value());
            stmt.setLong(8, marketData.volume());
            
            stmt.executeUpdate();
            
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save market data for " + marketData.symbol(), e);
        }
    }
    
    /**
     * Save multiple market data records in batch
     */
    public void saveBatch(List<MarketData> marketDataList) {
        String sql = """
            INSERT INTO market_data (symbol, trade_date, open_price, high_price, low_price, 
                                    close_price, adjusted_close, volume)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (symbol, trade_date) 
            DO UPDATE SET 
                open_price = EXCLUDED.open_price,
                high_price = EXCLUDED.high_price,
                low_price = EXCLUDED.low_price,
                close_price = EXCLUDED.close_price,
                adjusted_close = EXCLUDED.adjusted_close,
                volume = EXCLUDED.volume
            """;
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            conn.setAutoCommit(false);
            
            for (MarketData md : marketDataList) {
                stmt.setString(1, md.symbol());
                stmt.setDate(2, Date.valueOf(md.date()));
                stmt.setBigDecimal(3, md.open().value());
                stmt.setBigDecimal(4, md.high().value());
                stmt.setBigDecimal(5, md.low().value());
                stmt.setBigDecimal(6, md.close().value());
                stmt.setBigDecimal(7, md.adjustedClose().value());
                stmt.setLong(8, md.volume());
                stmt.addBatch();
            }
            
            stmt.executeBatch();
            conn.commit();
            
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save market data batch", e);
        }
    }
    
    /**
     * Find market data for a specific symbol and date
     */
    public Optional<MarketData> findBySymbolAndDate(String symbol, LocalDate date) {
        String sql = """
            SELECT symbol, trade_date, open_price, high_price, low_price, 
                   close_price, adjusted_close, volume
            FROM market_data
            WHERE symbol = ? AND trade_date = ?
            """;
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, symbol);
            stmt.setDate(2, Date.valueOf(date));
            
            ResultSet rs = stmt.executeQuery();
            
            if (rs.next()) {
                return Optional.of(mapToMarketData(rs));
            }
            
            return Optional.empty();
            
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find market data", e);
        }
    }
    
    /**
     * Get historical market data for a symbol within a date range
     */
    public List<MarketData> findBySymbolBetweenDates(String symbol, LocalDate startDate, LocalDate endDate) {
        String sql = """
            SELECT symbol, trade_date, open_price, high_price, low_price, 
                   close_price, adjusted_close, volume
            FROM market_data
            WHERE symbol = ? AND trade_date BETWEEN ? AND ?
            ORDER BY trade_date ASC
            """;
        
        List<MarketData> results = new ArrayList<>();
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, symbol);
            stmt.setDate(2, Date.valueOf(startDate));
            stmt.setDate(3, Date.valueOf(endDate));
            
            ResultSet rs = stmt.executeQuery();
            
            while (rs.next()) {
                results.add(mapToMarketData(rs));
            }
            
            return results;
            
        } catch (SQLException e) {
            throw new RuntimeException("Failed to fetch historical market data", e);
        }
    }
    
    /**
     * Get the most recent N trading days for a symbol
     */
    public List<MarketData> findRecentBySymbol(String symbol, int limit) {
        String sql = """
            SELECT symbol, trade_date, open_price, high_price, low_price, 
                   close_price, adjusted_close, volume
            FROM market_data
            WHERE symbol = ?
            ORDER BY trade_date DESC
            LIMIT ?
            """;
        
        List<MarketData> results = new ArrayList<>();
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, symbol);
            stmt.setInt(2, limit);
            
            ResultSet rs = stmt.executeQuery();
            
            while (rs.next()) {
                results.add(mapToMarketData(rs));
            }
            
            // Reverse to get chronological order
            return results.reversed();
            
        } catch (SQLException e) {
            throw new RuntimeException("Failed to fetch recent market data", e);
        }
    }
    
    /**
     * Get the latest closing price for a symbol
     */
    public Optional<Price> getLatestPrice(String symbol) {
        String sql = """
            SELECT close_price
            FROM market_data
            WHERE symbol = ?
            ORDER BY trade_date DESC
            LIMIT 1
            """;
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, symbol);
            ResultSet rs = stmt.executeQuery();
            
            if (rs.next()) {
                return Optional.of(new Price(rs.getBigDecimal("close_price")));
            }
            
            return Optional.empty();
            
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get latest price", e);
        }
    }
    
    /**
     * Map ResultSet to MarketData domain object
     */
    private MarketData mapToMarketData(ResultSet rs) throws SQLException {
        return new MarketData(
            rs.getString("symbol"),
            rs.getDate("trade_date").toLocalDate(),
            new Price(rs.getBigDecimal("open_price")),
            new Price(rs.getBigDecimal("high_price")),
            new Price(rs.getBigDecimal("low_price")),
            new Price(rs.getBigDecimal("close_price")),
            new Price(rs.getBigDecimal("adjusted_close")),
            rs.getLong("volume")
        );
    }
}