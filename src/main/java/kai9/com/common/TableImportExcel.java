package kai9.com.common;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import kai9.com.service.ProgressStatus_Service;
import kai9.libs.DateParserUtil;
import kai9.libs.PoiUtil;

@Component
public class TableImportExcel {

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    @Qualifier("commonjdbc")
    JdbcTemplate jdbcTemplate_com;

    @Autowired
    private ProgressStatus_Service progressStatus_Service;

    // デバッグ用に現新のデータ内容を出力するか
    private boolean isDebug = true;

    // 件数格納用の変数(スレッドセーフ)
    private ThreadLocal<Integer> progressStatusId = ThreadLocal.withInitial(() -> 0);
    private ThreadLocal<Integer> importCount = ThreadLocal.withInitial(() -> 0);
    private ThreadLocal<Integer> existCount = ThreadLocal.withInitial(() -> 0);
    private ThreadLocal<Integer> newCount = ThreadLocal.withInitial(() -> 0);
    private ThreadLocal<Integer> modifyCount = ThreadLocal.withInitial(() -> 0);
    private ThreadLocal<Integer> noChangeCount = ThreadLocal.withInitial(() -> 0);

    // ハッシュ値格納用(スレッドセーフ)
    private ThreadLocal<Map<String, String>> existingHashes = ThreadLocal.withInitial(HashMap::new);
    private ThreadLocal<Map<String, Integer>> existingModifyCounts = ThreadLocal.withInitial(HashMap::new);

    // エラー情報格納用(スレッドセーフ)
    private ThreadLocal<Map<Integer, List<String>>> errorLogs = ThreadLocal.withInitial(HashMap::new);

    public void logException(int key, String message) {
        // 既存のキーがある場合、そのリストに追加
        if (errorLogs.get().containsKey(key)) {
            errorLogs.get().get(key).add(message);
        } else {
            // 新しいキーの場合、新しいリストを作成して追加
            List<String> messages = new ArrayList<>();
            messages.add(message);
            errorLogs.get().put(key, messages);
        }
    }

    public String getExceptionMessages(int key) {
        List<String> messages = errorLogs.get().getOrDefault(key, new ArrayList<>());
        // 各メッセージを改行(CRLF)で結合し返す
        return String.join("\r\n", messages);
    }

    /**
     * データ取込
     * 
     * 第1引数：テーブル名 第2引数：エクセルのパス 第3引数：シート名。省略時、第1引数のテーブル名をシート名として扱う
     * ※自動でBテーブルのデータも削除される
     * 
     * @throws Exception
     */
    public String exec(String tableName, String FileName, int progress_status_id,StringBuilder relationsSB) throws Exception {
        String crlf = System.lineSeparator(); // 改行コード
        try {
            // 各クラス変数を初期化
            progressStatusId.set(progress_status_id);
            importCount.set(0);
            existCount.set(0);
            newCount.set(0);
            modifyCount.set(0);
            noChangeCount.set(0);
            existingHashes.get().clear();
            existingModifyCounts.get().clear();
            errorLogs.get().clear();

            // 進捗管理
            progressStatus_Service.init(progress_status_id);
            // 内訳=エクセル読み込み、DB新規登録、DB更新登録、DB新規登録(履歴)、DB更新登録(履歴)、エクセル書込み、既存データ検索の7箇所分
            progressStatus_Service.setMaxValue1(progress_status_id, 7 + 1);

            if (isDebug) {
                // デバック用(新旧レコード比較)
                String tempDir = System.getenv("TEMP");
                Files.write(Paths.get(tempDir, "new.txt"), Collections.singleton(""), StandardOpenOption.CREATE);
                Files.write(Paths.get(tempDir, "Exist.txt"), Collections.singleton(""), StandardOpenOption.CREATE);
            }

            // 実行
            String sheetName = tableName.length() > 31 ? "データ" : tableName;// シート名上限の31文字を超える場合は一律「データ」という名前のシートで扱う
            bulkInsertFromExcel(FileName, tableName, sheetName,relationsSB);
            // 結果を返す
            return "新規:" + newCount.get() + "件" + crlf + "更新:" + modifyCount.get() + "件" + crlf + "変更無し:" + noChangeCount.get() + "件" + crlf + "エラー:" + errorLogs.get().size() + "件";
        } catch (Exception e) {
            throw e;
        }
    }
    // ラッパー(relationsSB無し)
    public String exec(String tableName, String FileName, int progress_status_id) throws Exception {
        StringBuilder relationsSB = new StringBuilder();
        return exec(tableName, FileName, progress_status_id,relationsSB);
    }


