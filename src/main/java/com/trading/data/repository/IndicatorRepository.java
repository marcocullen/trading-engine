package com.trading.data.repository;

import com.trading.domain.IndicatorResult;

import javax.sql.DataSource;
import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Repository for technical indicator persistence
 */
public class IndicatorRepository {

    private final DataSource dataSource;

    public IndicatorRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * Save indicator results in batch
     */
    public void saveBatch(List<IndicatorResult> results) {
        if (results.isEmpty()) {
            return;
        }

        String sql = """
            INSERT INTO technical_indicators (symbol, trade_date, indicator_name, value, metadata)
            VALUES (?, ?, ?, ?, ?::jsonb)
            ON CONFLICT (symbol, trade_date, indicator_name)
            DO UPDATE SET 
                value = EXCLUDED.value,
                metadata = EXCLUDED.metadata
            """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            conn.setAutoCommit(false);

            for (IndicatorResult result : results) {
                stmt.setString(1, result.symbol());
                stmt.setDate(2, Date.valueOf(result.date()));
                stmt.setString(3, result.indicatorName());
                stmt.setBigDecimal(4, result.value());
                stmt.setString(5, result.metadata());
                stmt.addBatch();
            }

            stmt.executeBatch();
            conn.commit();

        } catch (SQLException e) {
            throw new RuntimeException("Failed to save indicator results", e);
        }
    }

    /**
     * Get indicator values for a symbol within date range
     */
    public List<IndicatorResult> findBySymbolAndIndicator(
            String symbol,
            String indicatorName,
            LocalDate startDate,
            LocalDate endDate) {

        String sql = """
            SELECT symbol, trade_date, indicator_name, value, metadata
            FROM technical_indicators
            WHERE symbol = ? 
              AND indicator_name = ?
              AND trade_date BETWEEN ? AND ?
            ORDER BY trade_date ASC
            """;

        List<IndicatorResult> results = new ArrayList<>();

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, symbol);
            stmt.setString(2, indicatorName);
            stmt.setDate(3, Date.valueOf(startDate));
            stmt.setDate(4, Date.valueOf(endDate));

            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                results.add(new IndicatorResult(
                        rs.getString("symbol"),
                        rs.getDate("trade_date").toLocalDate(),
                        rs.getString("indicator_name"),
                        rs.getBigDecimal("value"),
                        rs.getString("metadata")
                ));
            }

            return results;

        } catch (SQLException e) {
            throw new RuntimeException("Failed to fetch indicators", e);
        }
    }

    /**
     * Get the latest indicator value for a symbol
     */
    public IndicatorResult getLatest(String symbol, String indicatorName) {
        String sql = """
            SELECT symbol, trade_date, indicator_name, value, metadata
            FROM technical_indicators
            WHERE symbol = ? AND indicator_name = ?
            ORDER BY trade_date DESC
            LIMIT 1
            """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, symbol);
            stmt.setString(2, indicatorName);

            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return new IndicatorResult(
                        rs.getString("symbol"),
                        rs.getDate("trade_date").toLocalDate(),
                        rs.getString("indicator_name"),
                        rs.getBigDecimal("value"),
                        rs.getString("metadata")
                );
            }

            return null;

        } catch (SQLException e) {
            throw new RuntimeException("Failed to get latest indicator", e);
        }
    }

    /**
     * Delete all indicators for a symbol (for recalculation)
     */
    public void deleteBySymbol(String symbol) {
        String sql = "DELETE FROM technical_indicators WHERE symbol = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, symbol);
            stmt.executeUpdate();

        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete indicators", e);
        }
    }
}