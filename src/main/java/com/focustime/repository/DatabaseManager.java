package com.focustime.repository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseManager {
    private static final String DATABASE_FILE_NAME = "focustime.db";
    private static final Path DATA_DIRECTORY = Path.of(System.getenv("APPDATA"), "FocusTime");
    private static final Path DATABASE_PATH = DATA_DIRECTORY.resolve(DATABASE_FILE_NAME);
    private static final Path LEGACY_DATABASE_PATH = Path.of(DATABASE_FILE_NAME);
    private static final String DATABASE_URL = "jdbc:sqlite:" + DATABASE_PATH.toAbsolutePath();

    public Connection getConnection() throws SQLException {
        ensureDataDirectory();
        migrateLegacyDatabaseIfNeeded();
        Connection connection = DriverManager.getConnection(DATABASE_URL);
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
        }
        return connection;
    }

    public void initializeDatabase() {
        try (Connection connection = getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS tasks (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        name TEXT NOT NULL,
                        daily_objective_minutes INTEGER NOT NULL,
                        scheduled_days TEXT NOT NULL DEFAULT '1,2,3,4,5,6,7',
                        active INTEGER NOT NULL DEFAULT 1,
                        created_at TEXT NOT NULL,
                        updated_at TEXT NOT NULL
                    )
                    """);
            ensureScheduledDaysColumn(connection);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS time_entries (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        task_id INTEGER NOT NULL,
                        start_time TEXT NOT NULL,
                        end_time TEXT NOT NULL,
                        duration_seconds INTEGER NOT NULL,
                        entry_date TEXT NOT NULL,
                        note TEXT,
                        created_at TEXT NOT NULL,
                        FOREIGN KEY (task_id) REFERENCES tasks(id)
                    )
                    """);
            statement.execute("""
                    CREATE INDEX IF NOT EXISTS idx_time_entries_entry_date
                    ON time_entries(entry_date)
                    """);
            statement.execute("""
                    CREATE INDEX IF NOT EXISTS idx_time_entries_task_id
                    ON time_entries(task_id)
                    """);
        } catch (SQLException e) {
            throw new RepositoryException("No se pudo inicializar la base de datos", e);
        }
    }

    private void ensureScheduledDaysColumn(Connection connection) throws SQLException {
        boolean hasColumn = false;
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("PRAGMA table_info(tasks)")) {
            while (resultSet.next()) {
                if ("scheduled_days".equalsIgnoreCase(resultSet.getString("name"))) {
                    hasColumn = true;
                    break;
                }
            }
        }

        if (!hasColumn) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("ALTER TABLE tasks ADD COLUMN scheduled_days TEXT NOT NULL DEFAULT '1,2,3,4,5,6,7'");
                statement.execute("UPDATE tasks SET scheduled_days = '1,2,3,4,5,6,7' WHERE scheduled_days IS NULL OR scheduled_days = ''");
            }
        }
    }

    private void ensureDataDirectory() {
        try {
            Files.createDirectories(DATA_DIRECTORY);
        } catch (IOException e) {
            throw new RepositoryException("No se pudo crear la carpeta de datos de FocusTime", e);
        }
    }

    private void migrateLegacyDatabaseIfNeeded() {
        try {
            if (Files.exists(LEGACY_DATABASE_PATH) && !Files.exists(DATABASE_PATH)) {
                Files.copy(LEGACY_DATABASE_PATH, DATABASE_PATH, StandardCopyOption.COPY_ATTRIBUTES);
            }
        } catch (IOException e) {
            throw new RepositoryException("No se pudo migrar la base de datos existente", e);
        }
    }
}
