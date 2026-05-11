package com.focustime.repository;

import com.focustime.model.TimeEntry;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class TimeEntryRepository {
    private final DatabaseManager databaseManager;

    public TimeEntryRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    public TimeEntry save(TimeEntry entry) {
        String sql = """
                INSERT INTO time_entries (task_id, start_time, end_time, duration_seconds, entry_date, note, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """;
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, entry.getTaskId());
            statement.setString(2, entry.getStartTime().toString());
            statement.setString(3, entry.getEndTime().toString());
            statement.setLong(4, entry.getDurationSeconds());
            statement.setString(5, entry.getEntryDate().toString());
            statement.setString(6, entry.getNote());
            statement.setString(7, entry.getCreatedAt().toString());
            statement.executeUpdate();

            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    entry.setId(keys.getLong(1));
                }
            }
            return entry;
        } catch (SQLException e) {
            throw new RepositoryException("No se pudo guardar el registro de tiempo", e);
        }
    }

    public List<TimeEntry> findByDate(LocalDate date) {
        String sql = "SELECT * FROM time_entries WHERE entry_date = ? ORDER BY start_time DESC";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, date.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                List<TimeEntry> entries = new ArrayList<>();
                while (resultSet.next()) {
                    entries.add(mapEntry(resultSet));
                }
                return entries;
            }
        } catch (SQLException e) {
            throw new RepositoryException("No se pudieron cargar los registros del dia", e);
        }
    }

    public Optional<TimeEntry> findById(Long id) {
        String sql = "SELECT * FROM time_entries WHERE id = ?";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.of(mapEntry(resultSet));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new RepositoryException("No se pudo cargar el registro de tiempo", e);
        }
    }

    public TimeEntry update(TimeEntry entry) {
        String sql = """
                UPDATE time_entries
                SET task_id = ?, start_time = ?, end_time = ?, duration_seconds = ?, entry_date = ?, note = ?
                WHERE id = ?
                """;
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, entry.getTaskId());
            statement.setString(2, entry.getStartTime().toString());
            statement.setString(3, entry.getEndTime().toString());
            statement.setLong(4, entry.getDurationSeconds());
            statement.setString(5, entry.getEntryDate().toString());
            statement.setString(6, entry.getNote());
            statement.setLong(7, entry.getId());
            int updatedRows = statement.executeUpdate();
            if (updatedRows == 0) {
                throw new IllegalArgumentException("No se encontro el registro de tiempo para actualizar.");
            }
            return entry;
        } catch (SQLException e) {
            throw new RepositoryException("No se pudo actualizar el registro de tiempo", e);
        }
    }

    public void deleteById(Long id) {
        String sql = "DELETE FROM time_entries WHERE id = ?";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new RepositoryException("No se pudo eliminar el registro de tiempo", e);
        }
    }

    public void deleteByTaskId(Long taskId) {
        String sql = "DELETE FROM time_entries WHERE task_id = ?";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, taskId);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new RepositoryException("No se pudieron eliminar los registros de la tarea", e);
        }
    }

    public long getTotalSecondsByDate(LocalDate date) {
        String sql = "SELECT COALESCE(SUM(duration_seconds), 0) FROM time_entries WHERE entry_date = ?";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, date.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getLong(1) : 0L;
            }
        } catch (SQLException e) {
            throw new RepositoryException("No se pudo calcular el total del dia", e);
        }
    }

    public Map<Long, Long> getSecondsByTaskForDate(LocalDate date) {
        String sql = """
                SELECT task_id, SUM(duration_seconds) AS total_seconds
                FROM time_entries
                WHERE entry_date = ?
                GROUP BY task_id
                """;
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, date.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                Map<Long, Long> totals = new HashMap<>();
                while (resultSet.next()) {
                    totals.put(resultSet.getLong("task_id"), resultSet.getLong("total_seconds"));
                }
                return totals;
            }
        } catch (SQLException e) {
            throw new RepositoryException("No se pudieron calcular los totales por tarea", e);
        }
    }

    public Map<LocalDate, Long> getTotalSecondsByDateRange(LocalDate startDate, LocalDate endDate) {
        String sql = """
                SELECT entry_date, SUM(duration_seconds) AS total_seconds
                FROM time_entries
                WHERE entry_date BETWEEN ? AND ?
                GROUP BY entry_date
                """;
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, startDate.toString());
            statement.setString(2, endDate.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                Map<LocalDate, Long> totalsByDate = new HashMap<>();
                while (resultSet.next()) {
                    totalsByDate.put(LocalDate.parse(resultSet.getString("entry_date")), resultSet.getLong("total_seconds"));
                }
                return totalsByDate;
            }
        } catch (SQLException e) {
            throw new RepositoryException("No se pudieron cargar los totales del calendario", e);
        }
    }

    public Map<LocalDate, Integer> getEntryCountByDateRange(LocalDate startDate, LocalDate endDate) {
        String sql = """
                SELECT entry_date, COUNT(*) AS total_entries
                FROM time_entries
                WHERE entry_date BETWEEN ? AND ?
                GROUP BY entry_date
                """;
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, startDate.toString());
            statement.setString(2, endDate.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                Map<LocalDate, Integer> entriesByDate = new HashMap<>();
                while (resultSet.next()) {
                    entriesByDate.put(LocalDate.parse(resultSet.getString("entry_date")), resultSet.getInt("total_entries"));
                }
                return entriesByDate;
            }
        } catch (SQLException e) {
            throw new RepositoryException("No se pudo cargar el numero de sesiones del calendario", e);
        }
    }

    private TimeEntry mapEntry(ResultSet resultSet) throws SQLException {
        return new TimeEntry(
                resultSet.getLong("id"),
                resultSet.getLong("task_id"),
                LocalDateTime.parse(resultSet.getString("start_time")),
                LocalDateTime.parse(resultSet.getString("end_time")),
                resultSet.getLong("duration_seconds"),
                LocalDate.parse(resultSet.getString("entry_date")),
                resultSet.getString("note"),
                LocalDateTime.parse(resultSet.getString("created_at"))
        );
    }
}
