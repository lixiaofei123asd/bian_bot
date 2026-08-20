package com.lxf.bian_bot.repository.impl;

import com.lxf.bian_bot.model.BinanceKline;
import com.lxf.bian_bot.repository.KlineRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
        try {
            Files.createDirectories(Path.of("data"));
        } catch (IOException e) {
            throw new IllegalStateException("SQLite数据目录创建失败", e);
        }

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
    public void save(String interval, BinanceKline kline) {
        String sql = """
                INSERT INTO binance_kline (
                    symbol, interval, open_time, close_time, open_price, high_price, low_price,
                    close_price, volume, quote_volume, trade_count, taker_buy_volume,
                    taker_buy_quote_volume
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
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
                    taker_buy_quote_volume = excluded.taker_buy_quote_volume
                """;

        try (Connection connection = getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            bindKline(statement, interval, kline);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("SQLite K线保存失败: " + kline.getSymbol(), e);
        }
    }

    @Override
    public void saveAll(String interval, List<BinanceKline> klines) {
        if (klines == null || klines.isEmpty()) {
            return;
        }

        String sql = """
                INSERT INTO binance_kline (
                    symbol, interval, open_time, close_time, open_price, high_price, low_price,
                    close_price, volume, quote_volume, trade_count, taker_buy_volume,
                    taker_buy_quote_volume
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
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
                    taker_buy_quote_volume = excluded.taker_buy_quote_volume
                """;

        try (Connection connection = getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            connection.setAutoCommit(false);
            for (BinanceKline kline : klines) {
                bindKline(statement, interval, kline);
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
                SELECT symbol, open_time, close_time, open_price, high_price, low_price,
                       close_price, volume, quote_volume, trade_count, taker_buy_volume,
                       taker_buy_quote_volume
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

    private void bindKline(PreparedStatement statement, String interval, BinanceKline kline) throws SQLException {
        statement.setString(1, kline.getSymbol());
        statement.setString(2, interval);
        statement.setLong(3, kline.getOpenTime());
        statement.setLong(4, kline.getCloseTime());
        statement.setBigDecimal(5, kline.getOpenPrice());
        statement.setBigDecimal(6, kline.getHighPrice());
        statement.setBigDecimal(7, kline.getLowPrice());
        statement.setBigDecimal(8, kline.getClosePrice());
        statement.setBigDecimal(9, kline.getVolume());
        statement.setBigDecimal(10, kline.getQuoteAssetVolume());
        statement.setLong(11, kline.getNumberOfTrades());
        statement.setBigDecimal(12, kline.getTakerBuyBaseAssetVolume());
        statement.setBigDecimal(13, kline.getTakerBuyQuoteAssetVolume());
    }

    private BinanceKline map(ResultSet rs) throws SQLException {
        BinanceKline kline = new BinanceKline();
        kline.setSymbol(rs.getString("symbol"));
        kline.setOpenTime(rs.getLong("open_time"));
        kline.setCloseTime(rs.getLong("close_time"));
        kline.setOpenPrice(rs.getBigDecimal("open_price"));
        kline.setHighPrice(rs.getBigDecimal("high_price"));
        kline.setLowPrice(rs.getBigDecimal("low_price"));
        kline.setClosePrice(rs.getBigDecimal("close_price"));
        kline.setVolume(rs.getBigDecimal("volume"));
        kline.setQuoteAssetVolume(rs.getBigDecimal("quote_volume"));
        kline.setNumberOfTrades(rs.getLong("trade_count"));
        kline.setTakerBuyBaseAssetVolume(rs.getBigDecimal("taker_buy_volume"));
        kline.setTakerBuyQuoteAssetVolume(rs.getBigDecimal("taker_buy_quote_volume"));
        return kline;
    }
}