    /**
     * エクセルファイルからテーブルにバルクインサートを行う
     * 
     * @param excelFilePath エクセルファイルのパス
     * @param tableName テーブル名
     * @param SheetName シート名
     * @return 処理したレコード数
     * @throws Exception
     */
    public Integer bulkInsertFromExcel(String excelFilePath, String tableName, String SheetName,StringBuilder relationsSB) throws Exception {
        // 列名と型情報を取得する
        Map<String, String> columnTypes = getColumnTypes(excelFilePath, SheetName, tableName);

        // エクセルからレコード情報を取得する
        List<Map<String, Object>> records = getRecords(excelFilePath, SheetName, columnTypes,relationsSB);

        // 主キーを取得する
        String primaryKey = getPrimaryKey(tableName);

        // 既存のハッシュ値を取得する
        getExistingHashes(tableName, primaryKey, columnTypes);

        // INSERT文の生成
        String insertSql = "INSERT INTO " + tableName + " (";
        insertSql += String.join(", ", columnTypes.keySet());
        insertSql += ") VALUES (";
        insertSql += String.join(", ", Collections.nCopies(columnTypes.size(), "?"));
        insertSql += ")";

        // INSERT文の生成(履歴用)
        String tableName_b = tableName.endsWith("_a") ? tableName.substring(0, tableName.length() - 2) + "_b" : tableName.toLowerCase();
        String insertSql_b = "INSERT INTO " + tableName_b + " (";
        insertSql_b += String.join(", ", columnTypes.keySet());
        insertSql_b += ") VALUES (";
        insertSql_b += String.join(", ", Collections.nCopies(columnTypes.size(), "?"));
        insertSql_b += ")";

        // UPDATE文の生成
        String updateSql = "UPDATE " + tableName + " SET ";
        updateSql += columnTypes.keySet().stream().map(column -> column + " = ?").collect(Collectors.joining(", "));
        updateSql += " WHERE " + primaryKey + " = CAST(? AS " + getColumnType(columnTypes, primaryKey) + ")";

        // レコードを新規挿入と更新に分ける
        List<Map<String, Object>> newRecords = new ArrayList<>();
        List<Map<String, Object>> updateRecords = new ArrayList<>();
        // 状態管理用リスト
        List<String> recordStatuses = new ArrayList<>();

        // ログインID取得
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String name = auth.getName();
        String sql = "select * from m_user_a where login_id = ?";
        Map<String, Object> map = jdbcTemplate_com.queryForMap(sql, name);
        int user_id = (int) map.get("user_id");

        int index = 0; // カウンター変数
        for (Map<String, Object> record : records) {
            String primaryKeyValue = record.get(primaryKey).toString();
            String newHash = calculateHash(record);
            String oldHash = existingHashes.get().get(primaryKeyValue);

            if (!newHash.equals(oldHash)) {
                if (isDebug) {
                    // デバック用(新旧レコード比較)
                    String tempDir = System.getenv("TEMP");
                    Files.write(Paths.get(tempDir, "new.txt"), Collections.singleton(record.toString()), StandardOpenOption.APPEND);
                }
                record.put("hash_value", newHash);
                if (existingHashes.get().containsKey(primaryKeyValue)) {
                    // 更新
                    updateRecords.add(record);
                    recordStatuses.add("更新");
                } else {
                    // 新規
                    newRecords.add(record);
                    recordStatuses.add("新規");
                }

                Boolean isSuccess = true;

                // 更新回数を変更する
                try {
                    Object modifyCountObj = record.get("modify_count");
                    if (modifyCountObj != null && (modifyCountObj instanceof Number)) {
                        // modifyCountObjが存在し、且つ、数値型の場合だけ処理
                        int modify_count = ((Number) modifyCountObj).intValue();
                        if (existingHashes.get().containsKey(primaryKeyValue)) {
                            // 更新
                            // 更新前後のレコードで、更新回数が異なる場合、排他制御エラーとする
                            int currentModifyCount = existingModifyCounts.get().get(primaryKeyValue);
                            if (currentModifyCount != modify_count) {
                                // ログ記憶
                                logException(index, "排他エラー：更新回数が異なります。別で更新されているためスキップしました。");
                                // 変更対象から除外
                                updateRecords.remove(updateRecords.size() - 1);
                                recordStatuses.set(recordStatuses.size() - 1, "更新失敗");
                                isSuccess = false;
                            } else {
                                modify_count++;
                            }
                        } else {
                            // 新規
                            modify_count = 1;
                        }
                        record.put("modify_count", modify_count);
                    }
                } catch (Exception e) {
                    // エクセルに格納する際、文字が多いと入らないので100文字に削る
                    String shortMessage = (e.getMessage() != null && e.getMessage().length() > 100) ? e.getMessage().substring(0, 100) : e.getMessage();
                    // ログ記憶
                    logException(index, "modify_count:更新失敗" + shortMessage);
                    continue;
                }

                // 更新日時を変更する
                try {
                    Object update_date = new Timestamp(System.currentTimeMillis());
                    record.put("update_date", update_date);
                } catch (Exception e) {
                    // エクセルに格納する際、文字が多いと入らないので100文字に削る
                    String shortMessage = (e.getMessage() != null && e.getMessage().length() > 100) ? e.getMessage().substring(0, 100) : e.getMessage();
                    // ログ記憶
                    logException(index, "update_date:更新失敗" + shortMessage);
                    continue;
                }

                // 更新者を変更する
                try {
                    Object updateUIdObj = record.get("update_u_id");
                    if (updateUIdObj != null && updateUIdObj instanceof Integer) {
                        // updateUIdObjが存在し、且つ、Integer型の場合だけ処理
                        record.put("update_u_id", user_id);
                    }
                } catch (Exception e) {
                    // エクセルに格納する際、文字が多いと入らないので100文字に削る
                    String shortMessage = (e.getMessage() != null && e.getMessage().length() > 100) ? e.getMessage().substring(0, 100) : e.getMessage();
                    // ログ記憶
                    logException(index, "update_u_id:更新失敗" + shortMessage);
                    continue;
                }

                if (isSuccess) {
                    if (existingHashes.get().containsKey(primaryKeyValue)) {
                        // 更新件数を加算
                        modifyCount.set(modifyCount.get() + 1);
                    } else {
                        // 新規件数を加算
                        newCount.set(newCount.get() + 1);
                    }
                }
            } else {
                recordStatuses.add("変更無し");
                // 変更無し件数を加算
                noChangeCount.set(noChangeCount.get() + 1);
            }

            index++;
        }

        // 進捗管理
        progressStatus_Service.setCurrentValue1(progressStatusId.get(), 3);
        progressStatus_Service.setMaxValue2(progressStatusId.get(), newRecords.size());
        progressStatus_Service.setCurrentValue2(progressStatusId.get(), 0);
        // 新規挿入のバッチ処理
        if (!newRecords.isEmpty()) {
            executeBatchUpdate(insertSql, newRecords, columnTypes, primaryKey, records.size());
        }

        // 進捗管理
        progressStatus_Service.setCurrentValue1(progressStatusId.get(), 4);
        progressStatus_Service.setMaxValue2(progressStatusId.get(), updateRecords.size());
        progressStatus_Service.setCurrentValue2(progressStatusId.get(), 0);
        // 更新のバッチ処理
        if (!updateRecords.isEmpty()) {
            executeBatchUpdate(updateSql, updateRecords, columnTypes, primaryKey, records.size());
        }

        // 進捗管理
        progressStatus_Service.setCurrentValue1(progressStatusId.get(), 5);
        progressStatus_Service.setMaxValue2(progressStatusId.get(), updateRecords.size() + newRecords.size());
        progressStatus_Service.setCurrentValue2(progressStatusId.get(), 0);
        // 履歴のバッチ処理
        if (!newRecords.isEmpty()) {
            executeBatchUpdate(insertSql_b, newRecords, columnTypes, primaryKey, records.size());
        }
        // 進捗管理
        progressStatus_Service.setCurrentValue1(progressStatusId.get(), 6);
        progressStatus_Service.setMaxValue2(progressStatusId.get(), updateRecords.size() + newRecords.size());
        progressStatus_Service.setCurrentValue2(progressStatusId.get(), 0);
        if (!updateRecords.isEmpty()) {
            executeBatchUpdate(insertSql_b, updateRecords, columnTypes, primaryKey, records.size());
        }

        // 進捗管理
        progressStatus_Service.setCurrentValue1(progressStatusId.get(), 7);
        progressStatus_Service.setMaxValue2(progressStatusId.get(), importCount.get());
        progressStatus_Service.setCurrentValue2(progressStatusId.get(), 0);
        // エクセルに情報を書き戻す
        writeBackToExcel(excelFilePath, SheetName, records, recordStatuses);

        // シリアル型が採用されている場合、そのシリアル値を現在登録済のレコードに合わせて更新する
        sql = "SELECT attname FROM pg_catalog.pg_attribute WHERE attrelid = ?::regclass AND attnum > 0 AND attisdropped = false";
        List<String> columnNames = jdbcTemplate.query(sql, (rs, rowNum) -> rs.getString("attname"), tableName);
        String sequenceName = null;
        Long generatedId = (long) 0;
        for (String columnName : columnNames) {
            String sequenceNameSql = String.format("SELECT pg_get_serial_sequence('%s', '%s')", tableName, columnName);
            sequenceName = jdbcTemplate.queryForObject(sequenceNameSql, String.class);
            if (sequenceName != null) {
                String maxIdSql = "SELECT MAX(" + columnName + ") FROM " + tableName;
                generatedId = Optional.ofNullable(jdbcTemplate.queryForObject(maxIdSql, Long.class)).orElse(0L);
                break;
            }
        }
        if (sequenceName != null) {
            String setValSql = "SELECT setval('" + sequenceName + "', " + (generatedId + 1) + ", true)";
            jdbcTemplate.execute(setValSql);
        }

        // 進捗管理(100%で表示)
        progressStatus_Service.updateProgress1(progressStatusId.get(), "完了", 100, "完了");
        progressStatus_Service.updateProgress2(progressStatusId.get(), "完了", 100, "完了");
        Thread.sleep(1000);

        return records.size();
    }

