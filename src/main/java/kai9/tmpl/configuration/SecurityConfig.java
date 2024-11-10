package kai9.tmpl.configuration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

import kai9.tmpl.auth.filter.JwtAuthorizationFilter;
import kai9.tmpl.auth.filter.WebUsernamePasswordAuthenticationFilter;

@SuppressWarnings("deprecation")
@EnableWebSecurity
public class SecurityConfig {

    @Value("${jwt.secretKey}")
    private String secretKey;

    @Autowired
    UserDetailsService userDetailsService;

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    AuthenticationManager authenticationManager(
            final AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

        http.authorizeHttpRequests()
                .mvcMatchers("/test").permitAll()
                .mvcMatchers("/static/**").permitAll()
                .mvcMatchers("/").permitAll()
                // 全てのURLリクエストは認証されているユーザーしかアクセスできない
                .anyRequest().authenticated();

//    		.antMatchers("/api/test").permitAll() //利かない・・・

        // 独自フィルター作成
        WebUsernamePasswordAuthenticationFilter filter = new WebUsernamePasswordAuthenticationFilter(authenticationManager(http.getSharedObject(AuthenticationConfiguration.class)), secretKey);
        filter.setAuthenticationManager(authenticationManager(http.getSharedObject(AuthenticationConfiguration.class)));
        filter.setRequiresAuthenticationRequestMatcher(new AntPathRequestMatcher("/api/auth", "GET")); // ログインURL
        http.addFilter(filter);

        // 認可フィルター
        JwtAuthorizationFilter filter2 = new JwtAuthorizationFilter(authenticationManager(http.getSharedObject(AuthenticationConfiguration.class)), secretKey);
        http.addFilter(filter2)
                // Basic認証の対象となるパス https://codezine.jp/article/detail/11703
                .antMatcher("/api/**")
                .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS);

        // CORS設定
        // https://yukihane.github.io/blog/202101/24/spring-boot-cors-permit-all/
        http.cors(Customizer.withDefaults());
        // jwtを用いるのでcsrfは無効にしておく(jwtを使う事がcsrf対策なので)
        http.csrf().disable();

        return http.build();
    }

}