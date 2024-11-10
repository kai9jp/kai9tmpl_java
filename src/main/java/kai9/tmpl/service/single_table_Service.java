package kai9.tmpl.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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
import kai9.tmpl.dto.single_table_Request;
import kai9.tmpl.model.single_table;
import kai9.tmpl.repository.single_table_Repository;

/**
 * サービス
 */
@Service
public class single_table_Service {

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    @Qualifier("commonjdbc")
    JdbcTemplate jdbcTemplate_com;

    @Autowired
    private single_table_Repository single_table_rep;

    /**
     * 全検索
     */
    @Transactional(readOnly = true)
    public List<single_table> searchAll() {
        String sql = "select * from single_table_a order by s_pk";
        RowMapper<single_table> rowMapper = new BeanPropertyRowMapper<single_table>(single_table.class);
        return jdbcTemplate.query(sql, rowMapper);
    }

    /**
     * 新規登録
     */
    public single_table create(single_table_Request single_table_request) throws Exception {
        try {
            single_table single_table = new single_table();
            // 【制御:開始】カラム:新規登録
            single_table.setModify_count(1);// 新規登録は1固定
            single_table.setNatural_key1(single_table_request.getNatural_key1());
            single_table.setNatural_key21(single_table_request.getNatural_key21());
            single_table.setNatural_key22_33(single_table_request.getNatural_key22_33());
            single_table.setNatural_key31(single_table_request.getNatural_key31());
            single_table.setNatural_key32(single_table_request.getNatural_key32());
            single_table.setFullwidth_limited(single_table_request.getFullwidth_limited());
            single_table.setHalfwidth_limited(single_table_request.getHalfwidth_limited());
            single_table.setHalfwidth_alphabetical_limited(single_table_request.getHalfwidth_alphabetical_limited());
            single_table.setHalfwidth_number_limited(single_table_request.getHalfwidth_number_limited());
            single_table.setHalfwidth_symbol_limited(single_table_request.getHalfwidth_symbol_limited());
            single_table.setHalfwidth_kana_limited(single_table_request.getHalfwidth_kana_limited());
            single_table.setFullwidth_kana_limited(single_table_request.getFullwidth_kana_limited());
            single_table.setNumber_limited(single_table_request.getNumber_limited());
            single_table.setSmall_number_point(single_table_request.getSmall_number_point());
            single_table.setNumber_real(single_table_request.getNumber_real());
            single_table.setNumber_double(single_table_request.getNumber_double());
            single_table.setNormal_string(single_table_request.getNormal_string());
            single_table.setPostal_code(single_table_request.getPostal_code());
            single_table.setPhone_number(single_table_request.getPhone_number());
            single_table.setDate(single_table_request.getDate());
            single_table.setDatetime(single_table_request.getDatetime());
            single_table.setEmail_address(single_table_request.getEmail_address());
            single_table.setUrl(single_table_request.getUrl());
            single_table.setFlg(single_table_request.getFlg());
            single_table.setRegexp(single_table_request.getRegexp());
            single_table.setMemo(single_table_request.getMemo());
            single_table.setRelated_pk(single_table_request.getRelated_pk());
            single_table.setUpdate_date(new java.sql.Timestamp(new java.util.Date().getTime()));
            single_table.setDelflg(single_table_request.getDelflg());
            // 【制御:終了】カラム:新規登録

            // 更新ユーザ取得
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String name = auth.getName();
            int user_id = getUserIDByLoginID(name);
            single_table.setUpdate_u_id(user_id);
            single_table = single_table_rep.save(single_table);

            // 履歴の登録:SQL実行
            String sql = "insert into single_table_b select * from single_table_a where s_pk = ?";
            jdbcTemplate.update(sql, single_table.getS_pk());

            return single_table;
        } catch (Exception e) {
            DBExceptionHandlerUtil.handleException(e);
            return null; // ↑で例外が再スローされるため、ここには到達しない
        }
    }

