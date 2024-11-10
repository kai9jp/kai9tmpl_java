package kai9.tmpl.service;

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

import kai9.tmpl.database.DBExceptionHandlerUtil;
import kai9.tmpl.dto.sql_Request;
import kai9.tmpl.model.sql;
import kai9.tmpl.repository.sql_Repository;

/**
 * サービス
 */
@Service
public class sql_Service {

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    @Qualifier("commonjdbc")
    JdbcTemplate jdbcTemplate_com;

    @Autowired
    private sql_Repository sql_rep;

    /**
     * 全検索
     */
    @Transactional(readOnly = true)
    public List<sql> searchAll() {
        String sql = "select * from sql_a order by sql_pk";
        RowMapper<sql> rowMapper = new BeanPropertyRowMapper<sql>(sql.class);
        return jdbcTemplate.query(sql, rowMapper);
    }

    /**
     * 新規登録
     */
    public sql create(sql_Request sql_request) throws Exception {
        try {
            sql sql = new sql();
            sql.setModify_count(1);// 新規登録は1固定
            sql.setSql_name(sql_request.getSql_name());
            sql.setSql(sql_request.getSql());
            sql.setMemo(sql_request.getMemo());
            sql.setUpdate_date(new java.sql.Timestamp(new java.util.Date().getTime()));
            sql.setDelflg(sql_request.getDelflg());

            // 更新ユーザ取得
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String name = auth.getName();
            int user_id = getUserIDByLoginID(name);
            sql.setUpdate_u_id(user_id);
            sql = sql_rep.save(sql);

            // 履歴の登録:SQL実行
            String sql_text = "insert into sql_b select * from sql_a where sql_pk = ?";
            jdbcTemplate.update(sql_text, sql.getSql_pk());

            return sql;
        } catch (Exception e) {
            DBExceptionHandlerUtil.handleException(e);
            return null; // ↑で例外が再スローされるため、ここには到達しない
        }
    }

    /**
     * 更新
     */
    public sql update(sql_Request sqlUpdateRequest) throws Exception {
        try {
            sql sql = findById(sqlUpdateRequest.getSql_pk());
            boolean IsChange = false;
            if (sql.getSql_pk() != sqlUpdateRequest.getSql_pk()) IsChange = true;
            if (!sql.getSql_name().equals(sqlUpdateRequest.getSql_name())) IsChange = true;
            if (!sql.getSql().equals(sqlUpdateRequest.getSql())) IsChange = true;
            if (!sql.getMemo().equals(sqlUpdateRequest.getMemo())) IsChange = true;
            if (sql.getDelflg() != sqlUpdateRequest.getDelflg()) IsChange = true;
            if (!IsChange) return sql;

            sql.setModify_count(sqlUpdateRequest.getModify_count() + 1);// 更新回数+1
            sql.setSql_name(sqlUpdateRequest.getSql_name());
            sql.setSql(sqlUpdateRequest.getSql());
            sql.setMemo(sqlUpdateRequest.getMemo());
            sql.setUpdate_date(new java.sql.Timestamp(new java.util.Date().getTime()));
            sql.setDelflg(sqlUpdateRequest.getDelflg());

            // 更新ユーザ取得
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String name = auth.getName();
            int user_id = getUserIDByLoginID(name);
            sql.setUpdate_u_id(user_id);
            sql = sql_rep.save(sql);

            // 履歴の登録:SQL実行
            String sql_text = "insert into sql_b select * from sql_a where sql_pk = ?";
            jdbcTemplate.update(sql_text, sql.getSql_pk());

            return sql;
        } catch (Exception e) {
            DBExceptionHandlerUtil.handleException(e);
            return null; // ↑で例外が再スローされるため、ここには到達しない
        }
    }

    /**
     * 削除
     */
    public sql delete(sql_Request sqlUpdateRequest) {
        sql sql = findById(sqlUpdateRequest.getSql_pk());
        sql.setDelflg(!sqlUpdateRequest.getDelflg());
        sql.setModify_count(sqlUpdateRequest.getModify_count() + 1);// 更新回数+1

        // 更新ユーザ取得
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String name = auth.getName();
        int user_id = getUserIDByLoginID(name);
        sql.setUpdate_u_id(user_id);
        sql = sql_rep.save(sql);

        // 履歴の登録:SQL実行
        String sql_text = "insert into sql_b select * from sql_a where sql_pk = ?";
        jdbcTemplate.update(sql_text, sql.getSql_pk());

        return sql;
    }

    /**
     * 主キー検索
     */
    public sql findById(Integer sql_pk) {
        String sql_text = "select * from sql_a where sql_pk = ?";
        RowMapper<sql> rowMapper = new BeanPropertyRowMapper<sql>(sql.class);
        sql sql = jdbcTemplate.queryForObject(sql_text, rowMapper, sql_pk);
        return sql;
    }

    /**
     * 物理削除
     */
    public void delete(Integer sql_pk) {
        String sql_text = "select * from sql_a where sql_pk = ?";
        RowMapper<sql> rowMapper = new BeanPropertyRowMapper<sql>(sql.class);
        sql sql = jdbcTemplate.queryForObject(sql_text, rowMapper, sql_pk);
        sql_rep.delete(sql);
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
