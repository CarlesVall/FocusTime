package com.focustime.service;

import com.focustime.model.TimeEntry;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

public class TimerService {
    private final TimeTrackingService timeTrackingService;
    private final Map<Long, LocalDateTime> runningSessions = new HashMap<>();

    public TimerService(TimeTrackingService timeTrackingService) {
        this.timeTrackingService = timeTrackingService;
    }

    public void start(Long taskId) {
        if (taskId == null) {
            throw new IllegalArgumentException("Selecciona una tarea para iniciar el temporizador.");
        }
        if (runningSessions.containsKey(taskId)) {
            throw new IllegalStateException("La tarea seleccionada ya tiene un temporizador activo.");
        }

        runningSessions.put(taskId, LocalDateTime.now());
    }

    public TimeEntry stopAndRegister(Long taskId, String note) {
        if (taskId == null) {
            throw new IllegalArgumentException("Selecciona una tarea para pausar el temporizador.");
        }
        LocalDateTime startTime = runningSessions.get(taskId);
        if (startTime == null) {
            throw new IllegalStateException("La tarea seleccionada no tiene un temporizador activo.");
        }

        LocalDateTime endTime = LocalDateTime.now();
        TimeEntry entry = timeTrackingService.registerEntry(taskId, startTime, endTime, note);
        runningSessions.remove(taskId);
        return entry;
    }

    public void cancel(Long taskId) {
        if (taskId == null) {
            throw new IllegalArgumentException("Selecciona una tarea para cancelar el temporizador.");
        }
        if (!runningSessions.containsKey(taskId)) {
            throw new IllegalStateException("La tarea seleccionada no tiene un temporizador activo.");
        }
        runningSessions.remove(taskId);
    }

    public boolean isRunning(Long taskId) {
        return taskId != null && runningSessions.containsKey(taskId);
    }

    public boolean hasRunningSessions() {
        return !runningSessions.isEmpty();
    }

    public Duration getElapsed(Long taskId) {
        LocalDateTime startTime = taskId == null ? null : runningSessions.get(taskId);
        if (startTime == null) {
            return Duration.ZERO;
        }
        return Duration.between(startTime, LocalDateTime.now());
    }
}
