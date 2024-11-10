package kai9.tmpl.service;

import java.util.Date;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import kai9.tmpl.dto.AppEnv_Request;
import kai9.tmpl.model.AppEnv;
import kai9.tmpl.repository.AppEnv_Repository;

/**
 * 環境設定:サービス
 */
@Service
public class AppEnv_Service {

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    @Qualifier("commonjdbc")
    JdbcTemplate jdbcTemplate_com;

    @Autowired
    private AppEnv_Repository app_env_rep;

    /**
     * 全検索
     */
    @Transactional(readOnly = true)
    public List<AppEnv> searchAll() {
        String sql = "select * from app_env_a order by ";
        RowMapper<AppEnv> rowMapper = new BeanPropertyRowMapper<AppEnv>(AppEnv.class);
        return jdbcTemplate.query(sql, rowMapper);
    }

    /**
     * 新規登録、更新
     */
    public AppEnv create(AppEnv_Request app_env_request) throws CloneNotSupportedException {
        AppEnv app_env = new AppEnv();
        if (app_env_request.getModify_count() == 0) {
            app_env.setModify_count(1);// 新規登録は1固定
        } else {
            boolean IsChange = false;
            app_env = findById();
            if (!app_env.getDir_tmp().equals(app_env_request.getDir_tmp())) IsChange = true;
            if (!app_env.getDel_days_tmp().equals(app_env_request.getDel_days_tmp())) IsChange = true;
            // 変更が無い場合は何もしない
            if (!IsChange) return app_env;

            // 更新回数+1
            app_env.setModify_count(app_env_request.getModify_count() + 1);
        }
        app_env.setDir_tmp(app_env_request.getDir_tmp());
        app_env.setDel_days_tmp(app_env_request.getDel_days_tmp());
        app_env.setUpdate_date(new Date());
        // 更新ユーザ取得
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String name = auth.getName();
        int user_id = getUserIDByLoginID(name);
        app_env.setUpdate_u_id(user_id);

        // delete & insert
        jdbcTemplate.update("DELETE FROM app_env_a");

        app_env = app_env_rep.save(app_env);

        // 履歴の登録:SQL実行
        String sql = "insert into app_env_b select * from app_env_a ";
        jdbcTemplate.update(sql);

        return app_env;
    }

    /**
     * 主キー検索
     */
    public AppEnv findById() {
        String sql = "select * from app_env_a";
        RowMapper<AppEnv> rowMapper = new BeanPropertyRowMapper<AppEnv>(AppEnv.class);
        AppEnv app_env = jdbcTemplate.queryForObject(sql, rowMapper);
        return app_env;
    }

    /**
     * ログインIDからユーザIDを取得
     */
    public int getUserIDByLoginID(String login_id) {
        String sql = "select * from m_user_a where login_id = ?";
        Map<String, Object> map = jdbcTemplate_com.queryForMap(sql, login_id);
        int user_id = (int) map.get("user_id");
        return user_id;
    }

}
