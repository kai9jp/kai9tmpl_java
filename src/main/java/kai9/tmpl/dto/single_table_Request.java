package kai9.tmpl.dto;

import java.io.Serializable;

import lombok.Data;
//【制御:型】BLOB

/**
 * リクエストデータ
 */
@Data
@SuppressWarnings("serial")
public class single_table_Request implements Serializable {

    // 【制御:開始】カラム
    /**
     * シングルID
     */
    private Integer s_pk;

    /**
     * 更新回数
     */
    private Integer modify_count;

    /**
     * ナチュラルキー1
     */
    private String natural_key1;

    /**
     * ナチュラルキー2-1
     */
    private String natural_key21;

    /**
     * ナチュラルキー2-2
     */
    private String natural_key22_33;

    /**
     * ナチュラルキー3-1
     */
    private String natural_key31;

    /**
     * ナチュラルキー3-2
     */
    private String natural_key32;

    /**
     * 全角限定
     */
    private String fullwidth_limited;

    /**
     * 半角限定
     */
    private String halfwidth_limited;

    /**
     * 半角英字限定
     */
    private String halfwidth_alphabetical_limited;

    /**
     * 半角数字限定
     */
    private String halfwidth_number_limited;

    /**
     * 半角記号限定
     */
    private String halfwidth_symbol_limited;

    /**
     * 半角カナ限定
     */
    private String halfwidth_kana_limited;

    /**
     * 全角カナ限定
     */
    private String fullwidth_kana_limited;

    /**
     * 数値限定
     */
    private Integer number_limited;

    /**
     * 小数点
     */
    private java.math.BigDecimal small_number_point;

    /**
     * 単精度浮動小数点数
     */
    private Float number_real;

    /**
     * 倍精度浮動小数点数
     */
    private Double number_double;

    /**
     * ノーマル文字列
     */
    private String normal_string;

    /**
     * 郵便番号
     */
    private String postal_code;

    /**
     * 電話番号
     */
    private String phone_number;

    /**
     * 日付
     */
    private java.sql.Date date;

    /**
     * 日時
     */
    private java.sql.Timestamp datetime;

    /**
     * メールアドレス
     */
    private String email_address;

    /**
     * URL
     */
    private String url;

    /**
     * フラグ
     */
    private Boolean flg;

    /**
     * 正規表現
     */
    private String regexp;

    /**
     * 備考
     */
    private String memo;

    /**
     * 関連ID
     */
    private Integer related_pk;

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
    // 【制御:終了】カラム

    // 【制御:開始】relation
    /**
     * (relation)関連ID+関連データ
     */
    private String related_pk__related_data;
    // 【制御:終了】relation

}
