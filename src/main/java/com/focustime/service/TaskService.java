package com.focustime.service;

import com.focustime.model.Task;
import com.focustime.repository.TaskRepository;
import com.focustime.repository.TimeEntryRepository;

import java.time.LocalDateTime;
import java.util.List;

public class TaskService {
    private final TaskRepository taskRepository;
    private final TimeEntryRepository timeEntryRepository;

    public TaskService(TaskRepository taskRepository, TimeEntryRepository timeEntryRepository) {
        this.taskRepository = taskRepository;
        this.timeEntryRepository = timeEntryRepository;
    }

    public Task createTask(String name, int dailyObjectiveMinutes, String scheduledDays) {
        String normalizedName = validateName(name);
        if (dailyObjectiveMinutes <= 0) {
            throw new IllegalArgumentException("El objetivo diario debe ser mayor que cero.");
        }
        String normalizedScheduledDays = validateScheduledDays(scheduledDays);
        if (taskRepository.existsActiveByName(normalizedName)) {
            throw new IllegalArgumentException("Ya existe una tarea activa con ese nombre.");
        }

        LocalDateTime now = LocalDateTime.now();
        Task task = new Task(null, normalizedName, dailyObjectiveMinutes, normalizedScheduledDays, true, now, now);
        return taskRepository.save(task);
    }

    public Task updateTask(Long id, String name, int dailyObjectiveMinutes, String scheduledDays) {
        if (id == null) {
            throw new IllegalArgumentException("La tarea no existe.");
        }

        Task existingTask = taskRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("La tarea no existe."));

        String normalizedName = validateName(name);
        if (dailyObjectiveMinutes <= 0) {
            throw new IllegalArgumentException("El objetivo diario debe ser mayor que cero.");
        }
        String normalizedScheduledDays = validateScheduledDays(scheduledDays);
        if (taskRepository.existsActiveByNameExcludingId(normalizedName, id)) {
            throw new IllegalArgumentException("Ya existe una tarea activa con ese nombre.");
        }

        existingTask.setName(normalizedName);
        existingTask.setDailyObjectiveMinutes(dailyObjectiveMinutes);
        existingTask.setScheduledDays(normalizedScheduledDays);
        existingTask.setUpdatedAt(LocalDateTime.now());
        return taskRepository.update(existingTask);
    }

    public List<Task> findAllTasks() {
        return taskRepository.findAll();
    }

    public List<Task> findActiveTasks() {
        return taskRepository.findActive();
    }

    public void deleteTask(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("La tarea no existe.");
        }
        taskRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("La tarea no existe."));
        timeEntryRepository.deleteByTaskId(id);
        taskRepository.deleteById(id);
    }

    private String validateName(String name) {
        String normalizedName = name == null ? "" : name.trim();
        if (normalizedName.isEmpty()) {
            throw new IllegalArgumentException("El nombre de la tarea no puede estar vacio.");
        }
        return normalizedName;
    }

    private String validateScheduledDays(String scheduledDays) {
        String normalized = scheduledDays == null ? "" : scheduledDays.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Selecciona al menos un dia para la tarea.");
        }
        return normalized;
    }
}
