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
import kai9.tmpl.dto.related_table_Request;
import kai9.tmpl.model.related_table;
import kai9.tmpl.repository.related_table_Repository;

/**
 * サービス
 */
@Service
public class related_table_Service {

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    @Qualifier("commonjdbc")
    JdbcTemplate jdbcTemplate_com;

    @Autowired
    private related_table_Repository related_table_rep;

    /**
     * 全検索
     */
    @Transactional(readOnly = true)
    public List<related_table> searchAll() {
        String sql = "select * from related_table_a order by related_pk";
        RowMapper<related_table> rowMapper = new BeanPropertyRowMapper<related_table>(related_table.class);
        return jdbcTemplate.query(sql, rowMapper);
    }

    /**
     * 新規登録
     */
    public related_table create(related_table_Request related_table_request) throws Exception {
        try {
            related_table related_table = new related_table();
            related_table.setModify_count(1);//新規登録は1固定
            related_table.setRelated_data(related_table_request.getRelated_data());
            related_table.setUpdate_date(new java.sql.Timestamp(new java.util.Date().getTime()));
            related_table.setDelflg(related_table_request.getDelflg());

            // 更新ユーザ取得
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String name = auth.getName();
            int user_id = getUserIDByLoginID(name);
            related_table.setUpdate_u_id(user_id);
            related_table = related_table_rep.save(related_table);

            // 履歴の登録:SQL実行
            String sql = "insert into related_table_b select * from related_table_a where related_pk = ?";
            jdbcTemplate.update(sql, related_table.getRelated_pk());

            return related_table;
        } catch (Exception e) {
            DBExceptionHandlerUtil.handleException(e);
            return null; // ↑で例外が再スローされるため、ここには到達しない
        }
    }

    /**
     * 更新
     */
    public related_table update(related_table_Request related_tableUpdateRequest) throws Exception {
        try {
            related_table related_table = findById(related_tableUpdateRequest.getRelated_pk());
            boolean IsChange = false;
            if (related_table.getRelated_pk() != related_tableUpdateRequest.getRelated_pk() ) IsChange = true;
            if (!related_table.getRelated_data().equals(related_tableUpdateRequest.getRelated_data()) ) IsChange = true;
            if (related_table.getDelflg() != related_tableUpdateRequest.getDelflg() ) IsChange = true;
            if (!IsChange) return related_table;

            related_table.setModify_count(related_tableUpdateRequest.getModify_count()+1);//更新回数+1
            related_table.setRelated_data(related_tableUpdateRequest.getRelated_data());
            related_table.setUpdate_date(new java.sql.Timestamp(new java.util.Date().getTime()));
            related_table.setDelflg(related_tableUpdateRequest.getDelflg());

            // 更新ユーザ取得
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String name = auth.getName();
            int user_id = getUserIDByLoginID(name);
            related_table.setUpdate_u_id(user_id);
            related_table = related_table_rep.save(related_table);

            // 履歴の登録:SQL実行
            String sql = "insert into related_table_b select * from related_table_a where related_pk = ?";
            jdbcTemplate.update(sql, related_table.getRelated_pk());

            return related_table;
        } catch (Exception e) {
            DBExceptionHandlerUtil.handleException(e);
            return null; // ↑で例外が再スローされるため、ここには到達しない
        }
    }

    /**
     * 削除
     */
    public related_table delete(related_table_Request related_tableUpdateRequest) {
        related_table related_table = findById(related_tableUpdateRequest.getRelated_pk());
        related_table.setDelflg(!related_tableUpdateRequest.getDelflg());
        related_table.setModify_count(related_tableUpdateRequest.getModify_count() + 1);// 更新回数+1

        // 更新ユーザ取得
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String name = auth.getName();
        int user_id = getUserIDByLoginID(name);
        related_table.setUpdate_u_id(user_id);
        related_table = related_table_rep.save(related_table);

        // 履歴の登録:SQL実行
        String sql = "insert into related_table_b select * from related_table_a where related_pk = ?";
        jdbcTemplate.update(sql, related_table.getRelated_pk());

        return related_table;
    }

    /**
     * 主キー検索
     */
    public related_table findById(Integer related_pk) {
        String sql = "select * from related_table_a where related_pk = ?";
        RowMapper<related_table> rowMapper = new BeanPropertyRowMapper<related_table>(related_table.class);
        related_table related_table = jdbcTemplate.queryForObject(sql, rowMapper, related_pk);
        return related_table;
    }

    /**
     * 物理削除
     */
    public void delete(Integer related_pk) {
        String sql = "select * from related_table_a where related_pk = ?";
        RowMapper<related_table> rowMapper = new BeanPropertyRowMapper<related_table>(related_table.class);
        related_table related_table = jdbcTemplate.queryForObject(sql, rowMapper, related_pk);
        related_table_rep.delete(related_table);
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
