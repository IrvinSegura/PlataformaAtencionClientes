package com.irvinsegura.supportchat.shared.infrastructure;

import javax.sql.DataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@Configuration(proxyBeanMethods = false)
@EnableTransactionManagement
class PersistenceConfiguration {

    // Replaces Boot's default transaction manager, so every transaction is scoped to the bound workspace
    @Bean
    JdbcTransactionManager transactionManager(DataSource dataSource) {
        TenantAwareTransactionManager manager = new TenantAwareTransactionManager(dataSource);
        // Read-only transactions are read-only in PostgreSQL too, so a query can never write by mistake
        manager.setEnforceReadOnly(true);
        return manager;
    }
}
