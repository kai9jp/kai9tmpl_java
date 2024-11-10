package kai9.com.model;

import java.sql.Timestamp;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

import lombok.Data;

@Entity
@Table(name = "progress_status")
@Data
public class ProgressStatus {

    /**
     * ID
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    /**
     * プロセス名
     */
    @Column(name = "process_name")
    private String processName;

    /**
     * ステータス
     */
    @Column(name = "status")
    private String status;

    /**
     * 進捗1
     */
    @Column(name = "progress1")
    private Integer progress1;

    /**
     * 進捗2
     */
    @Column(name = "progress2")
    private Integer progress2;

    /**
     * 中止
     */
    @Column(name = "is_stop")
    private Boolean is_stop;

    /**
     * メッセージ
     */
    @Column(name = "message")
    private String message;

    /**
     * 更新者
     */
    @Column(name = "update_u_id")
    private Integer updateUId;

    /**
     * 更新日時
     */
    @Column(name = "update_date")
    private Timestamp updateDate;

    // コンストラクタ(空
    public ProgressStatus() {
    }
}
