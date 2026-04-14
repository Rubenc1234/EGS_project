package egs.transactions_service.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class DatabaseConstraintFixer implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) {
        log.info("=== Checking for database constraints to fix ===");
        try {
            // Drop the status check constraint that might prevent new enum values
            jdbcTemplate.execute("ALTER TABLE transactions DROP CONSTRAINT IF EXISTS transactions_status_check");
            log.info("=== Successfully dropped transactions_status_check constraint (if it existed) ===");
            
            // Also drop the type check constraint just in case it's restrictive
            jdbcTemplate.execute("ALTER TABLE transactions DROP CONSTRAINT IF EXISTS transactions_type_check");
            log.info("=== Successfully dropped transactions_type_check constraint (if it existed) ===");
        } catch (Exception e) {
            log.error("=== Error while fixing database constraints: {} ===", e.getMessage());
        }
    }
}
