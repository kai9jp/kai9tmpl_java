package kai9.com.service;

import java.sql.Timestamp;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import kai9.com.dto.ProgressStatus_Request;
import kai9.com.model.ProgressStatus;
import kai9.com.repository.ProgressStatus_Repository;

@Service
public class ProgressStatus_Service {

    // 使い方------------------------------------------------------
    // 1.init
    // 2.setMaxValue1
    // 3.setCurrentValue1
    // 4.setMaxValue2
    // 5.setCurrentValue2
    // 使い方------------------------------------------------------

    @Autowired
    private ProgressStatus_Repository progressStatus_Repository;

    // 前回のprogress記憶用
    private static Map<Integer, Integer> progress1MemoryMap = new HashMap<>();
    private static Map<Integer, Integer> progress2MemoryMap = new HashMap<>();

    // クラス変数
    private static Map<Integer, String> statusMap = new HashMap<>();
    private static Map<Integer, Integer> maxValue1Map = new HashMap<>();
    private static Map<Integer, Integer> currentValue1Map = new HashMap<>();
    private static Map<Integer, Integer> maxValue2Map = new HashMap<>();
    private static Map<Integer, Integer> currentValue2Map = new HashMap<>();

    // maxValue1を記憶する関数
    public void setStatus(int id, String status) {
        statusMap.put(id, status);
    }

    // maxValue1を記憶する関数
    public void setMaxValue1(int id, int value) {
        maxValue1Map.put(id, value);
    }

    // currentValue1を記憶する関数
    public void setCurrentValue1(int id, int value) {
        currentValue1Map.put(id, value);
        updateProgress1(id, statusMap.getOrDefault(id, ""), getProgress1(id), "");
    }

    // maxValue2を記憶する関数
    public void setMaxValue2(int id, int value) {
        maxValue2Map.put(id, value);
    }

    // currentValue2を記憶する関数
    public void setCurrentValue2(int id, int value) {
        currentValue2Map.put(id, value);
        updateProgress2(id, statusMap.getOrDefault(id, ""), getProgress2(id), "");
    }

    // 初期化する関数
    public void init(int id) {
        statusMap.put(id, "");
        maxValue1Map.put(id, 0);
        currentValue1Map.put(id, 0);
        maxValue2Map.put(id, 0);
        currentValue2Map.put(id, 0);
    }

    // progress1を算出する関数
    public int getProgress1(int id) {
        int maxValue1 = maxValue1Map.getOrDefault(id, 0);
        int currentValue1 = currentValue1Map.getOrDefault(id, 0);
        if (maxValue1 == 0) return 0;
        return (int) ((currentValue1 / (double) maxValue1) * 100);
    }

    // progress2を算出する関数
    public int getProgress2(int id) {
        int maxValue2 = maxValue2Map.getOrDefault(id, 0);
        int currentValue2 = currentValue2Map.getOrDefault(id, 0);
        if (maxValue2 == 0) return 0;
        return (int) ((currentValue2 / (double) maxValue2) * 100);
    }

    /**
     * 全検索
     * 
     * @return 検索結果
     */
    @Transactional(readOnly = true)
    public List<ProgressStatus> searchAll() {
        return progressStatus_Repository.findAll();
    }

    /**
     * 新規登録
     */
    public ProgressStatus create(ProgressStatus_Request progressStatus_Request) {
        ProgressStatus progressStatus = new ProgressStatus();
        // 【制御:開始】カラム:新規登録
        progressStatus.setProcessName(progressStatus_Request.getProcessName());
        progressStatus.setStatus(progressStatus_Request.getStatus());
        progressStatus.setProgress1(progressStatus_Request.getProgress1());
        progressStatus.setProgress2(progressStatus_Request.getProgress2());
        progressStatus.setIs_stop(progressStatus_Request.getIs_stop());
        progressStatus.setMessage(progressStatus_Request.getMessage());
        progressStatus.setUpdateUId(progressStatus_Request.getUpdateUId());
        progressStatus.setUpdateDate(new java.sql.Timestamp(new java.util.Date().getTime()));
        // 【制御:終了】カラム:新規登録

        return progressStatus_Repository.save(progressStatus);
    }

