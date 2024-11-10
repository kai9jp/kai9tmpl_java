package kai9.tmpl.database;

import javax.persistence.EntityManagerFactory;
import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;

import com.zaxxer.hikari.HikariDataSource;

import lombok.Getter;
import lombok.Setter;

//データソースを定義するクラス
//Springアプリケーションに複数のデータソースを定義するので、そのプライマリデータソースを指定
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "spring.datasource.primary")
@EnableJpaRepositories(basePackages = "kai9.tmpl.repository", entityManagerFactoryRef = "primaryEntityManagerFactory", transactionManagerRef = "primaryTransactionManager")
public class PrimaryDataSourceProperties {

    // データソースに必要なプロパティ
    private String driverClassName; // ドライバークラス名
    private String url; // データベースのURL
    private String username; // ユーザー名
    private String password; // パスワード
    private int maximumPoolSize; // HikariCPの最大プールサイズ
    private int minimumIdle; // HikariCPの最小アイドル接続数

    // プライマリデータソースを定義するためのメソッド
    @Bean
    @Primary // プライマリデータソースであることを示すアノテーション
    public javax.sql.DataSource primaryDataSource() {
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setDriverClassName(driverClassName);
        dataSource.setJdbcUrl(url);
        dataSource.setUsername(username);
        dataSource.setPassword(password);
        dataSource.setMaximumPoolSize(maximumPoolSize); // HikariCPの最大プールサイズを設定
        dataSource.setMinimumIdle(minimumIdle); // HikariCPの最小アイドル接続数を設定
        return dataSource;
    }

    // プライマリデータソースを使用するためのJdbcTemplateを作成するためのメソッド
    @Bean
    @Primary // プライマリデータソースであることを示すアノテーション
    public JdbcTemplate createJdbcTemplate(javax.sql.DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    // HibernateをJPAの実装として使用し、プライマリデータソースを基に、パッケージ内のエンティティクラスを管理するためのファクトリを提供
    @Bean(name = "primaryEntityManagerFactory")
    @Primary
    public LocalContainerEntityManagerFactoryBean primaryEntityManagerFactory(
            @Qualifier("primaryDataSource")
            DataSource dataSource) {
        LocalContainerEntityManagerFactoryBean em = new LocalContainerEntityManagerFactoryBean();
        em.setDataSource(dataSource);
        em.setPackagesToScan("kai9.tmpl.model");
        em.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        return em;
    }

    // JPAのトランザクション管理を実現するためのPlatformTransactionManagerを提供
    @Bean(name = "primaryTransactionManager")
    @Primary
    public PlatformTransactionManager primaryTransactionManager(
            EntityManagerFactory primaryEntityManagerFactory) {
        return new JpaTransactionManager(primaryEntityManagerFactory);
    }
}
