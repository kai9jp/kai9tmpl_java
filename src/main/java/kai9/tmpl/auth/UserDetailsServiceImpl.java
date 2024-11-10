package kai9.tmpl.auth;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 認可、認証の制御
 */
@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    @Autowired
    @Qualifier("commonjdbc")
    JdbcTemplate jdbcTemplate;

    @Autowired
    @Lazy
    PasswordEncoder passwordEncoder;

    // DB認証を行うのでloadUserByUsernameをオーバーライド
    // ユーザマスタのインスタンスを返す
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        String sql = "SELECT * FROM m_user_a WHERE login_id = ?";
        Map<String, Object> map = jdbcTemplate.queryForMap(sql, username);// ヒットしない場合例外が発生しcatch区へ遷移する
        String password = (String) map.get("password");
        Collection<GrantedAuthority> authorities = new ArrayList<>();
        // ロールをGrantedAuthority用に変換し格納
        switch ((int) map.get("authority_lv")) {
        case 1:
            // 1:一般
            authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
            break;
        case 2:
            // 2:参照専用
            authorities.add(new SimpleGrantedAuthority("ROLE_READ_ONLY"));
            break;
        case 3:
            // 3:管理者
            authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
        }

        return new UserDetailsImpl(username, password, authorities);
    }
}
