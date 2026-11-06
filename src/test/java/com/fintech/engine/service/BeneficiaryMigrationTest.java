package com.fintech.engine.service;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import java.sql.*;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class BeneficiaryMigrationTest {
    @Test void migrationEnforcesDestinationUniquenessAndAllowsDifferentOwners() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:beneficiary_migration;MODE=PostgreSQL", "sa", "")) {
            connection.createStatement().execute("CREATE TABLE users (id UUID PRIMARY KEY)");
            connection.createStatement().execute("CREATE TABLE accounts (id UUID PRIMARY KEY)");
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/V8__add_beneficiaries.sql"));
            UUID owner = UUID.randomUUID(), other = UUID.randomUUID();
            try (PreparedStatement users = connection.prepareStatement("INSERT INTO users VALUES (?)")) {
                users.setObject(1, owner); users.executeUpdate(); users.setObject(1, other); users.executeUpdate();
            }
            String sql = "INSERT INTO beneficiaries (id,user_id,type,nickname,bank_code,account_number,account_name,currency,destination_key,created_at) VALUES (?,?,'BANK','Alex','ABC','00123456','Alex','USD','BANK:USD:ABC:00123456',CURRENT_TIMESTAMP)";
            try (PreparedStatement insert = connection.prepareStatement(sql)) {
                insert.setObject(1, UUID.randomUUID()); insert.setObject(2, owner); insert.executeUpdate();
                insert.setObject(1, UUID.randomUUID());
                assertThatThrownBy(insert::executeUpdate).isInstanceOf(SQLException.class);
                insert.setObject(1, UUID.randomUUID()); insert.setObject(2, other);
                assertThat(insert.executeUpdate()).isEqualTo(1);
            }
            assertThatThrownBy(() -> connection.createStatement().executeUpdate("UPDATE beneficiaries SET type='WALLET'"))
                .isInstanceOf(SQLException.class);
        }
    }
}
