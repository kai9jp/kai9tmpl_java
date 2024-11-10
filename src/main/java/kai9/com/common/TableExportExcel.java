package kai9.com.common;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import kai9.com.service.ProgressStatus_Service;
import kai9.libs.Kai9Utils;
import kai9.libs.PoiUtil;

@Component
public class TableExportExcel {

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Value("${spring.datasource.primary.schema}")
    private String primarySchema;

    @Autowired
    private ProgressStatus_Service progressStatus_Service;

    // 件数格納用の変数(スレッドセーフ)
    private ThreadLocal<Integer> progressStatusId = ThreadLocal.withInitial(() -> 0);

    /**
     * テーブル出力(エクセル)
     * 第1引数:テーブル名
     * 第2引数:出力ファイル名
     * 第3引数:プログレスバーのID
     * 第4引数:LEFT JOINのためのテーブルとカラムの関係情報（relationsSB）
     * 
     * @throws Exception
     */
    public void exec(String tableName, String where_sql, String FileName, int progressStatusId, StringBuilder relationsSB) throws Exception {
        this.progressStatusId.set(progressStatusId);
        exportTableInfoToExcel(tableName, where_sql, FileName, relationsSB);
    }

    // ラッパー(where_sql無し)
    public void exec(String tableName, String FileName, int progressStatusId, StringBuilder relationsSB) throws Exception {
        this.progressStatusId.set(progressStatusId);
        exportTableInfoToExcel(tableName, "", FileName, relationsSB);
    }

    // ラッパー(relationsSB無し)
    public void exec(String tableName, String where_sql, String FileName, int progressStatusId) throws Exception {
        this.progressStatusId.set(progressStatusId);
        StringBuilder relationsSB = new StringBuilder();
        exportTableInfoToExcel(tableName, where_sql, FileName, relationsSB);
    }

    // ラッパー(where_sql,relationsSB無し)
    public void exec(String tableName, String FileName, int progressStatusId) throws Exception {
        this.progressStatusId.set(progressStatusId);
        StringBuilder relationsSB = new StringBuilder();
        exportTableInfoToExcel(tableName, "", FileName, relationsSB);
    }

