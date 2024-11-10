package kai9.tmpl.controller;

import java.io.File;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.json.JSONException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import kai9.com.common.TableExportExcel;
import kai9.com.common.TableImportExcel;
import kai9.libs.JsonResponse;
import kai9.libs.Kai9Utils;
import kai9.tmpl.dto.sql_Request;
import kai9.tmpl.model.AppEnv;
import kai9.tmpl.model.sql;
import kai9.tmpl.service.sql_Service;

/**
 * コントローラ
 */
@RestController
public class sql_Controller {

    @Autowired
    private sql_Service sql_service;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    @Qualifier("commonjdbc")
    JdbcTemplate jdbcTemplate_com;

    @Autowired
    NamedParameterJdbcTemplate namedJdbcTemplate;

    @Autowired
    private TableExportExcel tableExportExcel;

    @Autowired
    private TableImportExcel tableImportExcel;

    /**
     * 新規登録、更新
     */
    @PostMapping(value = { "/api/sql_create", "/api/sql_update", "/api/sql_delete" }, produces = "application/json;charset=utf-8")
    public void create(@RequestBody
    sql_Request sql_request, HttpServletResponse res, HttpServletRequest request) throws CloneNotSupportedException, IOException, JSONException {
        String crlf = System.lineSeparator();// 改行コード
        try {
            String URL = request.getRequestURI();
            String sql = "";
            sql sql_result = null;
            if (URL.toLowerCase().contains("sql_create")) {
                // 登録
                sql_result = sql_service.create(sql_request);
            } else {
                // 排他制御
                // 更新回数が異なる場合エラー
                sql = "select modify_count from sql_a where sql_pk = ?";
                String modify_count = jdbcTemplate.queryForObject(sql, String.class, sql_request.getSql_pk());
                if (!modify_count.equals(String.valueOf(sql_request.getModify_count()))) {
                    // JSON形式でレスポンスを返す
                    JsonResponse json = new JsonResponse();
                    json.setReturn_code(HttpStatus.CONFLICT.value());
                    json.setMsg("シングル表　【" + "Sql_pk=" + sql_request.getSql_pk() + "】　で排他エラー発生。ページリロード後に再登録して下さい。");
                    json.SetJsonResponse(res);
                    return;
                }

                if (URL.toLowerCase().contains("sql_update")) {
                    // 更新
                    sql_result = sql_service.update(sql_request);
                }

                if (URL.toLowerCase().contains("sql_delete")) {
                    // 削除フラグをON/OFF反転する
                    sql_result = sql_service.delete(sql_request);
                }
            }

            JsonResponse json = new JsonResponse();
            json.setReturn_code(HttpStatus.OK.value());
            json.setMsg("正常に登録しました");
            json.Add("sql_pk", String.valueOf(sql_result.getSql_pk()));
            json.Add("modify_count", String.valueOf(sql_result.getModify_count()));
            json.SetJsonResponse(res);
            return;

        } catch (Exception e) {
            String Msg = Kai9Utils.GetException(e);
            JsonResponse json = new JsonResponse();
            json.setReturn_code(HttpStatus.INTERNAL_SERVER_ERROR.value());
            json.setMsg("サーバー内でエラーが発生しました：" + crlf + Msg);
            json.SetJsonResponse(res);
        }
    }

    /**
     * 件数を返す
     */
    @PostMapping(value = "/api/sql_count", produces = "application/json;charset=utf-8")
    public String sql_count(String findstr, boolean isDelDataShow, HttpServletResponse res) throws CloneNotSupportedException, IOException {

        // 文字列カラムの曖昧検索
        String where = "";
        if (findstr != "") {
            findstr = findstr.replace("　", " ");
            String[] strs = findstr.split(" ");
            String str = "";
            for (int i = 0; i < strs.length; i++) {
                if (str != "") {
                    str = str + ",";
                }
                str = str + "'%" + strs[i] + "%'";
            }
            where = " and("
                    + " CAST(sql_pk AS TEXT) ~~* any(array[" + str + "])"
                    + " or"
                    + " sql_name ~~* any(array[" + str + "])"
                    + " or"
                    + " sql ~~* any(array[" + str + "])"
                    + " or"
                    + " memo ~~* any(array[" + str + "])"
                    + " or"
                    + " CAST(update_u_id AS TEXT) ~~* any(array[" + str + "])"
                    + " or"
                    + " TO_CHAR(update_date , 'YYYY/MM/DD HH24:MI:SS') ~~* any(array[" + str + "])"
                    + ")";
        }

        String Delflg = "";
        if (!isDelDataShow) {
            Delflg = "AND Delflg = false ";
        }

        String sql = "SELECT COUNT(*) FROM sql_a Where 0 = 0 " + Delflg + where;
        String all_count = jdbcTemplate.queryForObject(sql, String.class);

        // JSON形式でレスポンスを返す
        res.setContentType("application/json");
        res.setCharacterEncoding("UTF-8");
        String str = "{\"return_code\": " + HttpStatus.OK.value() + ","
                + " \"count\": " + all_count
                + "}";
        return str;
    }

