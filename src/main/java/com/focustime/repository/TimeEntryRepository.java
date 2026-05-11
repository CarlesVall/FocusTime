package com.focustime.repository;

import com.focustime.model.TimeEntry;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

public class TimeEntryRepository {
    private final JpaManager jpaManager;

    public TimeEntryRepository(JpaManager jpaManager) {
        this.jpaManager = jpaManager;
    }

    public TimeEntry save(TimeEntry entry) {
        return inTransaction(entityManager -> {
            entityManager.persist(entry);
            return entry;
        }, "No se pudo guardar el registro de tiempo");
    }

    public List<TimeEntry> findByDate(LocalDate date) {
        try (EntityManager entityManager = jpaManager.createEntityManager()) {
            return entityManager
                    .createQuery("""
                            SELECT entry
                            FROM TimeEntry entry
                            WHERE entry.entryDate = :date
                            ORDER BY entry.startTime DESC
                            """, TimeEntry.class)
                    .setParameter("date", date)
                    .getResultList();
        } catch (PersistenceException e) {
            throw new RepositoryException("No se pudieron cargar los registros del dia", e);
        }
    }

    public Optional<TimeEntry> findById(Long id) {
        try (EntityManager entityManager = jpaManager.createEntityManager()) {
            return Optional.ofNullable(entityManager.find(TimeEntry.class, id));
        } catch (PersistenceException e) {
            throw new RepositoryException("No se pudo cargar el registro de tiempo", e);
        }
    }

    public TimeEntry update(TimeEntry entry) {
        return inTransaction(entityManager -> entityManager.merge(entry), "No se pudo actualizar el registro de tiempo");
    }

    public void deleteById(Long id) {
        inTransaction(entityManager -> {
            TimeEntry entry = entityManager.find(TimeEntry.class, id);
            if (entry != null) {
                entityManager.remove(entry);
            }
            return null;
        }, "No se pudo eliminar el registro de tiempo");
    }

    public void deleteByTaskId(Long taskId) {
        inTransaction(entityManager -> {
            entityManager
                    .createQuery("DELETE FROM TimeEntry entry WHERE entry.taskId = :taskId")
                    .setParameter("taskId", taskId)
                    .executeUpdate();
            return null;
        }, "No se pudieron eliminar los registros de la tarea");
    }

    public long getTotalSecondsByDate(LocalDate date) {
        try (EntityManager entityManager = jpaManager.createEntityManager()) {
            Long total = entityManager
                    .createQuery("""
                            SELECT COALESCE(SUM(entry.durationSeconds), 0)
                            FROM TimeEntry entry
                            WHERE entry.entryDate = :date
                            """, Long.class)
                    .setParameter("date", date)
                    .getSingleResult();
            return total == null ? 0L : total;
        } catch (PersistenceException e) {
            throw new RepositoryException("No se pudo calcular el total del dia", e);
        }
    }

    public Map<Long, Long> getSecondsByTaskForDate(LocalDate date) {
        try (EntityManager entityManager = jpaManager.createEntityManager()) {
            List<Object[]> rows = entityManager
                    .createQuery("""
                            SELECT entry.taskId, SUM(entry.durationSeconds)
                            FROM TimeEntry entry
                            WHERE entry.entryDate = :date
                            GROUP BY entry.taskId
                            """, Object[].class)
                    .setParameter("date", date)
                    .getResultList();

            Map<Long, Long> totals = new HashMap<>();
            for (Object[] row : rows) {
                totals.put((Long) row[0], (Long) row[1]);
            }
            return totals;
        } catch (PersistenceException e) {
            throw new RepositoryException("No se pudieron calcular los totales por tarea", e);
        }
    }

    public Map<LocalDate, Long> getTotalSecondsByDateRange(LocalDate startDate, LocalDate endDate) {
        try (EntityManager entityManager = jpaManager.createEntityManager()) {
            List<Object[]> rows = entityManager
                    .createQuery("""
                            SELECT entry.entryDate, SUM(entry.durationSeconds)
                            FROM TimeEntry entry
                            WHERE entry.entryDate BETWEEN :startDate AND :endDate
                            GROUP BY entry.entryDate
                            """, Object[].class)
                    .setParameter("startDate", startDate)
                    .setParameter("endDate", endDate)
                    .getResultList();

            Map<LocalDate, Long> totalsByDate = new HashMap<>();
            for (Object[] row : rows) {
                totalsByDate.put((LocalDate) row[0], (Long) row[1]);
            }
            return totalsByDate;
        } catch (PersistenceException e) {
            throw new RepositoryException("No se pudieron cargar los totales del calendario", e);
        }
    }

    public Map<LocalDate, Integer> getEntryCountByDateRange(LocalDate startDate, LocalDate endDate) {
        try (EntityManager entityManager = jpaManager.createEntityManager()) {
            List<Object[]> rows = entityManager
                    .createQuery("""
                            SELECT entry.entryDate, COUNT(entry)
                            FROM TimeEntry entry
                            WHERE entry.entryDate BETWEEN :startDate AND :endDate
                            GROUP BY entry.entryDate
                            """, Object[].class)
                    .setParameter("startDate", startDate)
                    .setParameter("endDate", endDate)
                    .getResultList();

            Map<LocalDate, Integer> entriesByDate = new HashMap<>();
            for (Object[] row : rows) {
                entriesByDate.put((LocalDate) row[0], ((Long) row[1]).intValue());
            }
            return entriesByDate;
        } catch (PersistenceException e) {
            throw new RepositoryException("No se pudo cargar el numero de sesiones del calendario", e);
        }
    }

    private <T> T inTransaction(Function<EntityManager, T> operation, String errorMessage) {
        EntityManager entityManager = jpaManager.createEntityManager();
        try (entityManager) {
            entityManager.getTransaction().begin();
            T result = operation.apply(entityManager);
            entityManager.getTransaction().commit();
            return result;
        } catch (RuntimeException e) {
            if (entityManager.getTransaction().isActive()) {
                entityManager.getTransaction().rollback();
            }
            throw new RepositoryException(errorMessage, e);
        }
    }
}