    /**
     * 既存全レコードを取得し、ハッシュ値を計算
     * 
     * @param tableName テーブル名
     * @param primaryKey 主キー
     * @param columnTypes 列名と型情報のマップ
     * @return レコードのハッシュ値のマップ
     * @throws SQLException
     * @throws NoSuchAlgorithmException
     */
    public void getExistingHashes(String tableName, String primaryKey, Map<String, String> columnTypes)
            throws SQLException, NoSuchAlgorithmException {
        // 既存レコード件数を取得
        String sql = String.format("SELECT COUNT(*) FROM %s", tableName);
        existCount.set(jdbcTemplate.queryForObject(sql, Integer.class));

        // 進捗管理
        progressStatus_Service.setCurrentValue1(progressStatusId.get(), 2);
        progressStatus_Service.setMaxValue2(progressStatusId.get(), existCount.get());
        progressStatus_Service.setCurrentValue2(progressStatusId.get(), 0);

        // 各レコードを検索しハッシュ値を算出
        AtomicInteger index = new AtomicInteger(0);
        sql = String.format("SELECT * FROM %s", tableName);
        jdbcTemplate.query(sql, (rs) -> {
            // 進捗管理
            progressStatus_Service.setCurrentValue2(progressStatusId.get(), index.get());

            Map<String, Object> record = new LinkedHashMap<>();
            int columnCount = rs.getMetaData().getColumnCount();
            for (int i = 1; i <= columnCount; i++) {
                String columnName = rs.getMetaData().getColumnName(i);
                record.put(columnName, rs.getObject(i));
            }

            String primaryKeyValue = record.get(primaryKey).toString();
            // 型に基づいて各カラムの値を成形する
            for (String columnName : record.keySet()) {
                String columnType = columnTypes.get(columnName);
                Object value = record.get(columnName);
                Object value2 = formatValueByType(columnType, value);
                record.put(columnName, value2);
            }
            if (isDebug) {
                // デバック用(新旧レコード比較)
                try {
                    String tempDir = System.getenv("TEMP");
                    Files.write(Paths.get(tempDir, "Exist.txt"), Collections.singleton(record.toString()), StandardOpenOption.APPEND);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            String hash;
            try {
                hash = calculateHash(record);
            } catch (NoSuchAlgorithmException e) {
                throw new RuntimeException(e); // ラップして再スロー(ramda式内なので、これを入れないとスローされない)
            }

            int modifyCount = (int) record.get("modify_count");

            existingHashes.get().put(primaryKeyValue, hash);
            existingModifyCounts.get().put(primaryKeyValue, modifyCount);

            index.incrementAndGet();
        });
    }

    /**
     * エクセルからレコードを読み込む
     * 
     * @param excelFilePath エクセルファイルのパス
     * @param SheetName シート名
     * @param columnTypes 列名と型情報のマップ
     * @param relationsSB 関連データの定義を持つStringBuilder
     * @return レコード情報のリスト
     * @throws IOException
     */
    public List<Map<String, Object>> getRecords(String excelFilePath, String SheetName, Map<String, String> columnTypes, StringBuilder relationsSB)
            throws IOException {
        List<Map<String, Object>> records = new ArrayList<>(); // レコード情報を保持するList

        // Excelファイルを読み込む
        Path path = Paths.get(excelFilePath);
        InputStream inputStream = Files.newInputStream(path);
        Workbook workbook = new XSSFWorkbook(inputStream);

        // 対象シートを取得する
        Sheet sheet = workbook.getSheet(SheetName);

        // データの開始列番号を取得する
        Integer Col2 = PoiUtil.findCol(sheet, "#C2#");// エラーチェックは割愛(getColumnTypes側で実施)
        // 列名が記載された行番号を取得する
        Integer Row2 = PoiUtil.findRow(sheet, "#R2#");// エラーチェックは割愛(getColumnTypes側で実施)
        // 開始行番号を取得する
        Integer Row6 = PoiUtil.findRow(sheet, "#R6#");
        if (Row6 == -1) {
            workbook.close();
            throw new RuntimeException("制御文字「#R6#」がエクセルに発見できませんでした:シート名[" + SheetName + "]'");
        }

        importCount.set(sheet.getLastRowNum());
        // 進捗管理
        progressStatus_Service.setCurrentValue1(progressStatusId.get(), 1);
        progressStatus_Service.setMaxValue2(progressStatusId.get(), importCount.get() - Row6);
        progressStatus_Service.setCurrentValue2(progressStatusId.get(), 0);

        // relationsSBの内容をマップに変換する
        Map<String, String> relationsMap = Arrays.stream(relationsSB.toString().split(","))
                .map(line -> line.split("=", 2))
                .filter(parts -> parts.length == 2)
                .collect(Collectors.toMap(parts -> parts[0].trim(), parts -> parts[1].trim()));

        // 7行目以降からレコード情報を取得する
        for (int i = Row6; i <= importCount.get(); i++) {
            // 進捗管理
            progressStatus_Service.setCurrentValue2(progressStatusId.get(), i - Row6);

            Row row = sheet.getRow(i);
            if (row == null) {
                break;
            }
            Map<String, Object> record = new LinkedHashMap<>();
            int maxCol = row.getLastCellNum();
            for (int j = Col2; j < maxCol; j++) {
                Cell cell = row.getCell(j);
                if (cell == null) {
                    continue;
                }
                String columnName = sheet.getRow(Row2).getCell(j).getStringCellValue().trim();
                if (columnName.isEmpty())
                    continue;// 列情報が無い場合は無視(#システム情報# 等)
                String columnType = columnTypes.get(columnName);
                Object value = null;
                switch (cell.getCellType()) {
                case BLANK:
                    if (columnType.equals("String")) {
                        // kai9は各serviceでnullを扱えない仕様なので
                        value = "";
                    } else {
                        // string以外もnullは許容しないのだが、事前の登録前チェックで弾くので、ここではnull
                        value = null;
                    }
                    break;
                case BOOLEAN:
                    value = cell.getBooleanCellValue();
                    break;
                case NUMERIC:
                    if (DateUtil.isCellDateFormatted(cell)) {
                        value = cell.getDateCellValue();
                    } else {
                        if (columnType.equals("short")) {
                            value = (short) cell.getNumericCellValue();
                        } else if (columnType.equals("Integer")) {
                            value = (int) cell.getNumericCellValue();
                        } else if (columnType.equals("long")) {
                            value = (long) cell.getNumericCellValue();
                        } else if (columnType.equals("float")) {
                            value = (float) cell.getNumericCellValue();
                        } else if (columnType.equals("java.math.BigDecimal")) {
                            value = new BigDecimal(cell.getNumericCellValue());
                        } else if (columnType.equals("date") || columnType.equals("timestamp") || columnType.equals("timestamp without time zone") || columnType.equals("timestamp with time zone")) {
                            double numericValue = cell.getNumericCellValue();
                            value = DateUtil.getJavaDate(numericValue);
                        }
                    }
                    break;
                case STRING:
                    value = cell.getStringCellValue();
                    break;
                case FORMULA:
                    switch (cell.getCachedFormulaResultType()) {
                    case BOOLEAN:
                        value = cell.getBooleanCellValue();
                        break;
                    case NUMERIC:
                        if (DateUtil.isCellDateFormatted(cell)) {
                            value = cell.getDateCellValue();
                        } else {
                            if (columnType.equals("short")) {
                                value = (short) cell.getNumericCellValue();
                            } else if (columnType.equals("Integer")) {
                                value = (int) cell.getNumericCellValue();
                            } else if (columnType.equals("long")) {
                                value = (long) cell.getNumericCellValue();
                            } else if (columnType.equals("float")) {
                                value = (float) cell.getNumericCellValue();
                            } else if (columnType.equals("java.math.BigDecimal")) {
                                value = new BigDecimal(cell.getNumericCellValue());
                            }
                        }
                        break;
                    case STRING:
                        value = cell.getStringCellValue();
                        break;
                    default:
                        value = null;
                    }
                    break;
                default:
                    value = null;
                }

                // relationsMapに存在するカラム名なら、関連データとして処理
                if (relationsMap.containsKey(columnName) && value != null && value instanceof String) {
                    String stringValue = value.toString();
                    if (stringValue.contains(":")) {
                        // ':'の左側の値を取得
                        value = stringValue.split(":")[0];
                    }
                }

                record.put(columnName, formatValueByType(columnType, value));
            }
            records.add(record);
        }

        // Excelファイルを閉じる
        workbook.close();

        // 本設定
        importCount.set(records.size());

        return records;
    }

    /**
     * バッチアップデートを実行する
     * 
     * @param sql SQL文
     * @param records レコード情報のリスト
     * @param columnTypes 列名と型情報のマップ
     * @param primaryKey 主キー
     * @param totalRecords 全レコード数
     * @throws SQLException
     */
    private void executeBatchUpdate(String sql, List<Map<String, Object>> records, Map<String, String> columnTypes,
            String primaryKey, int totalRecords) throws SQLException {
        jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {

            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                Map<String, Object> record = records.get(i);
                int index = 1;
                for (String columnName : columnTypes.keySet()) {
                    String columnType = columnTypes.get(columnName);
                    Object value = record.get(columnName);
                    setPreparedStatementValue(ps, index, columnType, value);
                    index++;
                }
                // 更新の場合、WHERE句の主キー値を設定
                if (sql.startsWith("UPDATE")) {
                    Object primaryKeyValue = record.get(primaryKey);
                    if (primaryKeyValue instanceof Integer) {
                        ps.setInt(index, (Integer) primaryKeyValue);
                    } else if (primaryKeyValue instanceof Long) {
                        ps.setLong(index, (Long) primaryKeyValue);
                    } else if (primaryKeyValue instanceof String) {
                        ps.setString(index, (String) primaryKeyValue);
                    } else {
                        ps.setString(index, primaryKeyValue.toString());
                    }
                    // System.out.println("Updating record with primary key: " + primaryKeyValue);
                    // //
                    // デバッグログ
                }

                // 進捗管理
                progressStatus_Service.setCurrentValue2(progressStatusId.get(), i + 1);
            }

            @Override
            public int getBatchSize() {
                return records.size();
            }
        });
    }

