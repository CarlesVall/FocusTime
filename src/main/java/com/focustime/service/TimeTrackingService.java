package com.focustime.service;

import com.focustime.model.TimeEntry;
import com.focustime.repository.TimeEntryRepository;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public class TimeTrackingService {
    private final TimeEntryRepository timeEntryRepository;

    public TimeTrackingService(TimeEntryRepository timeEntryRepository) {
        this.timeEntryRepository = timeEntryRepository;
    }

    public TimeEntry registerEntry(Long taskId, LocalDateTime start, LocalDateTime end, String note) {
        if (taskId == null) {
            throw new IllegalArgumentException("Selecciona una tarea antes de registrar tiempo.");
        }
        long durationSeconds = validateDuration(start, end);

        String cleanNote = note == null || note.isBlank() ? null : note.trim();
        TimeEntry entry = new TimeEntry(
                null,
                taskId,
                start,
                end,
                durationSeconds,
                start.toLocalDate(),
                cleanNote,
                LocalDateTime.now()
        );
        return timeEntryRepository.save(entry);
    }

    public List<TimeEntry> findEntriesByDate(LocalDate date) {
        return timeEntryRepository.findByDate(date);
    }

    public long getTotalSecondsByDate(LocalDate date) {
        return timeEntryRepository.getTotalSecondsByDate(date);
    }

    public Map<Long, Long> getSecondsByTaskForDate(LocalDate date) {
        return timeEntryRepository.getSecondsByTaskForDate(date);
    }

    public Map<LocalDate, Long> getTotalSecondsByDateRange(LocalDate startDate, LocalDate endDate) {
        return timeEntryRepository.getTotalSecondsByDateRange(startDate, endDate);
    }

    public Map<LocalDate, Integer> getEntryCountByDateRange(LocalDate startDate, LocalDate endDate) {
        return timeEntryRepository.getEntryCountByDateRange(startDate, endDate);
    }

    public TimeEntry updateEntry(Long entryId, Long taskId, LocalDateTime start, LocalDateTime end) {
        if (entryId == null) {
            throw new IllegalArgumentException("El registro no existe.");
        }
        if (taskId == null) {
            throw new IllegalArgumentException("Selecciona una tarea para el registro.");
        }

        TimeEntry existing = timeEntryRepository.findById(entryId)
                .orElseThrow(() -> new IllegalArgumentException("El registro no existe."));

        long durationSeconds = validateDuration(start, end);
        existing.setTaskId(taskId);
        existing.setStartTime(start);
        existing.setEndTime(end);
        existing.setDurationSeconds(durationSeconds);
        existing.setEntryDate(start.toLocalDate());
        return timeEntryRepository.update(existing);
    }

    public void deleteEntry(Long entryId) {
        if (entryId == null) {
            throw new IllegalArgumentException("El registro no existe.");
        }
        timeEntryRepository.deleteById(entryId);
    }

    private long validateDuration(LocalDateTime start, LocalDateTime end) {
        long durationSeconds = Duration.between(start, end).getSeconds();
        if (durationSeconds <= 0) {
            throw new IllegalArgumentException("No se registran sesiones de duracion cero o negativa.");
        }
        return durationSeconds;
    }
}
