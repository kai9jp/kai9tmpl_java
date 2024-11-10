package kai9.tmpl.model;

import javax.persistence.Column;
import javax.persistence.Entity;
//【制御:型】自動採番①
import javax.persistence.GeneratedValue;
//【制御:型】自動採番②
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
//【制御:型】relation
import javax.persistence.Transient;
//【制御:型】複合キー

import lombok.Data;

//【制御:クラス】複合キー

@Entity
@Table(name = "single_table_a")
@Data
//【制御:アノテーション】複合キー
/**
 * モデル
 */
public class single_table {
    /**
     * @Id：主キーに指定する。※複合キーの場合は@EmbeddedIdを使用
     * @GeneratedValue：主キーの指定をJPAに委ねる
     * @Column：name属性でマッピングするカラム名を指定する
     * @GenerationType.IDENTITY:自動採番
     */

    /**
     * プリミティブ型ではなくラッパークラスを用いている理由 の解説
     * プリミティブ型
     * → nullを扱えない
     * → int,boolean,long
     * ラッパークラス
     * → nullを扱える
     * → Integer,Boolean,Long
     */

    // 【制御:開始】カラム
    /**
     * シングルID
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "s_pk")
    private Integer s_pk;

    /**
     * 更新回数
     */
    @Column(name = "modify_count")
    private Integer modify_count;

    /**
     * ナチュラルキー1
     */
    @Column(name = "natural_key1")
    private String natural_key1;

    /**
     * ナチュラルキー2-1
     */
    @Column(name = "natural_key21")
    private String natural_key21;

    /**
     * ナチュラルキー2-2
     */
    @Column(name = "natural_key22_33")
    private String natural_key22_33;

    /**
     * ナチュラルキー3-1
     */
    @Column(name = "natural_key31")
    private String natural_key31;

    /**
     * ナチュラルキー3-2
     */
    @Column(name = "natural_key32")
    private String natural_key32;

    /**
     * 全角限定
     */
    @Column(name = "fullwidth_limited")
    private String fullwidth_limited;

    /**
     * 半角限定
     */
    @Column(name = "halfwidth_limited")
    private String halfwidth_limited;

    /**
     * 半角英字限定
     */
    @Column(name = "halfwidth_alphabetical_limited")
    private String halfwidth_alphabetical_limited;

    /**
     * 半角数字限定
     */
    @Column(name = "halfwidth_number_limited")
    private String halfwidth_number_limited;

    /**
     * 半角記号限定
     */
    @Column(name = "halfwidth_symbol_limited")
    private String halfwidth_symbol_limited;

    /**
     * 半角カナ限定
     */
    @Column(name = "halfwidth_kana_limited")
    private String halfwidth_kana_limited;

    /**
     * 全角カナ限定
     */
    @Column(name = "fullwidth_kana_limited")
    private String fullwidth_kana_limited;

    /**
     * 数値限定
     */
    @Column(name = "number_limited")
    private Integer number_limited;

    /**
     * 小数点
     */
    @Column(name = "small_number_point")
    private java.math.BigDecimal small_number_point;

    /**
     * 単精度浮動小数点数
     */
    @Column(name = "number_real")
    private Float number_real;

    /**
     * 倍精度浮動小数点数
     */
    @Column(name = "number_double")
    private Double number_double;

    /**
     * ノーマル文字列
     */
    @Column(name = "normal_string")
    private String normal_string;

    /**
     * 郵便番号
     */
    @Column(name = "postal_code")
    private String postal_code;

    /**
     * 電話番号
     */
    @Column(name = "phone_number")
    private String phone_number;

    /**
     * 日付
     */
    @Column(name = "date")
    private java.sql.Date date;

    /**
     * 日時
     */
    @Column(name = "datetime")
    private java.sql.Timestamp datetime;

    /**
     * メールアドレス
     */
    @Column(name = "email_address")
    private String email_address;

    /**
     * URL
     */
    @Column(name = "url")
    private String url;

    /**
     * フラグ
     */
    @Column(name = "flg")
    private Boolean flg;

    /**
     * 正規表現
     */
    @Column(name = "regexp")
    private String regexp;

    /**
     * 備考
     */
    @Column(name = "memo")
    private String memo;

    /**
     * 関連ID
     */
    @Column(name = "related_pk")
    private Integer related_pk;

    /**
     * 更新者
     */
    @Column(name = "update_u_id")
    private Integer update_u_id;

    /**
     * 更新日時
     */
    @Column(name = "update_date")
    private java.sql.Timestamp update_date;

    /**
     * 削除フラグ
     */
    @Column(name = "delflg")
    private Boolean delflg;
    // 【制御:終了】カラム

    // 更新者:非DB項目
    private transient String update_user = "";

    // 【制御:開始】relation
    // 非DBカラム(relation)関連ID+関連データ
    @Transient
    private String related_pk__related_data;
    // 【制御:終了】relation

    public single_table() {
        // コンストラクタ(空
    }

}