    /**
     * ページネーション検索
     */
    @PostMapping(value = "/api/sql_find", produces = "application/json;charset=utf-8")
    @ResponseBody
    public String sql_find(Integer limit, Integer offset, String findstr, boolean isDelDataShow) throws CloneNotSupportedException, IOException {

        String Delflg = "";

        List<sql> sql_list = null;
        if (findstr == "") {
            if (!isDelDataShow) {
                Delflg = " Where sql_a.Delflg = false";
            }
            String sql = "SELECT sql_a.* "
                    + " FROM sql_a "
                    + Delflg
                    + " order by sql_pk asc limit ? offset ?";
            RowMapper<sql> rowMapper = new BeanPropertyRowMapper<sql>(sql.class);
            sql_list = jdbcTemplate.query(sql, rowMapper, limit, offset);
        } else {
            findstr = findstr.replace("　", " ");
            String[] strs = findstr.split(" ");
            String str = "";
            for (int i = 0; i < strs.length; i++) {
                if (str != "") {
                    str = str + ",";
                }
                str = str + "'%" + strs[i] + "%'";
            }

            if (!isDelDataShow) {
                Delflg = " sql_a.Delflg = false AND ";
            }

            // https://loglog.xyz/programming/jdbctemplate_in
            // https://qiita.com/naohide_a/items/3c78837ac7a1e05c6134
            // str を配列渡しすると、カンマが入るのでインジェクションチェックでNGになりSQL発行が出来ない。スペース区切りで%を付与するよう加工しているので、危険な構文は書けないと判断
            String sql = "SELECT sql_a.* "
                    + " FROM sql_a  "
                    + " where "
                    + Delflg
                    + " ("
                    + " CAST(sql_a.sql_pk AS TEXT) ~~* any(array[" + str + "])"
                    + " or"
                    + " sql_a.sql_name ~~* any(array[" + str + "])"
                    + " or"
                    + " sql_a.sql ~~* any(array[" + str + "])"
                    + " or"
                    + " sql_a.memo ~~* any(array[" + str + "])"
                    + " or"
                    + " CAST(sql_a.update_u_id AS TEXT) ~~* any(array[" + str + "])"
                    + " or"
                    + " TO_CHAR(sql_a.update_date, 'YYYY/MM/DD HH24:MI:SS') ~~* any(array[" + str + "])"
                    + ")"
                    + "order by sql_pk limit :limit offset :offset ";
            MapSqlParameterSource Param = new MapSqlParameterSource()
                    .addValue("limit", limit)
                    .addValue("offset", offset);

            RowMapper<sql> rowMapper = new BeanPropertyRowMapper<sql>(sql.class);
            // https://loglog.xyz/programming/jdbctemplate_in
            sql_list = namedJdbcTemplate.query(sql, Param, rowMapper);
        }

        // 更新者の反映----------------------------------------------------------------
        Set<Integer> updateUIds = new HashSet<>();
        for (sql data : sql_list) {
            updateUIds.add(data.getUpdate_u_id());
        }
        if (updateUIds.size() != 0) {
            Map<String, Object> paramMap2 = new HashMap<>();
            paramMap2.put("keys", updateUIds);
            // SQLの発行
            String sql4 = "SELECT user_id, sei, mei FROM m_user_a WHERE user_id = ANY(ARRAY[ :keys ])";
            NamedParameterJdbcTemplate namedTemplate2 = new NamedParameterJdbcTemplate(jdbcTemplate_com);
            List<Map<String, Object>> userNames = namedTemplate2.queryForList(sql4, paramMap2);
            // update_userにuser_nameを反映
            for (sql data : sql_list) {
                for (Map<String, Object> row : userNames) {
                    Integer userId = (Integer) row.get("user_id");
                    if (data.getUpdate_u_id().equals(userId)) {
                        String userName = (String) row.get("sei") + " " + (String) row.get("mei");
                        data.setUpdate_user(userName);
                        break;
                    }
                }
            }
        }

        // サーバーデータが取得できなかった場合は、null値を返す
        if (sql_list == null || sql_list.size() == 0) {
            return null;
        }
        // 取得したサーバーデータをJSON文字列に変換し返却
        return getJsonData(sql_list);
    }