    /**
     * 
     * エクセルファイルから列名と型情報を取得する
     * 
     * @param excelFilePath エクセルファイルのパス
     * @param SheetName シート名
     * @return 列名と型情報のマップ
     * @throws IOException
     */
    public Map<String, String> getColumnTypes(String excelFilePath, String SheetName, String tableName) throws IOException {
        String crlf = System.lineSeparator(); // 改行コード
        Map<String, String> columnTypes = new LinkedHashMap<>();// 列名と型情報を保持するMap

        // Excelファイルを読み込む
        Path path = Paths.get(excelFilePath);
        InputStream inputStream = Files.newInputStream(path);
        try (Workbook workbook = new XSSFWorkbook(inputStream)) {
            // 対象シートを取得する
            Sheet sheet = workbook.getSheet(SheetName);

            // 列名が記載された行番号を取得する
            Integer Row2 = PoiUtil.findRow(sheet, "#R2#");
            if (Row2 == -1) {
                workbook.close();
                throw new RuntimeException("制御文字「#R2#」がエクセルに発見できませんでした:シート名[" + SheetName + "]'");
            }
            // 型情報が記載された行番号を取得する
            Integer Row3 = PoiUtil.findRow(sheet, "#R3#");
            if (Row3 == -1) {
                workbook.close();
                throw new RuntimeException("制御文字「#R3#」がエクセルに発見できませんでした:シート名[" + SheetName + "]'");
            }
            // 開始列番号を取得する
            Integer Col2 = PoiUtil.findCol(sheet, "#C2#");
            if (Col2 == -1) {
                workbook.close();
                throw new RuntimeException("制御文字「#C2#」がエクセルに発見できませんでした:シート名[" + SheetName + "]'");
            }

            // 2行目から列名を取得する
            Row headerRow = sheet.getRow(Row2);
            for (int i = Col2; i < headerRow.getLastCellNum(); i++) {
                Cell cell = headerRow.getCell(i);
                if (cell == null || cell.getCellType() == CellType.BLANK) {
                    break;
                }
                String columnName = cell.getStringCellValue().trim();

                // 3行目から型情報を取得する
                Row typeRow = sheet.getRow(Row3);
                if (typeRow == null) {
                    throw new IllegalArgumentException("Type row is missing");
                }
                Cell typeCell = typeRow.getCell(i);
                if (typeCell == null || typeCell.getCellType() == CellType.BLANK) {
                    throw new IllegalArgumentException("型の指定が不正です。カラム名=: " + columnName);
                }
                String columnType = typeCell.getStringCellValue().trim().toLowerCase();

                // DBの型に対応するspringの型に変換する
                switch (columnType) {
                case "boolean":
                    columnType = "Boolean";
                    break;
                case "smallint":
                    columnType = "short";
                    break;
                case "integer":
                    columnType = "Integer";
                    break;
                case "bigint":
                    columnType = "long";
                    break;
                case "real":
                    columnType = "float";
                    break;
                case "double":
                case "double precision":
                    columnType = "Double";
                    break;
                case "numeric":
                    columnType = "java.math.BigDecimal";
                    break;
                case "text":
                case "varchar":
                case "character":
                case "character varying":
                    columnType = "String";
                    break;
                case "bytea":
                    columnType = "byte[]";
                    break;
                case "timestamp":
                case "date":
                case "time":
                case "timestamp without time zone":
                    columnType = "Date";
                    break;
                case "smallserial":
                    columnType = "short";
                    break;
                case "serial":
                    columnType = "int";
                    break;
                case "bigserial":
                    columnType = "long";
                    break;
                default:
                    throw new IllegalArgumentException("取込テーブルのカラムがサポート対象外です。カラム名=: " + columnName + "、型" + columnType);
                }

                // 列名と型情報をMapに格納する
                columnTypes.put(columnName, columnType);
            }

            // Excelファイルを閉じる
            workbook.close();
        }
        
        // DBの列名を取得
        String sql = "SELECT column_name FROM information_schema.columns WHERE table_name = ?";
        @SuppressWarnings("deprecation")
        List<String> dbColumns = jdbcTemplate.queryForList(sql, new Object[] { tableName }, String.class);

        // 過不足チェック
        List<String> missingColumns = dbColumns.stream().filter(col -> !columnTypes.containsKey(col)).collect(Collectors.toList());
        List<String> extraColumns = columnTypes.keySet().stream().filter(col -> !dbColumns.contains(col)).collect(Collectors.toList());

        if (!missingColumns.isEmpty() || !extraColumns.isEmpty()) {
            throw new IllegalArgumentException(
                    "カラム定義の不一致があります: " + crlf +
                    (missingColumns.isEmpty() ? "" : "エクセルに不足しているカラム=[" + String.join(", ", missingColumns) + "]") + crlf +
                    (extraColumns.isEmpty() ? "" : " エクセルに余分なカラム=[" + String.join(", ", extraColumns) + "]") + crlf
            );
        }
        

        return columnTypes;
    }

