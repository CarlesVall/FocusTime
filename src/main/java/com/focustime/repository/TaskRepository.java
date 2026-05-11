package com.focustime.repository;

import com.focustime.model.Task;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;

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
            return entityManager
                    .createQuery("SELECT task FROM Task task ORDER BY task.positionIndex, lower(task.name), task.id", Task.class)
                    .getResultList();
        } catch (PersistenceException e) {
            throw new RepositoryException("No se pudieron cargar las tareas", e);
        }
    }

    public List<Task> findActive() {
        try (EntityManager entityManager = jpaManager.createEntityManager()) {
            return entityManager
                    .createQuery("SELECT task FROM Task task WHERE task.active = true ORDER BY task.positionIndex, lower(task.name), task.id", Task.class)
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

    public int getNextPositionIndex() {
        try (EntityManager entityManager = jpaManager.createEntityManager()) {
            Integer maxPosition = entityManager
                    .createQuery("SELECT COALESCE(MAX(task.positionIndex), -1) FROM Task task", Integer.class)
                    .getSingleResult();
            return maxPosition + 1;
        } catch (PersistenceException e) {
            throw new RepositoryException("No se pudo calcular la posicion de la tarea", e);
        }
    }

    public void updatePositionIndexes(List<Long> orderedTaskIds) {
        inTransaction(entityManager -> {
            for (int index = 0; index < orderedTaskIds.size(); index++) {
                Task task = entityManager.find(Task.class, orderedTaskIds.get(index));
                if (task != null) {
                    task.setPositionIndex(index);
                }
            }
            return null;
        }, "No se pudo actualizar el orden de las tareas");
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
