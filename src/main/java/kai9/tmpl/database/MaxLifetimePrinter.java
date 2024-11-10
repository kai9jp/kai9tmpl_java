package kai9.tmpl.database;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import com.zaxxer.hikari.HikariDataSource;

@Component
public class MaxLifetimePrinter implements CommandLineRunner {

    @Autowired
    private DataSource dataSource; // データソースを注入

    @Override
    public void run(String... args) throws Exception {
        if (dataSource instanceof HikariDataSource) {
            HikariDataSource hikariDataSource = (HikariDataSource) dataSource;
            long maxLifetimeMillis = hikariDataSource.getMaxLifetime();
            long maxLifetimeMinutes = maxLifetimeMillis / (60 * 1000);

            System.out.println("Max Lifetime: " + maxLifetimeMinutes + " minutes");
        } else {
            System.out.println("Max Lifetime is not available for this data source.");
        }
    }
}