    /**
     * 指定されたExcelファイルに対してレコードの状態（新規、更新、変更無し）を更新する
     * 
     * @param excelFilePath Excelファイルのパス
     * @param sheetName シート名
     * @param records 全レコードのリスト
     * @param recordStatuses レコードの状態リスト
     * @throws IOException 入出力例外が発生した場合
     */
    public void writeBackToExcel(String excelFilePath, String sheetName, List<Map<String, Object>> records, List<String> recordStatuses) throws IOException {
        // 指定されたパスからExcelファイルを読み込む
        Path path = Paths.get(excelFilePath);
        InputStream inputStream = Files.newInputStream(path);
        Workbook workbook = new XSSFWorkbook(inputStream);

        // 指定されたシートを取得する
        Sheet sheet = workbook.getSheet(sheetName);

        // 制御文字 #R6# と #システム情報# がある行と列を特定する
        Integer controlRow = PoiUtil.findRow(sheet, "#R6#");
        Integer controlCol = PoiUtil.findCol(sheet, "#システム情報#");

        // 制御文字が見つからない場合は例外をスローする
        if (controlRow == -1) {
            workbook.close();
            throw new RuntimeException("制御文字「#R6#」がエクセルに発見できませんでした:シート名[" + sheetName + "]'");
        }
        if (controlCol == -1) {
            workbook.close();
            throw new RuntimeException("制御文字「#システム情報#」がエクセルに発見できませんでした:シート名[" + sheetName + "]'");
        }

        // 列のインデックスを特定する
        int modifyCountCol = PoiUtil.findCol(sheet, "modify_count");
        int updateUIdCol = PoiUtil.findCol(sheet, "update_u_id");
        int updateDateCol = PoiUtil.findCol(sheet, "update_date");

        // レコードの状態をエクセルに書き込む
        for (int i = 0; i < records.size(); i++) {

            // 進捗管理
            progressStatus_Service.setCurrentValue2(progressStatusId.get(), i + 1);

            Map<String, Object> record = records.get(i);
            Row row = sheet.getRow(controlRow + i);
            if (row == null) {
                row = sheet.createRow(controlRow + i);
            }

            // システム情報列に状態を書き込む
            Cell cell = row.getCell(controlCol);
            if (cell == null) {
                cell = row.createCell(controlCol);
            }
            CellStyle cellStyle = cell.getCellStyle();// 既存のセル書式を取得
            String errorMsg = getExceptionMessages(i);
            if (errorMsg.isEmpty()) {
                cell.setCellValue(recordStatuses.get(i));
            } else {
                cell.setCellValue(recordStatuses.get(i) + ":" + errorMsg);
            }
            cell.setCellStyle(cellStyle);// 既存のセル書式を適用

            // 新規または更新の場合にその他の値を書き込む
            if (recordStatuses.get(i).equals("新規") || recordStatuses.get(i).equals("更新")) {
                if (modifyCountCol != -1) {
                    Cell modifyCountCell = row.getCell(modifyCountCol);
                    if (modifyCountCell == null) {
                        modifyCountCell = row.createCell(modifyCountCol);
                    }
                    CellStyle modifyCountCellStyle = modifyCountCell.getCellStyle();// 既存のセル書式を取得
                    modifyCountCell.setCellValue(record.get("modify_count").toString());
                    modifyCountCell.setCellStyle(modifyCountCellStyle);// 既存のセル書式を適用
                }
                if (updateUIdCol != -1) {
                    Cell updateUIdCell = row.getCell(updateUIdCol);
                    if (updateUIdCell == null) {
                        updateUIdCell = row.createCell(updateUIdCol);
                    }
                    CellStyle updateUIdCellStyle = updateUIdCell.getCellStyle(); // 既存のセル書式を取得
                    updateUIdCell.setCellValue(record.get("update_u_id").toString());
                    updateUIdCell.setCellStyle(updateUIdCellStyle);// 既存のセル書式を適用
                }
                if (updateDateCol != -1) {
                    Cell updateDateCell = row.getCell(updateDateCol);
                    if (updateDateCell == null) {
                        updateDateCell = row.createCell(updateDateCol);
                    }
                    CellStyle updateDateCellStyle = updateDateCell.getCellStyle();// 既存のセル書式を適用
                    updateDateCell.setCellValue(record.get("update_date").toString());
                    updateDateCell.setCellStyle(updateDateCellStyle);// 既存のセル書式を適用
                }
            }
        }

        // 更新された内容をファイルに書き戻す
        try (FileOutputStream fileOut = new FileOutputStream(excelFilePath)) {
            workbook.write(fileOut);
        }

        // ワークブックを閉じる
        workbook.close();
    }

