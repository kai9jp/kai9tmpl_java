package kai9.tmpl.model;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

import lombok.Data;

@Entity
@Table(name = "sql_a")
@Data
/**
 * モデル
 */
public class sql {
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

    /**
     * ID
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "sql_pk")
    private Integer sql_pk;

    /**
     * 更新回数
     */
    @Column(name = "modify_count")
    private Integer modify_count;

    /**
     * SQL名
     */
    @Column(name = "sql_name")
    private String sql_name;

    /**
     * SQL
     */
    @Column(name = "sql")
    private String sql;

    /**
     * 備考
     */
    @Column(name = "memo")
    private String memo;

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

    // 更新者:非DB項目
    private transient String update_user = "";

    public sql() {
        // コンストラクタ(空
    }

}
