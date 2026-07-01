package dev.dkocaj.boppin;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
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
        migrate(log);
    }

    private void migrate(Logger log) throws SQLException {
        if (!columnExists("pins", "canonical_name")) {
            log.info("[BopPin] Migrating: adding canonical_name column");
            try (Statement s = conn.createStatement()) {
                s.execute("ALTER TABLE pins ADD COLUMN canonical_name TEXT");
                s.execute("UPDATE pins SET canonical_name = LOWER(TRIM(last_name)) WHERE canonical_name IS NULL");
                s.execute("CREATE INDEX IF NOT EXISTS idx_pins_canonical_name ON pins(canonical_name)");
            }
            log.info("[BopPin] Migration complete: canonical_name populated from existing last_name values");
        }
    }

    private boolean columnExists(String table, String column) throws SQLException {
        DatabaseMetaData meta = conn.getMetaData();
        try (ResultSet rs = meta.getColumns(null, null, table, column)) {
            return rs.next();
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
            throw new RuntimeException("hasPin failed for " + uuid, e);
        }
    }

    /**
     * Returns the owner UUID for a canonical name, or null if not registered.
     * Throws RuntimeException on DB error (caller must fail-closed).
     */
    public synchronized UUID findOwnerByCanonicalName(String canonicalName) {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT uuid FROM pins WHERE canonical_name = ?")) {
            ps.setString(1, canonicalName);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return UUID.fromString(rs.getString(1));
                return null;
            }
        } catch (SQLException e) {
            throw new RuntimeException("findOwnerByCanonicalName failed for " + canonicalName, e);
        }
    }

    public synchronized void savePin(UUID uuid, String lastName, String canonicalName,
                                     String pin) throws SQLException {
        String hash = PinHasher.hash(pin);
        long now = System.currentTimeMillis();
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO pins (uuid, last_name, canonical_name, pin_hash, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT(uuid) DO UPDATE SET
                    last_name = excluded.last_name,
                    canonical_name = excluded.canonical_name,
                    pin_hash  = excluded.pin_hash,
                    updated_at = excluded.updated_at
                """)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, lastName);
            ps.setString(3, canonicalName);
            ps.setString(4, hash);
            ps.setLong(5, now);
            ps.setLong(6, now);
            ps.executeUpdate();
        }
    }

    public synchronized boolean verifyPin(UUID uuid, String pin) {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT pin_hash FROM pins WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return PinHasher.verify(pin, rs.getString(1));
                return false;
            }
        } catch (SQLException e) {
            throw new RuntimeException("verifyPin failed for " + uuid, e);
        }
    }

    public synchronized void touchName(UUID uuid, String name, String canonicalName) {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE pins SET last_name = ?, canonical_name = ?, updated_at = ? WHERE uuid = ?")) {
            ps.setString(1, name);
            ps.setString(2, canonicalName);
            ps.setLong(3, System.currentTimeMillis());
            ps.setString(4, uuid.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            log.warning("touchName failed for " + uuid + ": " + e.getMessage());
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
