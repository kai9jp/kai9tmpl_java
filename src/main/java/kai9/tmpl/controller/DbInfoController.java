package kai9.tmpl.controller;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import javax.servlet.http.HttpServletResponse;

import org.json.JSONException;
import org.postgresql.jdbc.PgArray;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapperResultSetExtractor;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.google.gson.Gson;

import kai9.libs.JsonResponse;
import kai9.libs.Kai9Utils;

/**
 * 仕様
 * 
 * １．ユニークインデックス名、対象カラム名を取得(バックエンドで取得し、フロントエンドへ伝える)
 * ２．ユニークインデックス名、対象カラムのデータを送信(フロントエンドから送信)
 * ３．各ユニークインデックスにマッチする、SQLを作成し重複データを検索(バックエンドで実施)
 * ４．重複しているデータがあれば結果を返す(バックエンドで実施)
 * 
 */

@RestController
@CrossOrigin
public class DbInfoController {

    @Autowired
    JdbcTemplate jdbcTemplate;

    @PostMapping("/api/db-info")
    public void getDbInfo(@RequestBody
    Map<String, String> request, HttpServletResponse res) throws JSONException, IOException {
        String tableName = request.get("tableName");

        if (tableName == null || tableName.isEmpty()) {
            JsonResponse json = new JsonResponse();
            json.setReturn_code(HttpStatus.BAD_REQUEST.value());
            json.setMsg("テーブル名が指定されていません。");
            try {
                json.SetJsonResponse(res);
            } catch (IOException e) {
                e.printStackTrace();
            }
            return;
        }

        try {
            Map<String, Object> dbInfo = new HashMap<>();

            // 必須カラム情報を取得
            List<String> requiredColumns = jdbcTemplate.queryForList(
                    "SELECT column_name FROM information_schema.columns WHERE table_name = ? AND is_nullable = 'NO'",
                    String.class,
                    tableName);
            dbInfo.put("requiredColumns", requiredColumns);

            // 各カラムの長さを取得
            // 各カラムの長さおよび精度を格納するマップを初期化
            Map<String, String> columnLengths = new HashMap<>();

            // データベースからカラム情報を取得するクエリを実行
            jdbcTemplate.query(
                    "SELECT column_name, data_type, character_maximum_length, numeric_precision, numeric_scale " +
                            "FROM information_schema.columns " +
                            "WHERE table_name = ?",
                    rs -> {
                        // カラム名とデータ型を取得
                        String columnName = rs.getString("column_name");
                        String dataType = rs.getString("data_type");

                        // データ型に応じてカラムの長さや精度を設定
                        if (dataType.equals("character varying")) {
                            // VARCHAR型の場合、最大文字数を取得して格納
                            columnLengths.put(columnName, String.valueOf(rs.getInt("character_maximum_length")));
                        } else if (dataType.equals("numeric") || dataType.equals("double precision")) {
                            // NUMERIC型やDOUBLE PRECISION型の場合、精度とスケールを取得して格納
                            int precision = rs.getInt("numeric_precision");
                            int scale = rs.getInt("numeric_scale");
                            columnLengths.put(columnName, precision + "," + scale);
                        }
                    },
                    // テーブル名をパラメータとして渡す
                    tableName);
            // 取得したカラム情報をdbInfoに格納
            dbInfo.put("columnLengths", columnLengths);

            // ユニークインデックス情報を取得
            List<Map<String, Object>> uniqueColumns = jdbcTemplate.queryForList(
                    "SELECT i.relname as index_name, a.attname as column_name " +
                            "FROM pg_index ix " +
                            "JOIN pg_class i ON i.oid = ix.indexrelid " +
                            "JOIN pg_class t ON t.oid = ix.indrelid " +
                            "JOIN pg_attribute a ON a.attnum = ANY(ix.indkey) AND a.attrelid = t.oid " +
                            "WHERE t.relname = ? AND ix.indisunique = true AND a.attname != 'modify_count'", // modify_countは除外
                    tableName);

            // プライマリキー情報を取得
            List<Map<String, Object>> primaryKeys = jdbcTemplate.queryForList(
                    "SELECT n.nspname as schema_name, c.relname as table_name, a.attname as column_name " +
                            "FROM pg_index i " +
                            "JOIN pg_class c ON c.oid = i.indrelid " +
                            "JOIN pg_attribute a ON a.attnum = ANY(i.indkey) AND a.attrelid = c.oid " +
                            "JOIN pg_namespace n ON n.oid = c.relnamespace " +
                            "WHERE c.relname = ? " +
                            "AND i.indisprimary " +
                            "AND a.attname != 'modify_count'", // modify_countは除く
                    tableName);
            // プライマリキー情報を追加
            for (Map<String, Object> uniqueColumn : uniqueColumns) {
                uniqueColumn.put("isPrimaryKey", primaryKeys.stream()
                        .anyMatch(pk -> pk.get("column_name").equals(uniqueColumn.get("column_name"))));
            }
            dbInfo.put("uniqueColumns", uniqueColumns);

            JsonResponse json = new JsonResponse();
            json.setReturn_code(HttpStatus.OK.value());
            json.setMsg("テーブル情報を正常に取得しました。");
            json.setData(new Gson().toJson(dbInfo));
            json.SetJsonResponse(res);

        } catch (Exception e) {
            JsonResponse json = Kai9Utils.handleException(e, res);
            json.setReturn_code(HttpStatus.INTERNAL_SERVER_ERROR.value());
            try {
                json.SetJsonResponse(res);
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }

    /**
     * キー項目の重複チェックを行うメソッド。
     * クライアントから送信されたキー項目データを受け取り、既存データベースのレコードと比較して重複を検出します。
     *
     * @param request クライアントからのリクエストボディ。キー項目のカラム名とデータを含む。
     * @param res サーバーからのレスポンスオブジェクト。
     * @throws IOException
     * @throws JSONException
     */
    @PostMapping("/api/validate-keys")
    public void validateKeys(@RequestBody
    Map<String, Object> request, HttpServletResponse res) throws JSONException, IOException {
        // リクエストからテーブル名とキー項目のカラム名、データを取得
        String tableName = (String) request.get("tableName");
        List<?> keyColumnNamesRaw = (List<?>) request.get("keyColumnNames");
        List<?> keyDataRaw = (List<?>) request.get("keyData");

        // キー項目のカラム名を安全にキャスト
        List<String> keyColumnNames = keyColumnNamesRaw.stream()
                .filter(Objects::nonNull)
                .map(Object::toString)
                .collect(Collectors.toList());

        // 各内部リストの要素は異なる型（例えば、StringやIntegerなど）なので、一律、Objectとしてキャスト
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> keyData = keyDataRaw.stream()
                .filter(Objects::nonNull)
                .map(item -> (Map<String, Object>) item)
                .collect(Collectors.toList());

        // プライマリキーカラムの取得
        String primaryKeyColumnSql = "SELECT kcu.column_name " +
                "FROM information_schema.table_constraints tc " +
                "JOIN information_schema.key_column_usage kcu ON tc.constraint_name = kcu.constraint_name " +
                "WHERE tc.table_name = ? AND tc.constraint_type = 'PRIMARY KEY' " +
                "AND kcu.column_name != 'modify_count'";// modify_countを除く
        List<String> primaryKeyColumns = jdbcTemplate.query(
                primaryKeyColumnSql,
                new RowMapperResultSetExtractor<String>((rs, rowNum) -> rs.getString("column_name")),
                tableName);

        // ユニークインデックス情報を取得（プライマリキー除く）
        List<Map<String, Object>> uniqueIndexes = jdbcTemplate.queryForList(
                "SELECT i.relname as index_name, array_agg(a.attname) as column_names " +
                        "FROM pg_index ix " +
                        "JOIN pg_class i ON i.oid = ix.indexrelid " +
                        "JOIN pg_class t ON t.oid = ix.indrelid " +
                        "JOIN pg_attribute a ON a.attnum = ANY(ix.indkey) AND a.attrelid = t.oid " +
                        "WHERE t.relname = ? AND ix.indisunique = true AND ix.indisprimary = false AND a.attname != 'modify_count' " +
                        "GROUP BY i.relname",
                tableName);

        // ユニークインデックス情報を元に、各キーセットを取得
        List<Map<String, Object>> keySets = uniqueIndexes.stream()
                .map(index -> {
                    // インデックス名を取得
                    String indexName = (String) index.get("index_name");
                    // カラム名オブジェクトを取得
                    Object namesObject = index.get("column_names");

                    // カラム名オブジェクトがPgArrayのインスタンスかを確認
                    if (namesObject instanceof PgArray) {
                        PgArray pgArray = (PgArray) namesObject;
                        try {
                            // PgArrayをString配列に変換
                            String[] columnNamesArray = (String[]) pgArray.getArray();
                            // String配列をList<String>に変換
                            List<String> columnNames = Arrays.asList(columnNamesArray);
                            // インデックス名とカラム名リストを含むキーセットを作成
                            Map<String, Object> keySet = new HashMap<>();
                            keySet.put("index_name", indexName);
                            keySet.put("column_names", columnNames);
                            return keySet;
                        } catch (SQLException e) {
                            // PgArrayからカラム名を取得する際にエラーが発生した場合
                            throw new IllegalStateException("column_namesの取得中にエラーが発生しました", e);
                        }
                    } else {
                        // namesObjectがPgArrayではない場合
                        throw new IllegalStateException("column_namesがPgArrayではありません");
                    }
                })
                .collect(Collectors.toList());

        try {
            List<String> errors = new ArrayList<>();

            int chunkSize = 10;// 1000より100、100より10の方が性能が速い。1000だと一回のパラメータが多いのでSQLレスポンスが激遅になる
            // データをチャンクに分割して処理
            for (int i = 0; i < keyData.size(); i += chunkSize) {
                int end = Math.min(keyData.size(), i + chunkSize);
                List<Map<String, Object>> chunk = keyData.subList(i, end);

                // chunkのprimary_key_dataを抽出し、重複を排除
                Set<Object> uniquePrimaryKeys = chunk.stream()
                        .map(map -> map.get("primary_key_data"))
                        .collect(Collectors.toSet());

                // NOT IN句の生成
                String notInClause = uniquePrimaryKeys.stream()
                        .map(value -> "CAST(" + value.toString() + " AS TEXT)")
                        .collect(Collectors.joining(", ", "(", ")"));

                // notInClauseが空の場合のチェック
                if (notInClause.equals("()")) {
                    notInClause = "(NULL)"; // 空の場合にNULLを設定
                }
                // 重複チェック用のSQLクエリを生成
                String sql = "SELECT " + String.join(", ", keyColumnNames) + ", " + primaryKeyColumns.get(0) + " FROM " + tableName +
                        " WHERE CAST(" + primaryKeyColumns.get(0) + " AS TEXT) NOT IN " + notInClause + " AND (";

                // パラメータの設定
                List<Object> params = new ArrayList<>();

                // 複合キー条件を構築
                String crlf = System.lineSeparator(); // 改行コードの取得
                StringBuilder keyConditions = new StringBuilder(); // キー条件を格納するStringBuilder
                // keySetsのサイズ分ループ
                boolean isFirstCondition1 = true; // 最初の条件かどうかのフラグ
                for (int t = 0; t < keySets.size(); t++) {
                    Map<String, Object> keySet = keySets.get(t); // 現在のkeySetを取得
                    @SuppressWarnings("unchecked")
                    List<String> columns = (List<String>) keySet.get("column_names"); // column_namesを取得

                    // 最初の条件の場合、改行と OR を追加
                    if (!isFirstCondition1 && keyConditions.length() != 0) {
                        keyConditions.append(crlf);
                        keyConditions.append(" OR ");
                    }
                    isFirstCondition1 = false; // 最初の条件フラグをfalseに設定
                    boolean isFirstCondition2 = true; // 最初の条件かどうかのフラグ

                    // chunkのサイズ分ループ
                    for (int tt = 0; tt < chunk.size(); tt++) {
                        // index_nameが一致する場合
                        if (chunk.get(tt).get("index_name").equals(keySet.get("index_name"))) {
                            // 最初の条件の場合、改行と OR を追加
                            if (!isFirstCondition2) {
                                keyConditions.append(crlf);
                                keyConditions.append(" OR ");
                            }
                            isFirstCondition2 = false; // 最初の条件フラグをfalseに設定
                            keyConditions.append("("); // 条件式の開始

                            @SuppressWarnings("unchecked")
                            List<Object> data = (List<Object>) chunk.get(tt).get("data"); // データを取得

                            // columnsのサイズ分ループ
                            for (int j = 0; j < columns.size(); j++) {
                                // 最初の列でない場合、 AND を追加
                                if (j > 0) {
                                    keyConditions.append(" AND ");
                                }
                                // 条件式を追加
                                keyConditions.append("CAST(").append(columns.get(j)).append(" AS TEXT) = CAST(? AS TEXT)");
                                params.add(data.get(j)); // パラメータの設定
                            }
                            keyConditions.append(")"); // 条件式の終了
                        }
                    }
                    if (isFirstCondition2) {
                        // 余分に付与したORを取り除く
                        if (keyConditions.toString().endsWith(crlf + " OR ")) { // 末尾が「改行+OR」の場合だけ
                            keyConditions.delete(keyConditions.length() - (crlf.length() + " OR ".length()), keyConditions.length());
                        }
                    }

                }

                // 完成したSQLクエリを結合
                if (keyConditions.length() > 0) {
                    sql += keyConditions.toString() + ")";
                } else {
                    sql = sql.substring(0, sql.length() - 5) + ")";
                }

                // デバッグ用のSQLクエリを生成
                String debugSql = sql;
                for (Object param : params) {
                    debugSql = debugSql.replaceFirst("\\?", "'" + param.toString() + "'");
                }

                // デバッグログ出力
                // System.out.println("Generated SQL: " + sql);
                // System.out.println("Parameters: " + params.size() + " - " + params);
                // System.out.println("Debug SQL:\n" + formatSql(debugSql));
                // System.out.println("PrimaryKeyColumns: " + primaryKeyColumns);
                // System.out.println("KeySets: " + keySets);

                // クエリを実行してデータベースから結果を取得
                List<Map<String, Object>> dbResults = jdbcTemplate.queryForList(sql, params.toArray());

                // データベースから取得した重複チェック結果を生成
                for (Map<String, Object> dbResult : dbResults) {
                    // 現在処理中のデータチャンク内の各エントリーをループ
                    for (int k = 0; k < chunk.size(); k++) {
                        // 現在のデータを取得
                        @SuppressWarnings("unchecked")
                        List<Object> data = (List<Object>) chunk.get(k).get("data");
                        String indexName = (String) chunk.get(k).get("index_name");

                        // indexNameに一致するuniqueIndexesのエントリを検索
                        Map<String, Object> uniqueIndexEntry = uniqueIndexes.stream()
                                .filter(index -> indexName.equals(index.get("index_name")))
                                .findFirst()
                                .orElse(null);

                        if (uniqueIndexEntry == null) {
                            // 一致するuniqueIndexEntryが見つからない場合はスキップ
                            continue;
                        }

                        // PgArrayをList<String>に変換
                        PgArray pgArray = (PgArray) uniqueIndexEntry.get("column_names");
                        List<String> keyColumns;
                        try {
                            keyColumns = Arrays.asList((String[]) pgArray.getArray());
                        } catch (SQLException e) {
                            throw new IllegalStateException("PgArrayからカラム名を取得中にエラーが発生しました", e);
                        }

                        // DB結果から対応するカラムの値を抽出
                        List<Object> dbResultSubList = keyColumns.stream()
                                .map(column -> dbResult.get(column))
                                .collect(Collectors.toList());

                        // データベース結果と現在のデータが一致するかをチェック
                        if (dbResultSubList.equals(data)) {
                            // 現在のプライマリキーの値を取得
                            Integer primaryKey = (Integer) chunk.get(k).get("primary_key_data");

                            // 重複する既存データのプライマリキーを取得
                            Object existingPrimaryKey = dbResult.get(primaryKeyColumns.get(0));

                            // 重複データの詳細を生成
                            StringBuilder duplicateMessage = new StringBuilder();
                            for (int m = 0; m < keyColumns.size(); m++) {
                                if (m > 0) {
                                    duplicateMessage.append("、");
                                }
                                duplicateMessage.append(keyColumns.get(m)).append("=").append(data.get(m));
                            }

                            // エラーメッセージを生成し、errorsリストに追加
                            errors.add(String.format(
                                    "キー重複しています（重複データ: %s）（投入するデータのPK: %s、値: %s）（重複する既存データのPK: %s、値: %s）。",
                                    duplicateMessage.toString(),
                                    primaryKeyColumns.get(0),
                                    primaryKey,
                                    primaryKeyColumns.get(0),
                                    existingPrimaryKey));
                        }
                    }
                }

            }

            // バリデーション結果をレスポンスに設定
            JsonResponse json = new JsonResponse();
            if (errors.isEmpty()) {
                json.setReturn_code(HttpStatus.OK.value());
                json.setMsg("キー検証が成功しました。");
            } else {
                json.setReturn_code(HttpStatus.BAD_REQUEST.value());
                json.setMsg("キー検証に失敗しました。");
                json.Add("errors", new Gson().toJson(errors));
            }
            json.SetJsonResponse(res);

        } catch (Exception e) {
            JsonResponse json = Kai9Utils.handleException(e, res);
            json.setReturn_code(HttpStatus.INTERNAL_SERVER_ERROR.value());
            try {
                json.SetJsonResponse(res);
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }

    // フォーマット用メソッド(デバッグログの出力用)
    private String formatSql(String sql) {
        return sql.replaceAll("(?i)SELECT", "\nSELECT")
                .replaceAll("(?i)FROM", "\nFROM")
                .replaceAll("(?i)WHERE", "\nWHERE")
                .replaceAll("(?i)AND", "\nAND")
                .replaceAll("(?i)OR", "\nOR");
    }

}