    /**
     * 履歴を取得する
     */
    @PostMapping(value = "/api/sql_history_find", produces = "application/json;charset=utf-8")
    public String sql_history_find(Integer sql_pk, HttpServletResponse res) throws CloneNotSupportedException, IOException {
        String sql = "select sql_b.*"
                + " FROM sql_b "

                + " where sql_pk = :sql_pk order by sql_b.modify_count desc";
        RowMapper<sql> rowMapper = new BeanPropertyRowMapper<sql>(sql.class);
        // https://loglog.xyz/programming/jdbctemplate_in
        MapSqlParameterSource Param = new MapSqlParameterSource()
                .addValue("sql_pk", sql_pk);
        List<sql> sql_list = namedJdbcTemplate.query(sql, Param, rowMapper);

        // データ取得できなかった場合は、null値を返す
        if (sql_list == null || sql_list.size() == 0) {
            return null;
        }
        // 取得データをJSON文字列に変換し返却
        return getJsonData(sql_list);
    }

    /**
     * 全件取得する
     */
    @PostMapping(value = "/api/sql_find_all", produces = "application/json;charset=utf-8")
    public String find_all(HttpServletResponse res) throws CloneNotSupportedException, IOException {
        List<sql> sql_list = sql_service.searchAll();
        // サーバーデータが取得できなかった場合は、null値を返す
        if (sql_list == null || sql_list.size() == 0) {
            return null;
        }
        // 取得したサーバーデータをJSON文字列に変換し返却
        return getJsonData(sql_list);
    }

