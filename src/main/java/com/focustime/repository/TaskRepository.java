package com.focustime.repository;

import com.focustime.model.Task;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

public class TaskRepository {
    private final JpaManager jpaManager;

    public TaskRepository(JpaManager jpaManager) {
        this.jpaManager = jpaManager;
    }

    public Task save(Task task) {
        return inTransaction(entityManager -> {
            entityManager.persist(task);
            return task;
        }, "No se pudo guardar la tarea");
    }

    public List<Task> findAll() {
        try (EntityManager entityManager = jpaManager.createEntityManager()) {
            List<Task> tasks = entityManager
                    .createQuery("SELECT task FROM Task task", Task.class)
                    .getResultList();
            return tasks.stream()
                    .sorted(Comparator.comparing(Task::isActive).reversed().thenComparing(Task::getName, String.CASE_INSENSITIVE_ORDER))
                    .toList();
        } catch (PersistenceException e) {
            throw new RepositoryException("No se pudieron cargar las tareas", e);
        }
    }

    public List<Task> findActive() {
        try (EntityManager entityManager = jpaManager.createEntityManager()) {
            return entityManager
                    .createQuery("SELECT task FROM Task task WHERE task.active = true ORDER BY lower(task.name)", Task.class)
                    .getResultList();
        } catch (PersistenceException e) {
            throw new RepositoryException("No se pudieron cargar las tareas", e);
        }
    }

    public Optional<Task> findById(Long id) {
        try (EntityManager entityManager = jpaManager.createEntityManager()) {
            return Optional.ofNullable(entityManager.find(Task.class, id));
        } catch (PersistenceException e) {
            throw new RepositoryException("No se pudo buscar la tarea", e);
        }
    }

    public boolean existsActiveByName(String name) {
        try (EntityManager entityManager = jpaManager.createEntityManager()) {
            Long count = entityManager
                    .createQuery("""
                            SELECT COUNT(task)
                            FROM Task task
                            WHERE task.active = true AND lower(task.name) = lower(:name)
                            """, Long.class)
                    .setParameter("name", name)
                    .getSingleResult();
            return count > 0;
        } catch (PersistenceException e) {
            throw new RepositoryException("No se pudo validar el nombre de la tarea", e);
        }
    }

    public boolean existsActiveByNameExcludingId(String name, Long excludedId) {
        try (EntityManager entityManager = jpaManager.createEntityManager()) {
            Long count = entityManager
                    .createQuery("""
                            SELECT COUNT(task)
                            FROM Task task
                            WHERE task.active = true AND lower(task.name) = lower(:name) AND task.id <> :excludedId
                            """, Long.class)
                    .setParameter("name", name)
                    .setParameter("excludedId", excludedId)
                    .getSingleResult();
            return count > 0;
        } catch (PersistenceException e) {
            throw new RepositoryException("No se pudo validar el nombre de la tarea", e);
        }
    }

    public Task update(Task task) {
        return inTransaction(entityManager -> entityManager.merge(task), "No se pudo actualizar la tarea");
    }

    public void deleteById(Long id) {
        inTransaction(entityManager -> {
            Task task = entityManager.find(Task.class, id);
            if (task != null) {
                entityManager.remove(task);
            }
            return null;
        }, "No se pudo eliminar la tarea");
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
