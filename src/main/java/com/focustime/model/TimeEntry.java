package com.focustime.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class TimeEntry {
    private Long id;
    private Long taskId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private long durationSeconds;
    private LocalDate entryDate;
    private String note;
    private LocalDateTime createdAt;

    public TimeEntry(Long id, Long taskId, LocalDateTime startTime, LocalDateTime endTime, long durationSeconds,
                     LocalDate entryDate, String note, LocalDateTime createdAt) {
        this.id = id;
        this.taskId = taskId;
        this.startTime = startTime;
        this.endTime = endTime;
        this.durationSeconds = durationSeconds;
        this.entryDate = entryDate;
        this.note = note;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getTaskId() {
        return taskId;
    }

    public void setTaskId(Long taskId) {
        this.taskId = taskId;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
    }

    public long getDurationSeconds() {
        return durationSeconds;
    }

    public void setDurationSeconds(long durationSeconds) {
        this.durationSeconds = durationSeconds;
    }

    public LocalDate getEntryDate() {
        return entryDate;
    }

    public void setEntryDate(LocalDate entryDate) {
        this.entryDate = entryDate;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
