package kai9.tmpl.dto;

import java.io.Serializable;
import java.util.Date;

import lombok.Data;

/**
 * 環境設定:リクエストデータ
 */
@SuppressWarnings("serial")
@Data
public class AppEnv_Request implements Serializable {

    /**
     * 更新回数
     */
    private Integer modify_count;

    /**
     * tmpフォルダ
     */
    private String dir_tmp;

    /**
     * [経過日数]tmpフォルダ削除
     */
    private Integer del_days_tmp;

    /**
     * 更新者
     */
    private Integer update_u_id;

    /**
     * 更新日時
     */
    private Date update_date;

}
