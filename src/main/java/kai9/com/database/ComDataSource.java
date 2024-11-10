package kai9.com.database;

import javax.persistence.EntityManagerFactory;
import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;

import com.zaxxer.hikari.HikariDataSource;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "spring.datasource.common")
@EnableJpaRepositories(basePackages = "kai9.com.repository", // リポジトリが存在するパッケージを指定
        entityManagerFactoryRef = "comEntityManagerFactory", transactionManagerRef = "comTransactionManager")
public class ComDataSource {

    private String driverClassName;
    private String url;
    private String username;
    private String password;
    private int maximumPoolSize;
    private int minimumIdle;

    @Bean(name = "commonds")
    public DataSource createDataSource() {
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setDriverClassName(driverClassName);
        dataSource.setJdbcUrl(url);
        dataSource.setUsername(username);
        dataSource.setPassword(password);
        dataSource.setMaximumPoolSize(maximumPoolSize);
        dataSource.setMinimumIdle(minimumIdle);
        return dataSource;
    }

    @Bean(name = "commonjdbc")
    public JdbcTemplate createJdbcTemplate(@Qualifier("commonds")
    DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Bean(name = "comEntityManagerFactory")
    public LocalContainerEntityManagerFactoryBean comEntityManagerFactory(
            @Qualifier("commonds")
            DataSource dataSource) {
        LocalContainerEntityManagerFactoryBean em = new LocalContainerEntityManagerFactoryBean();
        em.setDataSource(dataSource);
        em.setPackagesToScan("kai9.com.model"); // セカンダリのエンティティが存在するパッケージを指定
        em.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        return em;
    }

    @Bean(name = "comTransactionManager")
    public PlatformTransactionManager comTransactionManager(
            @Qualifier("comEntityManagerFactory")
            EntityManagerFactory comEntityManagerFactory) {
        return new JpaTransactionManager(comEntityManagerFactory);
    }
}