    /**
     * 更新
     */
    public single_table update(single_table_Request single_tableUpdateRequest) throws Exception {
        try {
            single_table single_table = findById(single_tableUpdateRequest.getS_pk());
            boolean IsChange = false;
            // 【制御:開始】カラム:変更判定
            if (!Objects.equals(single_table.getS_pk(), single_tableUpdateRequest.getS_pk())) IsChange = true;
            if (!Objects.equals(single_table.getNatural_key1(), single_tableUpdateRequest.getNatural_key1())) IsChange = true;
            if (!Objects.equals(single_table.getNatural_key21(), single_tableUpdateRequest.getNatural_key21())) IsChange = true;
            if (!Objects.equals(single_table.getNatural_key22_33(), single_tableUpdateRequest.getNatural_key22_33())) IsChange = true;
            if (!Objects.equals(single_table.getNatural_key31(), single_tableUpdateRequest.getNatural_key31())) IsChange = true;
            if (!Objects.equals(single_table.getNatural_key32(), single_tableUpdateRequest.getNatural_key32())) IsChange = true;
            if (!Objects.equals(single_table.getFullwidth_limited(), single_tableUpdateRequest.getFullwidth_limited())) IsChange = true;
            if (!Objects.equals(single_table.getHalfwidth_limited(), single_tableUpdateRequest.getHalfwidth_limited())) IsChange = true;
            if (!Objects.equals(single_table.getHalfwidth_alphabetical_limited(), single_tableUpdateRequest.getHalfwidth_alphabetical_limited())) IsChange = true;
            if (!Objects.equals(single_table.getHalfwidth_number_limited(), single_tableUpdateRequest.getHalfwidth_number_limited())) IsChange = true;
            if (!Objects.equals(single_table.getHalfwidth_symbol_limited(), single_tableUpdateRequest.getHalfwidth_symbol_limited())) IsChange = true;
            if (!Objects.equals(single_table.getHalfwidth_kana_limited(), single_tableUpdateRequest.getHalfwidth_kana_limited())) IsChange = true;
            if (!Objects.equals(single_table.getFullwidth_kana_limited(), single_tableUpdateRequest.getFullwidth_kana_limited())) IsChange = true;
            if (!Objects.equals(single_table.getNumber_limited(), single_tableUpdateRequest.getNumber_limited())) IsChange = true;
            if (isBigDecimalChanged(single_table.getSmall_number_point(), single_tableUpdateRequest.getSmall_number_point())) IsChange = true;
            if (!Objects.equals(single_table.getNumber_real(), single_tableUpdateRequest.getNumber_real())) IsChange = true;
            if (!Objects.equals(single_table.getNumber_double(), single_tableUpdateRequest.getNumber_double())) IsChange = true;
            if (!Objects.equals(single_table.getNormal_string(), single_tableUpdateRequest.getNormal_string())) IsChange = true;
            if (!Objects.equals(single_table.getPostal_code(), single_tableUpdateRequest.getPostal_code())) IsChange = true;
            if (!Objects.equals(single_table.getPhone_number(), single_tableUpdateRequest.getPhone_number())) IsChange = true;
            if (!Objects.equals(single_table.getDate(), single_tableUpdateRequest.getDate())) IsChange = true;
            if (!Objects.equals(single_table.getDatetime(), single_tableUpdateRequest.getDatetime())) IsChange = true;
            if (!Objects.equals(single_table.getEmail_address(), single_tableUpdateRequest.getEmail_address())) IsChange = true;
            if (!Objects.equals(single_table.getUrl(), single_tableUpdateRequest.getUrl())) IsChange = true;
            if (!Objects.equals(single_table.getFlg(), single_tableUpdateRequest.getFlg())) IsChange = true;
            if (!Objects.equals(single_table.getRegexp(), single_tableUpdateRequest.getRegexp())) IsChange = true;
            if (!Objects.equals(single_table.getMemo(), single_tableUpdateRequest.getMemo())) IsChange = true;
            if (!Objects.equals(single_table.getRelated_pk(), single_tableUpdateRequest.getRelated_pk())) IsChange = true;
            if (!Objects.equals(single_table.getDelflg(), single_tableUpdateRequest.getDelflg())) IsChange = true;
            // 【制御:終了】カラム:変更判定
            if (!IsChange) return single_table;

            // 【制御:開始】カラム:更新
            single_table.setModify_count(single_tableUpdateRequest.getModify_count() + 1);// 更新回数+1
            single_table.setNatural_key1(single_tableUpdateRequest.getNatural_key1());
            single_table.setNatural_key21(single_tableUpdateRequest.getNatural_key21());
            single_table.setNatural_key22_33(single_tableUpdateRequest.getNatural_key22_33());
            single_table.setNatural_key31(single_tableUpdateRequest.getNatural_key31());
            single_table.setNatural_key32(single_tableUpdateRequest.getNatural_key32());
            single_table.setFullwidth_limited(single_tableUpdateRequest.getFullwidth_limited());
            single_table.setHalfwidth_limited(single_tableUpdateRequest.getHalfwidth_limited());
            single_table.setHalfwidth_alphabetical_limited(single_tableUpdateRequest.getHalfwidth_alphabetical_limited());
            single_table.setHalfwidth_number_limited(single_tableUpdateRequest.getHalfwidth_number_limited());
            single_table.setHalfwidth_symbol_limited(single_tableUpdateRequest.getHalfwidth_symbol_limited());
            single_table.setHalfwidth_kana_limited(single_tableUpdateRequest.getHalfwidth_kana_limited());
            single_table.setFullwidth_kana_limited(single_tableUpdateRequest.getFullwidth_kana_limited());
            single_table.setNumber_limited(single_tableUpdateRequest.getNumber_limited());
            single_table.setSmall_number_point(single_tableUpdateRequest.getSmall_number_point());
            single_table.setNumber_real(single_tableUpdateRequest.getNumber_real());
            single_table.setNumber_double(single_tableUpdateRequest.getNumber_double());
            single_table.setNormal_string(single_tableUpdateRequest.getNormal_string());
            single_table.setPostal_code(single_tableUpdateRequest.getPostal_code());
            single_table.setPhone_number(single_tableUpdateRequest.getPhone_number());
            single_table.setDate(single_tableUpdateRequest.getDate());
            single_table.setDatetime(single_tableUpdateRequest.getDatetime());
            single_table.setEmail_address(single_tableUpdateRequest.getEmail_address());
            single_table.setUrl(single_tableUpdateRequest.getUrl());
            single_table.setFlg(single_tableUpdateRequest.getFlg());
            single_table.setRegexp(single_tableUpdateRequest.getRegexp());
            single_table.setMemo(single_tableUpdateRequest.getMemo());
            single_table.setRelated_pk(single_tableUpdateRequest.getRelated_pk());
            single_table.setUpdate_date(new java.sql.Timestamp(new java.util.Date().getTime()));
            single_table.setDelflg(single_tableUpdateRequest.getDelflg());
            // 【制御:終了】カラム:更新

            // 更新ユーザ取得
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String name = auth.getName();
            int user_id = getUserIDByLoginID(name);
            single_table.setUpdate_u_id(user_id);
            single_table = single_table_rep.save(single_table);

            // 履歴の登録:SQL実行
            String sql = "insert into single_table_b select * from single_table_a where s_pk = ?";
            jdbcTemplate.update(sql, single_table.getS_pk());

            return single_table;
        } catch (Exception e) {
            DBExceptionHandlerUtil.handleException(e);
            return null; // ↑で例外が再スローされるため、ここには到達しない
        }
    }