    /**
     * システムテーブルから主キーを動的に取得するメソッド
     * 
     * @param tableName テーブル名
     * @return 主キーのカラム名
     * @throws SQLException
     */
    public String getPrimaryKey(String tableName) throws SQLException {
        String sql = "SELECT kcu.column_name "
                + "FROM information_schema.table_constraints tco "
                + "JOIN information_schema.key_column_usage kcu "
                + "ON kcu.constraint_name = tco.constraint_name "
                + "AND kcu.constraint_schema = tco.constraint_schema "
                + "WHERE tco.constraint_type = 'PRIMARY KEY' " + "AND kcu.table_name = ?";
        List<String> primaryKeys = jdbcTemplate.queryForList(sql, new Object[] { tableName }, String.class);
        if (primaryKeys.isEmpty()) {
            throw new SQLException("Primary key not found for table: " + tableName);
        }
        return primaryKeys.get(0);
    }

    /**
     * メモリ上の各カラムをハッシュ化する
     * 
     * @param record レコード情報
     * @return ハッシュ値
     * @throws NoSuchAlgorithmException
     */
    public static String calculateHash(Map<String, Object> record) throws NoSuchAlgorithmException {
        // システムカラムを除外する
        List<String> excludeColumns = Arrays.asList("update_u_id", "update_date");
        // 除外（存在する場合のみ）
        Map<String, Object> filteredRecord = record.entrySet().stream()
            .filter(entry -> entry.getKey() != null && entry.getValue() != null) // nullは無視
            .filter(entry -> !excludeColumns.contains(entry.getKey()))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        
        // ハッシュ計算
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        String recordString = filteredRecord.toString();
        byte[] hashBytes = digest.digest(recordString.getBytes(StandardCharsets.UTF_8));
        StringBuilder hexString = new StringBuilder();
        for (byte b : hashBytes) {
            hexString.append(String.format("%02x", b));
        }
        return hexString.toString();
    }