    /**
     * 更新
     */
    public ProgressStatus update(ProgressStatus_Request progressStatus_Request) {
        Optional<ProgressStatus> optionalProgressStatus = progressStatus_Repository.findById(progressStatus_Request.getId());
        if (optionalProgressStatus.isPresent()) {
            ProgressStatus progressStatus = optionalProgressStatus.get();
            boolean isChange = false;
            // 【制御:開始】カラム:変更判定
            if (!progressStatus.getProcessName().equals(progressStatus_Request.getProcessName())) isChange = true;
            if (!progressStatus.getStatus().equals(progressStatus_Request.getStatus())) isChange = true;
            if (!progressStatus.getProgress1().equals(progressStatus_Request.getProgress1())) isChange = true;
            if (!progressStatus.getProgress2().equals(progressStatus_Request.getProgress2())) isChange = true;
            if (progressStatus.getMessage() != null && !progressStatus.getMessage().equals(progressStatus_Request.getMessage())) isChange = true;
            if (progressStatus.getUpdateUId() != null && !progressStatus.getUpdateUId().equals(progressStatus_Request.getUpdateUId())) isChange = true;
            // 【制御:終了】カラム:変更判定
            if (!isChange) return progressStatus;

            // 【制御:開始】カラム:更新
            progressStatus.setProcessName(progressStatus_Request.getProcessName());
            progressStatus.setStatus(progressStatus_Request.getStatus());
            progressStatus.setProgress1(progressStatus_Request.getProgress1());
            progressStatus.setProgress2(progressStatus_Request.getProgress2());
            progressStatus.setIs_stop((progressStatus_Request.getIs_stop()));
            progressStatus.setMessage(progressStatus_Request.getMessage());
            progressStatus.setUpdateUId(progressStatus_Request.getUpdateUId());
            progressStatus.setUpdateDate(new java.sql.Timestamp(new java.util.Date().getTime()));
            // 【制御:終了】カラム:更新

            return progressStatus_Repository.save(progressStatus);
        } else {
            throw new IllegalArgumentException("Invalid progress status ID: " + progressStatus_Request.getId());
        }
    }

    /**
     * 削除
     */
    public void delete(Integer id) {
        progressStatus_Repository.deleteById(id);
    }

    /**
     * 主キー検索
     */
    public ProgressStatus findById(Integer id) {
        return progressStatus_Repository.findById(id).orElse(null);
    }

    // ラッパー関数
    public void updateProgress1(int id, String status, int progress1, String message) {
        updateProgress(id, status, progress1, progress2MemoryMap.getOrDefault(id, 0), message);
    }

    // ラッパー関数
    public void updateProgress2(int id, String status, int progress2, String message) {
        updateProgress(id, status, progress1MemoryMap.getOrDefault(id, 0), progress2, message);
    }

    public void updateProgress(int id, String status, int progress1, int progress2, String message) {
        Boolean isChange = false;
        if (progress1 == 0 && progress1MemoryMap.getOrDefault(id, 0) != 0) {
            // ゼロにリセットされた場合は処理
            isChange = true;
        } else if (progress1 != progress1MemoryMap.getOrDefault(id, 0)) {
            // 値が変わった場合は処理
            isChange = true;
        }
        if (progress2 == 0 && progress2MemoryMap.getOrDefault(id, 0) != 0) {
            // ゼロにリセットされた場合は処理
            isChange = true;
        } else if (progress2 != progress2MemoryMap.getOrDefault(id, 0)) {
            // 値が変わった場合は処理
            isChange = true;
        }

        // 値が変わっていない場合は処理しない
        if (!isChange) return;

        // 中止フラグがONの場合は中断
        ProgressStatus progressStatus = findById(id);
        if (progressStatus != null) {
            if (progressStatus.getIs_stop()) {
                throw new IllegalStateException("中止しました");
            }
        }

        // 記憶
        progress1MemoryMap.put(id, progress1);
        progress2MemoryMap.put(id, progress2);

        // DB更新
        Optional<ProgressStatus> optionalProgressStatus = progressStatus_Repository.findById(id);
        if (optionalProgressStatus.isPresent()) {
            progressStatus = optionalProgressStatus.get();
            progressStatus.setStatus(status);
            progressStatus.setProgress1(progress1);
            progressStatus.setProgress2(progress2);
            progressStatus.setIs_stop(false);
            progressStatus.setMessage(message);
            progressStatus.setUpdateDate(new Timestamp(System.currentTimeMillis()));
            progressStatus_Repository.save(progressStatus);
        }
    }
}