    // 【制御:開始】isBigDecimalChanged
    public boolean isBigDecimalChanged(BigDecimal oldValue, BigDecimal newValue) {
        // どちらかがnullで、もう一方がnullでない場合は変更されたとみなす
        if (oldValue == null && newValue != null || oldValue != null && newValue == null) {
            return true;
        }
        // 両方がnullの場合は変更なし
        if (oldValue == null && newValue == null) {
            return false;
        }
        // 数値が異なる場合
        return oldValue.compareTo(newValue) != 0;
    }
    // 【制御:終了】isBigDecimalChanged

    /**
     * 削除
     */
    public single_table delete(single_table_Request single_tableUpdateRequest) {
        single_table single_table = findById(single_tableUpdateRequest.getS_pk());
        single_table.setDelflg(!single_tableUpdateRequest.getDelflg());
        single_table.setModify_count(single_tableUpdateRequest.getModify_count() + 1);// 更新回数+1

        // 更新ユーザ取得
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String name = auth.getName();
        int user_id = getUserIDByLoginID(name);
        single_table.setUpdate_u_id(user_id);
        single_table = single_table_rep.save(single_table);

        // 履歴の登録:SQL実行
        String sql = "insert into single_table_b select * from single_table_a where s_pk = ?";
        jdbcTemplate.update(sql, single_table.getS_pk());

        return single_table;
    }

    /**
     * 主キー検索
     */
    public single_table findById(Integer s_pk) {
        String sql = "select * from single_table_a where s_pk = ?";
        RowMapper<single_table> rowMapper = new BeanPropertyRowMapper<single_table>(single_table.class);
        single_table single_table = jdbcTemplate.queryForObject(sql, rowMapper, s_pk);
        return single_table;
    }

    /**
     * 物理削除
     */
    public void delete(Integer s_pk) {
        String sql = "select * from single_table_a where s_pk = ?";
        RowMapper<single_table> rowMapper = new BeanPropertyRowMapper<single_table>(single_table.class);
        single_table single_table = jdbcTemplate.queryForObject(sql, rowMapper, s_pk);
        single_table_rep.delete(single_table);
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