    /**
     * 指定された型に基づいて値をフォーマットする
     * 
     * @param columnType 列の型
     * @param value 値
     * @return フォーマットされた値
     */
    private Object formatValueByType(String columnType, Object value) {
        switch (columnType) {
        case "Boolean":
            return value == null ? null : Boolean.valueOf(value.toString());
        case "short":
            return value == null ? null : Short.valueOf(value.toString());
        case "Integer":
            return value == null ? null : Integer.valueOf(value.toString());
        case "Double":
            return value == null ? null : Double.valueOf(value.toString());
        case "float":
            return value == null ? null : Float.valueOf(value.toString());
        case "java.math.BigDecimal":
            return value == null ? null : new BigDecimal(value.toString());
        case "String":
            return value == null ? null : value.toString();
        case "byte[]":
            return value == null ? null : value;
        case "Date":
            if (value == null)
                return null;
            if (value instanceof Number) {
                // Excelのシリアル日付値の場合
                double excelDate = ((Number) value).doubleValue();
                Date dateValue = DateUtil.getJavaDate(excelDate);
                if (DateParserUtil.hasTime(dateValue)) {
                    return DateParserUtil.dateToStr(dateValue, "yyyy-MM-dd' 'HH:mm:ss.SSS");
                } else {
                    return DateParserUtil.dateToStr(dateValue, "yyyy-MM-dd");
                }
            } else {
                // 文字列形式の日付の場合
                String strValue = value.toString();
                Date dateValue = DateParserUtil.strToDate(strValue);
                if (DateParserUtil.hasTime(dateValue)) {
                    return DateParserUtil.dateToStr(dateValue, "yyyy-MM-dd' 'HH:mm:ss.SSS");
                } else {
                    return DateParserUtil.dateToStr(dateValue, "yyyy-MM-dd");
                }
            }
        default:
            throw new IllegalArgumentException("サポートされない型です: " + columnType);
        }
    }

