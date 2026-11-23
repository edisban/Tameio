package com.tameio;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

/** The real infrastructure for tests, in containers, with the same images and settings as compose.yaml. */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    private static final int S3_PORT = 8333;

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"));
    }

    @Bean
    @ServiceConnection
    RabbitMQContainer rabbitContainer() {
        return new RabbitMQContainer(DockerImageName.parse("rabbitmq:4.1-management-alpine"));
    }

    @Bean
    // Not a leak: the container is a bean, and Spring stops it when the test context closes
    @SuppressWarnings("resource")
    GenericContainer<?> storageContainer() {
        return new GenericContainer<>(DockerImageName.parse("chrislusf/seaweedfs:4.50"))
            .withCommand("server", "-dir=/data", "-s3", "-s3.port=" + S3_PORT, "-s3.config=/etc/seaweedfs/s3.json")
            // The same identities file local development uses
            .withCopyFileToContainer(MountableFile.forHostPath("../infra/seaweedfs/s3.json"), "/etc/seaweedfs/s3.json")
            .withExposedPorts(S3_PORT)
            .waitingFor(Wait.forHttp("/healthz").forPort(S3_PORT));
    }

    @Bean
    DynamicPropertyRegistrar storageProperties(GenericContainer<?> storageContainer) {
        return registry -> registry.add("tameio.storage.endpoint",
            () -> "http://%s:%d".formatted(storageContainer.getHost(), storageContainer.getMappedPort(S3_PORT)));
    }
}
