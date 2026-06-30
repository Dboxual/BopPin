package dev.dkocaj.boppin;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

public final class PinStore implements AutoCloseable {
    private final Logger log;
    private final Connection conn;

    public PinStore(Path dbFile, Logger log) throws SQLException {
        this.log = log;
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            throw new SQLException("SQLite JDBC driver not found on classpath. "
                    + "Paper should have resolved it via the libraries: block.", e);
        }
        String url = "jdbc:sqlite:" + dbFile.toAbsolutePath();
        this.conn = DriverManager.getConnection(url);
        try (Statement s = conn.createStatement()) {
            s.execute("PRAGMA journal_mode=WAL");
            s.execute("PRAGMA synchronous=NORMAL");
            s.execute("""
                CREATE TABLE IF NOT EXISTS pins (
                    uuid       TEXT PRIMARY KEY NOT NULL,
                    last_name  TEXT NOT NULL,
                    pin_hash   TEXT NOT NULL,
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL
                )
                """);
        }
    }

    public synchronized boolean hasPin(UUID uuid) {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT 1 FROM pins WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            log.severe("hasPin failed for " + uuid + ": " + e.getMessage());
            return false;
        }
    }

    public synchronized void savePin(UUID uuid, String lastName, String pin) throws SQLException {
        String hash = PinHasher.hash(pin);
        long now = System.currentTimeMillis();
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO pins (uuid, last_name, pin_hash, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT(uuid) DO UPDATE SET
                    last_name = excluded.last_name,
                    pin_hash  = excluded.pin_hash,
                    updated_at = excluded.updated_at
                """)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, lastName);
            ps.setString(3, hash);
            ps.setLong(4, now);
            ps.setLong(5, now);
            ps.executeUpdate();
        }
    }

    public synchronized boolean verifyPin(UUID uuid, String pin) {
        Optional<String> hash = loadHash(uuid);
        return hash.isPresent() && PinHasher.verify(pin, hash.get());
    }

    public synchronized void touchName(UUID uuid, String name) {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE pins SET last_name = ?, updated_at = ? WHERE uuid = ?")) {
            ps.setString(1, name);
            ps.setLong(2, System.currentTimeMillis());
            ps.setString(3, uuid.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            log.warning("touchName failed for " + uuid + ": " + e.getMessage());
        }
    }

    private Optional<String> loadHash(UUID uuid) {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT pin_hash FROM pins WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(rs.getString(1));
                return Optional.empty();
            }
        } catch (SQLException e) {
            log.severe("loadHash failed for " + uuid + ": " + e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public synchronized void close() {
        try {
            if (conn != null && !conn.isClosed()) conn.close();
        } catch (SQLException e) {
            log.warning("Failed to close PinStore: " + e.getMessage());
        }
    }
}
