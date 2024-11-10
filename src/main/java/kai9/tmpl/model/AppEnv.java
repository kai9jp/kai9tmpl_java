package kai9.tmpl.model;

import java.util.Date;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

import lombok.Data;

/**
 * 環境設定 :モデル
 */
@Entity
@Data
@Table(name = "app_env_a")
public class AppEnv {

    /**
     * 更新回数
     */
    @Id
    @Column(name = "modify_count")
    private Integer modify_count;

    /**
     * tmpフォルダ
     */
    @Column(name = "dir_tmp")
    private String dir_tmp;

    /**
     * [経過日数]tmpフォルダ削除
     */
    @Column(name = "del_days_tmp")
    private Integer del_days_tmp;

    /**
     * 更新者
     */
    @Column(name = "update_u_id")
    private Integer update_u_id;

    /**
     * 更新日時
     */
    @Column(name = "update_date")
    private Date update_date;

    public AppEnv() {
    }// コンストラクタ スタブ

}
