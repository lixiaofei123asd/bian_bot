package com.lxf.bian_bot.repository.impl;

import com.lxf.bian_bot.model.BinanceKline;
import com.lxf.bian_bot.repository.KlineRepository;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

@Slf4j
@Repository
public class SQLiteKlineRepository implements KlineRepository {

    private static final String DB_URL = "jdbc:sqlite:data/bian_bot.db";

    @Override
    public void initialize() {
        String sql = """
                CREATE TABLE IF NOT EXISTS binance_kline (
                    symbol TEXT NOT NULL,
                    interval TEXT NOT NULL,
                    open_time INTEGER NOT NULL,
                    close_time INTEGER NOT NULL,
                    open_price TEXT NOT NULL,
                    high_price TEXT NOT NULL,
                    low_price TEXT NOT NULL,
                    close_price TEXT NOT NULL,
                    volume TEXT NOT NULL,
                    quote_volume TEXT NOT NULL,
                    trade_count INTEGER NOT NULL,
                    taker_buy_volume TEXT NOT NULL,
                    taker_buy_quote_volume TEXT NOT NULL,
                    closed INTEGER NOT NULL,
                    PRIMARY KEY (symbol, interval, open_time)
                )
                """;

        try (Connection connection = getConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
            log.info("SQLite K线表初始化完成");
        } catch (SQLException e) {
            throw new IllegalStateException("SQLite K线表初始化失败", e);
        }
    }

    @Override
    public void save(BinanceKline kline) {
        String sql = """
                INSERT INTO binance_kline (
                    symbol, interval, open_time, close_time, open_price, high_price, low_price,
                    close_price, volume, quote_volume, trade_count, taker_buy_volume,
                    taker_buy_quote_volume, closed
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(symbol, interval, open_time) DO UPDATE SET
                    close_time = excluded.close_time,
                    open_price = excluded.open_price,
                    high_price = excluded.high_price,
                    low_price = excluded.low_price,
                    close_price = excluded.close_price,
                    volume = excluded.volume,
                    quote_volume = excluded.quote_volume,
                    trade_count = excluded.trade_count,
                    taker_buy_volume = excluded.taker_buy_volume,
                    taker_buy_quote_volume = excluded.taker_buy_quote_volume,
                    closed = excluded.closed
                """;

        try (Connection connection = getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            bindKline(statement, kline);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("SQLite K线保存失败: " + kline.getSymbol(), e);
        }
    }

    @Override
    public void saveAll(List<BinanceKline> klines) {
        if (klines == null || klines.isEmpty()) {
            return;
        }

        String sql = """
                INSERT INTO binance_kline (
                    symbol, interval, open_time, close_time, open_price, high_price, low_price,
                    close_price, volume, quote_volume, trade_count, taker_buy_volume,
                    taker_buy_quote_volume, closed
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(symbol, interval, open_time) DO UPDATE SET
                    close_time = excluded.close_time,
                    open_price = excluded.open_price,
                    high_price = excluded.high_price,
                    low_price = excluded.low_price,
                    close_price = excluded.close_price,
                    volume = excluded.volume,
                    quote_volume = excluded.quote_volume,
                    trade_count = excluded.trade_count,
                    taker_buy_volume = excluded.taker_buy_volume,
                    taker_buy_quote_volume = excluded.taker_buy_quote_volume,
                    closed = excluded.closed
                """;

        try (Connection connection = getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            connection.setAutoCommit(false);
            for (BinanceKline kline : klines) {
                bindKline(statement, kline);
                statement.addBatch();
            }
            statement.executeBatch();
            connection.commit();
        } catch (SQLException e) {
            throw new IllegalStateException("SQLite K线批量保存失败", e);
        }
    }

    @Override
    public List<BinanceKline> findLatest(String symbol, String interval, int limit) {
        String sql = """
                SELECT symbol, interval, open_time, close_time, open_price, high_price, low_price,
                       close_price, volume, quote_volume, trade_count, taker_buy_volume,
                       taker_buy_quote_volume, closed
                FROM binance_kline
                WHERE symbol = ? AND interval = ?
                ORDER BY open_time DESC
                LIMIT ?
                """;

        List<BinanceKline> result = new ArrayList<>();
        try (Connection connection = getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, symbol);
            statement.setString(2, interval);
            statement.setInt(3, limit);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    result.add(map(rs));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("SQLite K线查询失败: " + symbol, e);
        }

        result.sort((a, b) -> Long.compare(a.getOpenTime(), b.getOpenTime()));
        return result;
    }

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(DB_URL);
    }

    private void bindKline(PreparedStatement statement, BinanceKline kline) throws SQLException {
        statement.setString(1, kline.getSymbol());
        statement.setString(2, kline.getInterval());
        statement.setLong(3, kline.getOpenTime());
        statement.setLong(4, kline.getCloseTime());
        statement.setString(5, kline.getOpenPrice());
        statement.setString(6, kline.getHighPrice());
        statement.setString(7, kline.getLowPrice());
        statement.setString(8, kline.getClosePrice());
        statement.setString(9, kline.getVolume());
        statement.setString(10, kline.getQuoteVolume());
        statement.setInt(11, kline.getTradeCount());
        statement.setString(12, kline.getTakerBuyVolume());
        statement.setString(13, kline.getTakerBuyQuoteVolume());
        statement.setBoolean(14, kline.isClosed());
    }

    private BinanceKline map(ResultSet rs) throws SQLException {
        return BinanceKline.builder()
                .symbol(rs.getString("symbol"))
                .interval(rs.getString("interval"))
                .openTime(rs.getLong("open_time"))
                .closeTime(rs.getLong("close_time"))
                .openPrice(rs.getString("open_price"))
                .highPrice(rs.getString("high_price"))
                .lowPrice(rs.getString("low_price"))
                .closePrice(rs.getString("close_price"))
                .volume(rs.getString("volume"))
                .quoteVolume(rs.getString("quote_volume"))
                .tradeCount(rs.getInt("trade_count"))
                .takerBuyVolume(rs.getString("taker_buy_volume"))
                .takerBuyQuoteVolume(rs.getString("taker_buy_quote_volume"))
                .closed(rs.getBoolean("closed"))
                .build();
    }
}
