package org.migration.sharepoint.connection;

import static org.assertj.core.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.migration.sharepoint.infra.connection.ConnectionRegistry;
import org.migration.sharepoint.infra.connection.ConnectionRegistry.ConnectionSummary;
import org.migration.sharepoint.infra.exception.custom.BadRequestException;
import org.migration.sharepoint.infra.exception.custom.ConflictException;
import org.migration.sharepoint.infra.exception.custom.NotFoundException;

class ConnectionRegistryTest {

    private ConnectionRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new ConnectionRegistry();
    }

    // -------------------------------------------------------------------------
    // register
    // -------------------------------------------------------------------------

    @Test
    void shouldRegisterMysqlConnection() {
        registry.register("MYSQL_TEST", "MySQL Test", "jdbc:mysql://localhost:3306/db");
        assertThat(registry.resolveUrl("MYSQL_TEST")).isEqualTo("jdbc:mysql://localhost:3306/db");
    }

    @Test
    void shouldRegisterPostgresConnection() {
        registry.register("PG_TEST", "PG Test", "jdbc:postgresql://localhost:5432/db");
        assertThat(registry.resolveUrl("PG_TEST")).isEqualTo("jdbc:postgresql://localhost:5432/db");
    }

    @Test
    void shouldRegisterMongoConnection() {
        registry.register("MONGO_TEST", "Mongo", "mongodb://localhost:27017/db");
        assertThat(registry.resolveUrl("MONGO_TEST")).isEqualTo("mongodb://localhost:27017/db");
    }

    @Test
    void shouldRegisterMongoSrvConnection() {
        registry.register("MONGO_SRV", "Mongo SRV", "mongodb+srv://cluster.example.net/db");
        assertThat(registry.resolveUrl("MONGO_SRV")).isEqualTo("mongodb+srv://cluster.example.net/db");
    }

    @Test
    void shouldRegisterMariaDbConnection() {
        registry.register("MARIA_TEST", "MariaDB", "jdbc:mariadb://localhost:3306/db");
        assertThat(registry.resolveUrl("MARIA_TEST")).isEqualTo("jdbc:mariadb://localhost:3306/db");
    }

    @Test
    void shouldTrimWhitespaceFromUrl() {
        registry.register("MYSQL_TEST", "MySQL", "  jdbc:mysql://localhost:3306/db  ");
        assertThat(registry.resolveUrl("MYSQL_TEST")).isEqualTo("jdbc:mysql://localhost:3306/db");
    }

    @Test
    void shouldRejectPlainHttpUrl() {
        assertThatThrownBy(() -> registry.register("BAD", "Bad", "http://not-a-db.com"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("jdbc:mysql://");
    }

    @Test
    void shouldRejectArbitraryString() {
        assertThatThrownBy(() -> registry.register("BAD", "Bad", "not-a-url")).isInstanceOf(BadRequestException.class);
    }

    @Test
    void shouldRejectEmptyUrl() {
        assertThatThrownBy(() -> registry.register("BAD", "Bad", "   ")).isInstanceOf(BadRequestException.class);
    }

    @Test
    void shouldThrowConflictOnDuplicateKey() {
        registry.register("MYSQL_TEST", "MySQL", "jdbc:mysql://localhost:3306/db");
        assertThatThrownBy(() -> registry.register("MYSQL_TEST", "MySQL 2", "jdbc:mysql://other:3306/db"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("MYSQL_TEST");
    }

    // -------------------------------------------------------------------------
    // resolveUrl
    // -------------------------------------------------------------------------

    @Test
    void shouldThrowNotFoundForUnknownKey() {
        assertThatThrownBy(() -> registry.resolveUrl("NONEXISTENT"))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("NONEXISTENT");
    }

    @Test
    void shouldResolveAfterRegister() {
        registry.register("PG_PROD", "PG Prod", "jdbc:postgresql://prod:5432/db");
        assertThat(registry.resolveUrl("PG_PROD")).isEqualTo("jdbc:postgresql://prod:5432/db");
    }

    // -------------------------------------------------------------------------
    // list
    // -------------------------------------------------------------------------

    @Test
    void shouldReturnEmptyListWhenNoConnections() {
        assertThat(registry.list()).isEmpty();
    }

    @Test
    void shouldReturnAllConnectionsSortedByKey() {
        registry.register("Z_CONN", "Z", "jdbc:mysql://z:3306/db");
        registry.register("A_CONN", "A", "jdbc:mysql://a:3306/db");
        registry.register("M_CONN", "M", "jdbc:mysql://m:3306/db");

        List<ConnectionSummary> list = registry.list();
        assertThat(list).hasSize(3);
        assertThat(list.get(0).key()).isEqualTo("A_CONN");
        assertThat(list.get(1).key()).isEqualTo("M_CONN");
        assertThat(list.get(2).key()).isEqualTo("Z_CONN");
    }

    @Test
    void shouldNeverExposeUrlInList() {
        registry.register("MYSQL_TEST", "MySQL", "jdbc:mysql://localhost:3306/db?password=secret");
        ConnectionSummary summary = registry.list().getFirst();
        assertThat(summary).hasNoNullFieldsOrProperties();
        // ConnectionSummary only has key and name — no url field
        assertThat(summary.key()).isEqualTo("MYSQL_TEST");
        assertThat(summary.name()).isEqualTo("MySQL");
    }

    // -------------------------------------------------------------------------
    // remove
    // -------------------------------------------------------------------------

    @Test
    void shouldRemoveExistingConnection() {
        registry.register("MYSQL_TEST", "MySQL", "jdbc:mysql://localhost:3306/db");
        registry.remove("MYSQL_TEST");
        assertThatThrownBy(() -> registry.resolveUrl("MYSQL_TEST")).isInstanceOf(NotFoundException.class);
    }

    @Test
    void shouldThrowNotFoundWhenRemovingUnknownKey() {
        assertThatThrownBy(() -> registry.remove("NONEXISTENT"))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("NONEXISTENT");
    }

    @Test
    void shouldAllowReRegisterAfterRemove() {
        registry.register("MYSQL_TEST", "MySQL", "jdbc:mysql://localhost:3306/db");
        registry.remove("MYSQL_TEST");
        registry.register("MYSQL_TEST", "MySQL New", "jdbc:mysql://newhost:3306/db");
        assertThat(registry.resolveUrl("MYSQL_TEST")).isEqualTo("jdbc:mysql://newhost:3306/db");
    }
}