    /**
     * テーブル情報をExcelファイルに出力する
     * 
     * @param tableName 出力するテーブル名
     * @param FileName 出力先のファイル名
     * @param relationsSB LEFT JOINのためのテーブルとカラムの関係情報
     * @throws Exception
     */
    public void exportTableInfoToExcel(String tableName, String where_sql, String FileName, StringBuilder relationsSB) throws Exception {
        try {
            // 進捗管理
            progressStatus_Service.init(progressStatusId.get());
            progressStatus_Service.updateProgress2(progressStatusId.get(), "無し", -1, "無し");
            progressStatus_Service.setMaxValue1(progressStatusId.get(), 11);
            progressStatus_Service.setCurrentValue1(progressStatusId.get(), 1);

            // 保存場所が無ければ作成する(mkdirsは、途中の階層を全て作成する仕様)
            File file = new File(FileName);
            File parentDir = file.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }

            // カラム情報を取得するためのSQLクエリ
            String sql = "SELECT a.column_name, a.column_default, a.data_type, a.character_maximum_length, " +
                    "a.numeric_precision, a.numeric_scale, a.ordinal_position, d.description " +
                    "FROM information_schema.columns a " +
                    "JOIN pg_class c ON a.table_name = c.relname " +
                    "JOIN pg_namespace n ON c.relnamespace = n.oid " +
                    "JOIN pg_attribute attr ON attr.attname = a.column_name AND attr.attrelid = c.oid " +
                    "LEFT JOIN pg_description d ON d.objoid = c.oid AND d.objsubid = attr.attnum " +
                    "WHERE a.table_name = ? AND a.table_schema = ? " +
                    "ORDER BY a.ordinal_position";

            // カラム情報を取得し、リストに格納する
            List<String[]> columnInfoList = jdbcTemplate.query(sql, (resultSet, rowNum) -> {
                String[] columnInfo = new String[8];
                columnInfo[0] = resultSet.getString("column_name") != null ? resultSet.getString("column_name") : "-";
                columnInfo[1] = resultSet.getString("column_default") != null ? resultSet.getString("column_default") : "-";
                columnInfo[2] = resultSet.getString("data_type") != null ? resultSet.getString("data_type") : "-";
                columnInfo[3] = resultSet.getString("character_maximum_length") != null ? resultSet.getString("character_maximum_length") : "-";
                columnInfo[4] = resultSet.getString("numeric_precision") != null ? resultSet.getString("numeric_precision") : "-";
                columnInfo[5] = resultSet.getString("numeric_scale") != null ? resultSet.getString("numeric_scale") : "-";
                columnInfo[6] = resultSet.getString("ordinal_position") != null ? resultSet.getString("ordinal_position") : "-";
                columnInfo[7] = resultSet.getString("description") != null ? resultSet.getString("description") : "-";
                return columnInfo;
            }, tableName, primarySchema);
            progressStatus_Service.setCurrentValue1(progressStatusId.get(), 2);

            // Excelファイルを作成する
            XSSFWorkbook workbook = new XSSFWorkbook();
            String sheetName = tableName.length() > 31 ? "データ" : tableName;// シート名上限の31文字を超える場合は一律「データ」という名前のシートで扱う
            XSSFSheet sheet = workbook.createSheet(sheetName);

            // カラム情報の行を設定する
            for (int i = 0; i < columnInfoList.size(); i++) {
                String[] columnInfo = columnInfoList.get(i);
                // カラム名 (和名)
                createCellWithValue(sheet, 1, i + 2, columnInfo[7]);
                // カラム名 (英名)
                createCellWithValue(sheet, 2, i + 2, columnInfo[0]);
                // データ型
                createCellWithValue(sheet, 3, i + 2, columnInfo[2]);
                // 桁数 (数値型の場合は非表示)
                if ("numeric".equals(columnInfo[2]) || "decimal".equals(columnInfo[2])) {
                    String precisionScale = (columnInfo[4] != null && !columnInfo[4].isEmpty() && columnInfo[5] != null && !columnInfo[5].isEmpty())
                            ? columnInfo[4] + "," + columnInfo[5]
                            : "";
                    createCellWithValue(sheet, 4, i + 2, precisionScale);
                } else if ("smallint".equals(columnInfo[2]) || "integer".equals(columnInfo[2]) ||
                        "bigint".equals(columnInfo[2]) || "real".equals(columnInfo[2]) ||
                        "double precision".equals(columnInfo[2])) {
                    createCellWithValue(sheet, 4, i + 2, "");
                } else {
                    createCellWithValue(sheet, 4, i + 2, columnInfo[3]);
                }
            }
            progressStatus_Service.setCurrentValue1(progressStatusId.get(), 3);

            // カラム情報のヘッダーを設定する
            createCellWithValue(sheet, 1, 1, "カラム名(和)");
            createCellWithValue(sheet, 2, 1, "カラム名(英)");
            createCellWithValue(sheet, 3, 1, "型");
            createCellWithValue(sheet, 4, 1, "桁数");
            createCellWithValue(sheet, 5, 1, "No");
            createCellWithValue(sheet, 0, 1, "#C1#");
            createCellWithValue(sheet, 0, 2, "#C2#");
            createCellWithValue(sheet, 1, 0, "#R1#");
            createCellWithValue(sheet, 2, 0, "#R2#");
            createCellWithValue(sheet, 3, 0, "#R3#");
            createCellWithValue(sheet, 4, 0, "#R4#");
            createCellWithValue(sheet, 5, 0, "#R5#");
            createCellWithValue(sheet, 6, 0, "#R6#");
            progressStatus_Service.setCurrentValue1(progressStatusId.get(), 4);

            // relationsSBの解析
            Map<String, String[]> relationMap = parseRelationsSB(relationsSB.toString());

            // LEFT JOINのためのSQL生成
            String joinClause = buildLeftJoinClause(relationMap, tableName);

            // 明示的なSELECT句の作成
            String selectColumns = buildSelectColumns(relationMap, tableName);

            // プライマリキーを特定するSQL
            String primaryKeysSql = "SELECT kcu.column_name " +
                    "FROM information_schema.table_constraints AS tc " +
                    "JOIN information_schema.key_column_usage AS kcu " +
                    "ON tc.constraint_name = kcu.constraint_name " +
                    "AND tc.table_schema = kcu.table_schema " +
                    "WHERE tc.constraint_type = 'PRIMARY KEY' " +
                    "AND tc.table_name = ?";

            // SQLの実行、プライマリキーの列名を取得
            @SuppressWarnings("deprecation")
            List<String> primaryKeys = jdbcTemplate.queryForList(primaryKeysSql, new Object[] { tableName }, String.class);
            // modify_count'カラムを除外
            List<String> filteredPrimaryKeys = primaryKeys.stream()
                    .filter(pk -> !pk.equals("modify_count"))
                    .collect(Collectors.toList());
            // プライマリキーが存在する場合のみORDER BYを追加
            String orderByClause = "";
            if (!filteredPrimaryKeys.isEmpty()) {
                orderByClause = "ORDER BY " + String.join(", ", filteredPrimaryKeys);
            }

            // テーブルデータを取得し、Excelに出力する
            String queryDataSql = "SELECT " + selectColumns + " FROM " + tableName + " " + joinClause + " " + where_sql + " " + orderByClause;
            List<Map<String, Object>> tableData = jdbcTemplate.queryForList(queryDataSql);
            int currentRow = 6;

            // システムカラムの列番号記憶用
            List<Integer> systemColumns = new ArrayList<>();
            List<String> systemColumnNames = new ArrayList<>();

            // テーブルデータの各行に対して、各カラムの値を出力する
            for (Map<String, Object> rowData : tableData) {
                for (int i = 0; i < columnInfoList.size(); i++) {
                    String columnName = columnInfoList.get(i)[0];
                    Object value = rowData.get(columnName);

                    if (value != null) {
                        // relationsSBに基づいて、対応する別テーブルの値を取得
                        String[] relatedData = relationMap.get(columnName);
                        if (relatedData != null) {
                            // .を_に置換してエイリアス名を使用
                            String relatedTableA = relatedData[0].replace(".", "_");
                            String relatedTableB = relatedData[1].replace(".", "_");
                            String combinedValue = rowData.get(relatedTableA) + ":" + rowData.get(relatedTableB);
                            // カラムAとカラムBの値をセット
                            createCellWithValue(sheet, currentRow, i + 2, combinedValue);
                        } else {
                            // 通常のデータを出力
                            createCellWithValue(sheet, currentRow, i + 2, value.toString());
                        }
                        // Noを出力
                        createCellWithValue(sheet, currentRow, 1, String.valueOf(currentRow - 5));
                    }
                    // システムカラムの列番号記を憶用
                    if (columnName.toLowerCase().equals("modify_count") || columnName.toLowerCase().equals("update_u_id")
                            || columnName.toLowerCase().equals("update_date") || columnName.toLowerCase().equals("delflg")) {
                        if (!systemColumns.contains(i + 2)) {
                            systemColumns.add(i + 2);
                        }
                        if (!systemColumnNames.contains(columnName)) {
                            systemColumnNames.add(columnName);
                        }
                    }
                }
                currentRow++;
            }
            progressStatus_Service.setCurrentValue1(progressStatusId.get(), 5);

            // 目盛り線を消す
            sheet.setDisplayGridlines(false);
            progressStatus_Service.setCurrentValue1(progressStatusId.get(), 6);

            // #システム情報#
            int systemCol = PoiUtil.getLastColumnIndex(sheet) + 1;
            createCellWithValue(sheet, 1, systemCol, "#システム情報#");

            // 格子状の罫線を引く
            PoiUtil.setGridLines_void(sheet, 1, 1, sheet.getLastRowNum(), PoiUtil.getLastColumnIndex(sheet));

            // 背景色とフォント色を設定する
            XSSFColor backgroundColor1 = new XSSFColor(java.awt.Color.decode("#F2CEEF"), null);// 薄い青
            XSSFColor backgroundColor2 = new XSSFColor(java.awt.Color.decode("#ECECEC"), null);// 薄い灰

            CellRangeAddress range = new CellRangeAddress(1, 5, 1, PoiUtil.getLastColumnIndex(sheet)); // ※startRow, endRow, startCol, endCol
            PoiUtil.setCellBackgroundAndFontColor_void(sheet, range, backgroundColor1, IndexedColors.DARK_BLUE);
            // #R1#～#R6#の色
            range = new CellRangeAddress(1, 6, 0, 0); // ※startRow, endRow, startCol, endCol
            PoiUtil.setCellBackgroundAndFontColor_void(sheet, range, backgroundColor1, IndexedColors.WHITE);
            // #C1#の色
            range = new CellRangeAddress(0, 0, 1, 2); // ※startRow, endRow, startCol, endCol
            PoiUtil.setCellBackgroundAndFontColor_void(sheet, range, backgroundColor1, IndexedColors.WHITE);
            // 6行目のNoの色
            range = new CellRangeAddress(5, 5, 2, PoiUtil.getLastColumnIndex(sheet)); // ※startRow, endRow, startCol, endCol
            PoiUtil.setCellBackgroundAndFontColor_void(sheet, range, backgroundColor2, IndexedColors.WHITE);
            // B列のNoの色
            range = new CellRangeAddress(1, sheet.getLastRowNum(), 1, 1); // ※startRow, endRow, startCol, endCol
            PoiUtil.setCellBackgroundAndFontColor_void(sheet, range, backgroundColor1, IndexedColors.DARK_BLUE);
            // #システム情報#
            range = new CellRangeAddress(1, sheet.getLastRowNum(), systemCol, systemCol); // ※startRow, endRow, startCol, endCol
            PoiUtil.setCellBackgroundAndFontColor_void(sheet, range, IndexedColors.GREY_50_PERCENT, IndexedColors.BLACK);
            progressStatus_Service.setCurrentValue1(progressStatusId.get(), 7);

            // システムカラムの色をグレーにする
            for (int i = 0; i < systemColumns.size(); i++) {
                Integer column = systemColumns.get(i);

                if (systemColumnNames.get(i).toLowerCase().equals("delflg")) {
                    range = new CellRangeAddress(1, 5, column, column); // ※startRow, endRow, startCol, endCol
                    createCellWithValue(sheet, 5, column, "※システムカラム");
                } else {
                    range = new CellRangeAddress(1, sheet.getLastRowNum(), column, column); // ※startRow, endRow, startCol, endCol
                    createCellWithValue(sheet, 5, column, "※システムカラム(データを手で書き換えない事)");
                }
                PoiUtil.setCellBackgroundAndFontColor_void(sheet, range, IndexedColors.GREY_25_PERCENT, IndexedColors.DARK_BLUE);

                // 縮小して表示
                CellStyle existingCellStyle = sheet.getRow(5).getCell(column).getCellStyle();
                CellStyle newCellStyle = workbook.createCellStyle();
                newCellStyle.cloneStyleFrom(existingCellStyle);
                newCellStyle.setShrinkToFit(true);
                sheet.getRow(5).getCell(column).setCellStyle(newCellStyle);
            }
            progressStatus_Service.setCurrentValue1(progressStatusId.get(), 8);

            // シート内の全フォントを統一する
            PoiUtil.setFont(sheet, "メイリオ", 11);

            // 書式を文字列にする
            range = new CellRangeAddress(1, sheet.getLastRowNum(), 0, systemCol); // ※startRow, endRow, startCol, endCol
            PoiUtil.setCellFormatAsText(sheet, range);

            // 固定するセルの行番号と列番号を指定（C7で固定）
            int rowNum = 6;
            int colNum = 2;
            // ウィンドウを固定
            sheet.createFreezePane(colNum, rowNum, colNum, rowNum);
            progressStatus_Service.setCurrentValue1(progressStatusId.get(), 9);

            // 自動で列幅を揃える：最大30文字幅に制限（エクセルは仕様で、内部的には文字数で列幅を記憶くしてるっぽい)
            int maxWidth = 30 * 256;
            for (int i = 0; i <= PoiUtil.getLastColumnIndex(sheet); i++) {
                // 自動調整
                sheet.autoSizeColumn(i);
                // 最大30文字幅に制限
                if (sheet.getColumnWidth(i) > maxWidth) {
                    sheet.setColumnWidth(i, maxWidth);
                }
            }

            // システム列の幅を狭くする
            sheet.getRow(0).setHeightInPoints(10);// ピクセル
            sheet.setColumnWidth(0, 256);// 256で1文字分
            progressStatus_Service.setCurrentValue1(progressStatusId.get(), 10);

            // デバック用(全シートの属性数を表示)
            // int totalStyles = workbook.getNumCellStyles();
            // System.out.println("Total number of cell styles: " + totalStyles);

            // Excelファイルをディスクに書き込む
            try (FileOutputStream fileOut = new FileOutputStream(FileName)) {
                workbook.write(fileOut);
            }

            // workbookを閉じる
            workbook.close();

            // 進捗管理(100%で表示)
            progressStatus_Service.updateProgress1(progressStatusId.get(), "完了", 100, "完了");
            Thread.sleep(1000);
        } catch (Exception e) {
            String msg = Kai9Utils.GetException(e);
            throw new Exception(msg);
        }

    }

    // メソッド: セルを作成し、値を設定する
    private XSSFCell createCellWithValue(XSSFSheet sheet, int rowNumber, int columnNumber, String value) {
        // 指定された行番号で行を取得
        XSSFRow row = sheet.getRow(rowNumber);
        // 行がnullの場合、新しい行を作成
        if (row == null) {
            row = sheet.createRow(rowNumber);
        }
        // 指定された列番号でセルを取得
        XSSFCell cell = row.getCell(columnNumber);
        // セルがnullの場合、新しいセルを作成
        if (cell == null) {
            cell = row.createCell(columnNumber);
        }
        // セルに値を設定
        cell.setCellValue(value);

        return cell;
    }

    /**
     * relationsSBを解析し、対象テーブルのカラム名と関連テーブルの情報をMapに格納する
     */
    private Map<String, String[]> parseRelationsSB(String relations) {
        Map<String, String[]> relationMap = new HashMap<>();
        String[] relationsArray = relations.split(",");
        for (String relation : relationsArray) {
            if (relation.isEmpty()) continue;
            String[] parts = relation.split("=");
            String columnName = parts[0];
            String[] relatedTables = parts[1].split(":");
            relationMap.put(columnName, relatedTables);
        }
        return relationMap;
    }

    /**
     * リレーション用
     * LEFT JOINのSQLクエリ部分を生成する
     */
    private String buildLeftJoinClause(Map<String, String[]> relationMap, String tableName) {
        StringBuilder joinClause = new StringBuilder();
        for (Map.Entry<String, String[]> entry : relationMap.entrySet()) {
            String columnName = entry.getKey();
            String[] relatedTables = entry.getValue();

            String[] tableAndColumnA = relatedTables[0].split("\\.");

            joinClause.append(" LEFT JOIN ")
                    .append(tableAndColumnA[0]).append(" AS ").append(tableAndColumnA[0])
                    .append(" ON ")
                    .append(tableAndColumnA[0]).append(".").append(tableAndColumnA[1])
                    .append(" = ")
                    .append(tableName).append(".").append(columnName);
        }
        return joinClause.toString();
    }

    /**
     * リレーション用
     * 明示的なSELECT句を作成するメソッド
     */
    private String buildSelectColumns(Map<String, String[]> relationMap, String tableName) {
        StringBuilder selectColumns = new StringBuilder(tableName + ".*,"); // 基本テーブルのすべてのカラム

        for (Map.Entry<String, String[]> entry : relationMap.entrySet()) {
            String[] relatedTables = entry.getValue();

            String[] tableAndColumnA = relatedTables[0].split("\\.");
            String[] tableAndColumnB = relatedTables[1].split("\\.");

            // 明示的にカラムを追加し、エイリアスにテーブル名も含めて区別する
            selectColumns.append(tableAndColumnA[0]).append(".").append(tableAndColumnA[1])
                    .append(" AS ").append(tableAndColumnA[0]).append("_").append(tableAndColumnA[1]).append(",");
            selectColumns.append(tableAndColumnB[0]).append(".").append(tableAndColumnB[1])
                    .append(" AS ").append(tableAndColumnB[0]).append("_").append(tableAndColumnB[1]).append(",");
        }

        // 最後のカンマを削除
        return selectColumns.toString().replaceAll(",$", "");
    }

}
