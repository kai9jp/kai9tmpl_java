package kai9.com.dto;

import java.io.Serializable;
import java.sql.Timestamp;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

import lombok.Data;

/**
 * リクエストデータ
 */
@Data
@SuppressWarnings("serial")
public class ProgressStatus_Request implements Serializable {

    /**
     * ID
     */
    private Integer id;

    /**
     * プロセス名
     */
    @NotBlank(message = "Process name is required")
    private String processName;

    /**
     * ステータス
     */
    @NotBlank(message = "Status is required")
    private String status;

    /**
     * 進捗1
     */
    @NotNull(message = "Progress is required1")
    private Integer progress1;

    /**
     * 進捗2
     */
    @NotNull(message = "Progress is required2")
    private Integer progress2;

    /**
     * 中止
     */
    @NotNull(message = "Progress is is_stop")
    private Boolean is_stop;

    /**
     * メッセージ
     */
    private String message;

    /**
     * 更新者
     */
    private Integer updateUId;

    /**
     * 更新日時
     */
    private Timestamp updateDate;
}
