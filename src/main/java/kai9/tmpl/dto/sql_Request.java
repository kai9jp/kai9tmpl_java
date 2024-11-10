package kai9.tmpl.dto;

import java.io.Serializable;

import lombok.Data;

/**
 * リクエストデータ
 */
@Data
@SuppressWarnings("serial")
public class sql_Request implements Serializable {

    /**
     * ID
     */
    private Integer sql_pk;

    /**
     * 更新回数
     */
    private Integer modify_count;

    /**
     * SQL名
     */
    private String sql_name;

    /**
     * SQL
     */
    private String sql;

    /**
     * 備考
     */
    private String memo;

    /**
     * 更新者
     */
    private Integer update_u_id;

    /**
     * 更新日時
     */
    private java.sql.Timestamp update_date;

    /**
     * 削除フラグ
     */
    private Boolean delflg;

}
