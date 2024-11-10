package kai9.tmpl.dto;

import java.io.Serializable;

import lombok.Data;

/**
 * リクエストデータ
 */
@Data
@SuppressWarnings("serial")
public class related_table_Request implements Serializable {

	/**
	 *関連ID
	 */
    private Integer related_pk;

	/**
	 *更新回数
	 */
    private Integer modify_count;

	/**
	 *関連データ
	 */
    private String related_data;

	/**
	 *更新者
	 */
    private Integer update_u_id;

	/**
	 *更新日時
	 */
    private java.sql.Timestamp update_date;

	/**
	 *削除フラグ
	 */
    private Boolean delflg;


}
