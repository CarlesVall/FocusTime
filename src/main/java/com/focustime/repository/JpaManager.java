package com.focustime.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class JpaManager implements AutoCloseable {
    private static final String PERSISTENCE_UNIT_NAME = "focustime";
    private static final String DATABASE_FILE_NAME = "focustime.db";
    private static final Path DATA_DIRECTORY = Path.of(System.getenv("APPDATA"), "FocusTime");
    private static final Path DATABASE_PATH = DATA_DIRECTORY.resolve(DATABASE_FILE_NAME);
    private static final Path LEGACY_DATABASE_PATH = Path.of(DATABASE_FILE_NAME);

    private EntityManagerFactory entityManagerFactory;

    public void initialize() {
        configureHibernateLogging();
        getEntityManagerFactory();
    }

    public EntityManager createEntityManager() {
        return getEntityManagerFactory().createEntityManager();
    }

    private EntityManagerFactory getEntityManagerFactory() {
        if (entityManagerFactory == null || !entityManagerFactory.isOpen()) {
            ensureDataDirectory();
            migrateLegacyDatabaseIfNeeded();
            entityManagerFactory = Persistence.createEntityManagerFactory(PERSISTENCE_UNIT_NAME, properties());
            initializeTaskPositions();
        }
        return entityManagerFactory;
    }

    private Map<String, String> properties() {
        Map<String, String> properties = new HashMap<>();
        properties.put("jakarta.persistence.jdbc.driver", "org.sqlite.JDBC");
        properties.put("jakarta.persistence.jdbc.url", "jdbc:sqlite:" + DATABASE_PATH.toAbsolutePath());
        return properties;
    }

    private void configureHibernateLogging() {
        System.setProperty("org.jboss.logging.provider", "slf4j");
        Logger.getLogger("org.hibernate").setLevel(Level.WARNING);
        Logger.getLogger("org.hibernate.engine.jdbc.connections.internal.DriverManagerConnectionProviderImpl").setLevel(Level.SEVERE);
    }

    private void initializeTaskPositions() {
        EntityManager entityManager = entityManagerFactory.createEntityManager();
        try (entityManager) {
            entityManager.getTransaction().begin();
            entityManager
                    .createNativeQuery("UPDATE tasks SET position_index = id WHERE position_index IS NULL")
                    .executeUpdate();
            entityManager.getTransaction().commit();
        } catch (RuntimeException e) {
            if (entityManager.getTransaction().isActive()) {
                entityManager.getTransaction().rollback();
            }
            throw new RepositoryException("No se pudo inicializar el orden de las tareas", e);
        }
    }

    private void ensureDataDirectory() {
        try {
            Files.createDirectories(DATA_DIRECTORY);
        } catch (IOException e) {
            throw new RepositoryException("No se pudo crear la carpeta de datos de FocusTime", e);
        }
    }

    private void migrateLegacyDatabaseIfNeeded() {
        try {
            if (Files.exists(LEGACY_DATABASE_PATH) && !Files.exists(DATABASE_PATH)) {
                Files.copy(LEGACY_DATABASE_PATH, DATABASE_PATH, StandardCopyOption.COPY_ATTRIBUTES);
            }
        } catch (IOException e) {
            throw new RepositoryException("No se pudo migrar la base de datos existente", e);
        }
    }

    @Override
    public void close() {
        if (entityManagerFactory != null && entityManagerFactory.isOpen()) {
            entityManagerFactory.close();
        }
    }
}
