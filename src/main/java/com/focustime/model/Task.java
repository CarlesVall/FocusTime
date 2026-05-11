package com.focustime.model;

import java.time.LocalDateTime;

public class Task {
    private Long id;
    private String name;
    private int dailyObjectiveMinutes;
    private String scheduledDays;
    private boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Task(Long id, String name, int dailyObjectiveMinutes, String scheduledDays, boolean active, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.name = name;
        this.dailyObjectiveMinutes = dailyObjectiveMinutes;
        this.scheduledDays = scheduledDays;
        this.active = active;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getDailyObjectiveMinutes() {
        return dailyObjectiveMinutes;
    }

    public void setDailyObjectiveMinutes(int dailyObjectiveMinutes) {
        this.dailyObjectiveMinutes = dailyObjectiveMinutes;
    }

    public String getScheduledDays() {
        return scheduledDays;
    }

    public void setScheduledDays(String scheduledDays) {
        this.scheduledDays = scheduledDays;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
