package com.focustime.repository;

import com.focustime.model.Task;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class TaskRepository {
    private final DatabaseManager databaseManager;

    public TaskRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    public Task save(Task task) {
        String sql = """
                INSERT INTO tasks (name, daily_objective_minutes, scheduled_days, active, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """;
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, task.getName());
            statement.setInt(2, task.getDailyObjectiveMinutes());
            statement.setString(3, task.getScheduledDays());
            statement.setInt(4, task.isActive() ? 1 : 0);
            statement.setString(5, task.getCreatedAt().toString());
            statement.setString(6, task.getUpdatedAt().toString());
            statement.executeUpdate();

            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    task.setId(keys.getLong(1));
                }
            }
            return task;
        } catch (SQLException e) {
            throw new RepositoryException("No se pudo guardar la tarea", e);
        }
    }

    public List<Task> findAll() {
        return findBySql("SELECT * FROM tasks ORDER BY active DESC, name ASC");
    }

    public List<Task> findActive() {
        return findBySql("SELECT * FROM tasks WHERE active = 1 ORDER BY name ASC");
    }

    public Optional<Task> findById(Long id) {
        String sql = "SELECT * FROM tasks WHERE id = ?";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.of(mapTask(resultSet));
                }
            }
            return Optional.empty();
        } catch (SQLException e) {
            throw new RepositoryException("No se pudo buscar la tarea", e);
        }
    }

    public boolean existsActiveByName(String name) {
        String sql = "SELECT COUNT(*) FROM tasks WHERE active = 1 AND lower(name) = lower(?)";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, name);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() && resultSet.getInt(1) > 0;
            }
        } catch (SQLException e) {
            throw new RepositoryException("No se pudo validar el nombre de la tarea", e);
        }
    }

    public boolean existsActiveByNameExcludingId(String name, Long excludedId) {
        String sql = "SELECT COUNT(*) FROM tasks WHERE active = 1 AND lower(name) = lower(?) AND id <> ?";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, name);
            statement.setLong(2, excludedId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() && resultSet.getInt(1) > 0;
            }
        } catch (SQLException e) {
            throw new RepositoryException("No se pudo validar el nombre de la tarea", e);
        }
    }

    public Task update(Task task) {
        String sql = """
                UPDATE tasks
                SET name = ?, daily_objective_minutes = ?, scheduled_days = ?, active = ?, updated_at = ?
                WHERE id = ?
                """;
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, task.getName());
            statement.setInt(2, task.getDailyObjectiveMinutes());
            statement.setString(3, task.getScheduledDays());
            statement.setInt(4, task.isActive() ? 1 : 0);
            statement.setString(5, task.getUpdatedAt().toString());
            statement.setLong(6, task.getId());
            statement.executeUpdate();
            return task;
        } catch (SQLException e) {
            throw new RepositoryException("No se pudo actualizar la tarea", e);
        }
    }

    public void deleteById(Long id) {
        String sql = "DELETE FROM tasks WHERE id = ?";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new RepositoryException("No se pudo eliminar la tarea", e);
        }
    }

    private List<Task> findBySql(String sql) {
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            List<Task> tasks = new ArrayList<>();
            while (resultSet.next()) {
                tasks.add(mapTask(resultSet));
            }
            return tasks;
        } catch (SQLException e) {
            throw new RepositoryException("No se pudieron cargar las tareas", e);
        }
    }

    private Task mapTask(ResultSet resultSet) throws SQLException {
        return new Task(
                resultSet.getLong("id"),
                resultSet.getString("name"),
                resultSet.getInt("daily_objective_minutes"),
                resultSet.getString("scheduled_days"),
                resultSet.getInt("active") == 1,
                LocalDateTime.parse(resultSet.getString("created_at")),
                LocalDateTime.parse(resultSet.getString("updated_at"))
        );
    }
}
