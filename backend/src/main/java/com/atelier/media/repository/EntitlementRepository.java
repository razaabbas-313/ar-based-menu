package com.atelier.media.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import javax.sql.DataSource;

/** JDBC shares the JPA transaction/connection; JSON contains only registry-validated values. */
@Repository
public class EntitlementRepository extends JdbcTemplate {
    public EntitlementRepository(DataSource dataSource) { super(dataSource); }
}