    /**
     * PreparedStatement(SQL発行用)の値を設定する データ型に応じた適切な値を設定する
     * 
     * @param ps PreparedStatement
     * @param index インデックス
     * @param columnType カラムの型
     * @param value 値
     * @throws SQLException
     */
    private void setPreparedStatementValue(PreparedStatement ps, int index, String columnType, Object value)
            throws SQLException {
        switch (columnType) {
        // カラムの型がBooleanの場合
        case "Boolean":
            // 値がnullの場合は、PreparedStatementのsetNull()メソッドを使用して、null値を設定する
            if (value == null) {
                ps.setNull(index, Types.BOOLEAN);
            }
            // 値がBoolean型の場合は、PreparedStatementのsetBoolean()メソッドを使用して、Boolean型に変換する
            else if (value instanceof Boolean) {
                ps.setBoolean(index, (Boolean) value);
            }
            // 値が文字列型の場合は、文字列をBoolean型に変換する
            else if (value instanceof String) {
                String strValue = (String) value;
                // 文字列が"true"または"false"の場合は、Boolean.valueOf()メソッドを使用して、Boolean型に変換する
                if ("true".equalsIgnoreCase(strValue) || "false".equalsIgnoreCase(strValue)) {
                    ps.setBoolean(index, Boolean.valueOf(strValue));
                }
                // それ以外の場合は、エラーをスローする
                else {
                    throw new SQLException("エラー: カラムの値をBooleanにキャストできません。インデックス " + index);
                }
            }
            // それ以外の場合は、エラーをスローする
            else {
                throw new SQLException("エラー: カラムの値をBooleanにキャストできません。インデックス " + index);
            }
            break;
        case "short":
            if (value == null) {
                ps.setNull(index, Types.SMALLINT);
            }else {
                ps.setShort(index, (Short) value);
            }
            break;
        case "Integer":
            if (value == null) {
                ps.setNull(index, Types.INTEGER);
            }else {
                ps.setInt(index, Integer.valueOf(value.toString()));
            }
            break;
        case "long":
            if (value == null) {
                ps.setNull(index, Types.BIGINT);
            }else {
                ps.setLong(index, Long.valueOf(value.toString()));
            }
            break;
        case "Double":
            if (value == null) {
                ps.setNull(index, Types.DOUBLE);
            }else {
                ps.setDouble(index, Double.valueOf(value.toString()));
            }
            break;
        case "float":
            if (value == null) {
                ps.setNull(index, Types.REAL);
            }else {
                ps.setFloat(index, (Float) value);
            }
            break;
        case "java.math.BigDecimal":
            if (value == null) {
                ps.setNull(index, Types.NUMERIC);
            } else if (value instanceof BigDecimal) {
                ps.setBigDecimal(index, (BigDecimal) value);
            } else {
                ps.setBigDecimal(index, new BigDecimal(value.toString()));
            }
            break;
        case "String":
            if (value == null) {
                ps.setNull(index, Types.VARCHAR);
            }else {
                ps.setString(index, (String) value);
            }
            break;
        case "byte[]":
            if (value == null) {
                ps.setNull(index, Types.BINARY);
            }else {
                ps.setBytes(index, (byte[]) value);
            }
            break;
        // カラムの型がDateの場合
        case "Date":
            // 値がnullの場合は、PreparedStatementのsetNull()メソッドを使用して、null値を設定する
            if (value == null) {
                ps.setNull(index, Types.TIMESTAMP);
            }
            // 値がDate型の場合は、PreparedStatementのsetTimestamp()メソッドを使用して、Date型に変換する
            else if (value instanceof Date) {
                ps.setTimestamp(index, new Timestamp(((Date) value).getTime()));
            }
            // 値が文字列型の場合は、文字列をDate型に変換する
            else if (value instanceof String) {
                String strValue = (String) value;
                Date dateValue = DateParserUtil.strToDate(strValue);
                ps.setTimestamp(index, new Timestamp(dateValue.getTime()));
                // それ以外の場合は、エラーをスローする
            } else {
                throw new SQLException("エラー: カラムの値をDateにキャストできません。インデックス " + index);
            }
            break;
        default:
            throw new SQLException("取込テーブルのカラムがサポート対象外です。カラム名: " + columnType);
        }
    }

    /**
     * 列の型を取得する
     * 
     * @param columnTypes 列名と型情報のマップ
     * @param primaryKey 主キー
     * @return 列の型
     */
    private String getColumnType(Map<String, String> columnTypes, String primaryKey) {
        return columnTypes.get(primaryKey);
    }
}