    /**
     * 引数のオブジェクトをJSON文字列に変換する
     * 
     * @param data オブジェクトのデータ
     * @return 変換後JSON文字列
     */
    private String getJsonData(Object data) {
        String retVal = null;
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            retVal = objectMapper.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            System.err.println(e);
        }
        return retVal;
    }

    /**
     * エクセル：エクスポート
     */
    @PostMapping(value = "/api/sql_ExportExcel", produces = "application/json;charset=utf-8", consumes = "application/x-www-form-urlencoded")
    public ResponseEntity<Resource> sql_ExportExcel(@RequestParam
    Map<String, String> requestData, HttpServletResponse res, HttpServletRequest request) throws CloneNotSupportedException, IOException, JSONException {

        try {
            // 出力対象のテーブル名を取得
            String tableName = requestData.get("tableName");
            int progress_status_id = Integer.valueOf(requestData.get("progress_status_id"));

            // 環境設定をロード
            String sql = "select * from app_env_a";
            RowMapper<AppEnv> rowMapper = new BeanPropertyRowMapper<>(AppEnv.class);
            List<AppEnv> mEnvList = jdbcTemplate.query(sql, rowMapper);
            AppEnv appEnv = null;
            if (!mEnvList.isEmpty()) {
                appEnv = mEnvList.get(0);
            }
            // 一時フォルダを確保
            String tmpFilename = "";
            if (appEnv == null || appEnv.getDir_tmp().isEmpty()) {
                // アプリケーションが使用する一時フォルダを指定
                File tempDir = new File(System.getProperty("java.io.tmpdir")); // OSが勝手に消してくれる場所
                File myTempDir = new File(tempDir, "kai9tmpl_temp");
                myTempDir.mkdirs();
                tmpFilename = myTempDir.getAbsolutePath() + File.separator + tableName + "_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS")) + ".xlsx";
            } else {
                // 環境設定から一時フォルダを取得
                if (appEnv.getDir_tmp().endsWith(File.separator)) {
                    tmpFilename = appEnv.getDir_tmp() + tableName + ".xlsx";
                } else {
                    tmpFilename = appEnv.getDir_tmp() + File.separator + tableName + "_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS")) + ".xlsx";
                }
            }

            // エクセル作成
            tableExportExcel.exec(tableName, tmpFilename, progress_status_id);

            // 生成したファイルをAPIの戻りにバイナリ変換して返す
            File file = new File(tmpFilename);
            byte[] fileContent = Files.readAllBytes(file.toPath());
            ByteArrayResource resource = new ByteArrayResource(fileContent);

            HttpHeaders headers = new HttpHeaders();
            headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + tmpFilename);
            headers.add("Cache-Control", "no-cache, no-store, must-revalidate");
            headers.add("Pragma", "no-cache");
            headers.add("Expires", "0");

            return ResponseEntity
                    .ok()
                    .headers(headers)
                    .contentLength(file.length())
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(resource);

        } catch (Exception e) {
            return Kai9Utils.handleExceptionAsResponseEntity(e, res, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * エクセル：インポート
     */
    @PostMapping(value = { "/api/sql_ImportExcel" }, produces = "application/json;charset=utf-8")
    public ResponseEntity<Resource> sql_ImportExcel(
            @RequestParam
            Map<String, String> requestData,
            @RequestPart(value = "file_excel", required = false)
            MultipartFile file_s_excel,
            HttpServletResponse res, HttpServletRequest request) throws CloneNotSupportedException, IOException, JSONException {

        String returnMsg = "";
        String tmpFilename = "";
        String originalFilename = "";
        try {
            // 取込対象のテーブル名を取得
            String tableName = requestData.get("tableName");
            int progress_status_id = Integer.valueOf(requestData.get("progress_status_id"));

            if (file_s_excel != null) {

                // 元ファイル名を確保
                originalFilename = file_s_excel.getOriginalFilename();

                // 環境設定をロード
                String sql = "select * from app_env_a";
                RowMapper<AppEnv> rowMapper = new BeanPropertyRowMapper<>(AppEnv.class);
                List<AppEnv> mEnvList = jdbcTemplate.query(sql, rowMapper);
                AppEnv appEnv = null;
                if (!mEnvList.isEmpty()) {
                    appEnv = mEnvList.get(0);
                }
                // 一時フォルダを確保
                if (appEnv == null || appEnv.getDir_tmp().isEmpty()) {
                    // アプリケーションが使用する一時フォルダを指定
                    File tempDir = new File(System.getProperty("java.io.tmpdir")); // OSが勝手に消してくれる場所
                    File myTempDir = new File(tempDir, "kai9tmpl_temp");
                    myTempDir.mkdirs();
                    tmpFilename = myTempDir.getAbsolutePath() + File.separator + tableName + "_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS")) + ".xlsx";

                } else {
                    // 環境設定から一時フォルダを取得
                    if (appEnv.getDir_tmp().endsWith(File.separator)) {
                        tmpFilename = appEnv.getDir_tmp() + tableName + ".xlsx";
                    } else {
                        tmpFilename = appEnv.getDir_tmp() + File.separator + tableName + "_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS")) + ".xlsx";
                    }
                }

                // 保存
                File destFile = new File(tmpFilename);
                file_s_excel.transferTo(destFile);

                // エクセル取込
                returnMsg = tableImportExcel.exec(tableName, tmpFilename, progress_status_id);

                // 生成したファイルをAPIの戻りにバイナリ変換して返す
                File file = new File(tmpFilename);
                byte[] fileContent = Files.readAllBytes(file.toPath());
                ByteArrayResource resource = new ByteArrayResource(fileContent);

                HttpHeaders headers = new HttpHeaders();
                headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + tmpFilename);
                headers.add("Cache-Control", "no-cache, no-store, must-revalidate");
                headers.add("Pragma", "no-cache");
                headers.add("Expires", "0");
                headers.add("msg", URLEncoder.encode(returnMsg, StandardCharsets.UTF_8.toString()));
                headers.add("originalfilename", URLEncoder.encode(originalFilename, StandardCharsets.UTF_8.toString()));

                return ResponseEntity
                        .ok()
                        .headers(headers)
                        .contentLength(file.length())
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .body(resource);

            }

            return null;

        } catch (Exception e) {
            return Kai9Utils.handleExceptionAsResponseEntity(e, res, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

}
