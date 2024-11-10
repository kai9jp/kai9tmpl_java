package kai9.tmpl.controller;

import java.io.File;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
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
import com.fasterxml.jackson.core.type.TypeReference;
import kai9.com.common.TableExportExcel;
import kai9.com.common.TableImportExcel;
import kai9.libs.JsonResponse;
import kai9.libs.Kai9Utils;
import kai9.tmpl.dto.single_table_Request;
import kai9.tmpl.model.AppEnv;
//【制御:開始】relation型
import kai9.tmpl.model.related_table;
//【制御:終了】relation型
import kai9.tmpl.model.single_table;
import kai9.tmpl.service.single_table_Service;
//【制御:型】BLOB

/**
 * コントローラ
 */
@RestController
public class single_table_Controller {

    @Autowired
    private single_table_Service single_table_service;

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
    @PostMapping(value = { "/api/single_table_create", "/api/single_table_update",
            "/api/single_table_delete" }, produces = "application/json;charset=utf-8")
    public void create(@RequestBody
    single_table_Request single_table_request, HttpServletResponse res,
            HttpServletRequest request) throws CloneNotSupportedException, IOException, JSONException {
        String crlf = System.lineSeparator();// 改行コード
        try {
            String URL = request.getRequestURI();
            String sql = "";
            single_table single_table_result = null;
            if (URL.toLowerCase().contains("single_table_create")) {
                // 登録
                single_table_result = single_table_service.create(single_table_request);
            } else {
                // 排他制御
                // 更新回数が異なる場合エラー
                sql = "select modify_count from single_table_a where s_pk = ?";
                String modify_count = jdbcTemplate.queryForObject(sql, String.class, single_table_request.getS_pk());
                if (!modify_count.equals(String.valueOf(single_table_request.getModify_count()))) {
                    // JSON形式でレスポンスを返す
                    JsonResponse json = new JsonResponse();
                    json.setReturn_code(HttpStatus.CONFLICT.value());
                    json.setMsg("シングル表　【" + "S_pk=" + single_table_request.getS_pk() + "】　で排他エラー発生。ページリロード後に再登録して下さい。");
                    json.SetJsonResponse(res);
                    return;
                }

                if (URL.toLowerCase().contains("single_table_update")) {
                    // 更新
                    single_table_result = single_table_service.update(single_table_request);
                }

                if (URL.toLowerCase().contains("single_table_delete")) {
                    // 削除フラグをON/OFF反転する
                    single_table_result = single_table_service.delete(single_table_request);
                }
            }

            JsonResponse json = new JsonResponse();
            json.setReturn_code(HttpStatus.OK.value());
            json.setMsg("正常に登録しました");
            // 【制御:開始】カラム:新規登録
            json.Add("s_pk", String.valueOf(single_table_result.getS_pk()));
            json.Add("modify_count", String.valueOf(single_table_result.getModify_count()));
            // 【制御:終了】カラム:新規登録
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
    @PostMapping(value = "/api/single_table_count", produces = "application/json;charset=utf-8")
    public String single_table_count(String findstr, boolean isDelDataShow, HttpServletResponse res)
            throws CloneNotSupportedException, IOException {

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
                    // 【制御:開始】カラム:件数を返す
                    + " CAST(single_table_a.s_pk AS TEXT) ~~* any(array[" + str + "])"
                    + " or"
                    + " single_table_a.natural_key1 ~~* any(array[" + str + "])"
                    + " or"
                    + " single_table_a.natural_key21 ~~* any(array[" + str + "])"
                    + " or"
                    + " single_table_a.natural_key22_33 ~~* any(array[" + str + "])"
                    + " or"
                    + " single_table_a.natural_key31 ~~* any(array[" + str + "])"
                    + " or"
                    + " single_table_a.natural_key32 ~~* any(array[" + str + "])"
                    + " or"
                    + " single_table_a.fullwidth_limited ~~* any(array[" + str + "])"
                    + " or"
                    + " single_table_a.halfwidth_limited ~~* any(array[" + str + "])"
                    + " or"
                    + " single_table_a.halfwidth_alphabetical_limited ~~* any(array[" + str + "])"
                    + " or"
                    + " single_table_a.halfwidth_number_limited ~~* any(array[" + str + "])"
                    + " or"
                    + " single_table_a.halfwidth_symbol_limited ~~* any(array[" + str + "])"
                    + " or"
                    + " single_table_a.halfwidth_kana_limited ~~* any(array[" + str + "])"
                    + " or"
                    + " single_table_a.fullwidth_kana_limited ~~* any(array[" + str + "])"
                    + " or"
                    + " CAST(single_table_a.number_limited AS TEXT) ~~* any(array[" + str + "])"
                    + " or"
                    + " CAST(single_table_a.small_number_point AS TEXT) ~~* any(array[" + str + "])"
                    + " or"
                    + " CAST(single_table_a.number_real AS TEXT) ~~* any(array[" + str + "])"
                    + " or"
                    + " CAST(single_table_a.number_double AS TEXT) ~~* any(array[" + str + "])"
                    + " or"
                    + " single_table_a.normal_string ~~* any(array[" + str + "])"
                    + " or"
                    + " single_table_a.phone_number ~~* any(array[" + str + "])"
                    + " or"
                    + " TO_CHAR(single_table_a.date , 'YYYY/MM/DD') ~~* any(array[" + str + "])"
                    + " or"
                    + " TO_CHAR(single_table_a.datetime , 'YYYY/MM/DD HH24:MI:SS') ~~* any(array[" + str + "])"
                    + " or"
                    + " single_table_a.email_address ~~* any(array[" + str + "])"
                    + " or"
                    + " single_table_a.url ~~* any(array[" + str + "])"
                    + " or"
                    + " single_table_a.regexp ~~* any(array[" + str + "])"
                    + " or"
                    + " single_table_a.memo ~~* any(array[" + str + "])"
                    + " or"
                    + " CAST(single_table_a.related_pk AS TEXT) ~~* any(array[" + str + "])"
                    + " or"
                    + " CAST(single_table_a.update_u_id AS TEXT) ~~* any(array[" + str + "])"
                    + " or"
                    + " TO_CHAR(single_table_a.update_date , 'YYYY/MM/DD HH24:MI:SS') ~~* any(array[" + str + "])"
                    // 【制御:終了】カラム:件数を返す
                    // 【制御:開始】relationSQL⑧
                    + " or"
                    + " related_table_a.related_data ~~* any(array[" + str + "])"
                    // 【制御:終了】relationSQL⑧
                    + ")";
        }

        String Delflg = "";
        if (!isDelDataShow) {
            Delflg = "AND single_table_a.Delflg = false ";
        }

        String sql = "SELECT COUNT(*) FROM single_table_a "
                // 【制御:開始】relationSQL⑩
                + " left join related_table_a "
                + " on related_table_a.related_pk = single_table_a.related_pk "
                // 【制御:終了】relationSQL⑩
                + " Where 0 = 0 " + Delflg + where;

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
    @PostMapping(value = "/api/single_table_find", produces = "application/json;charset=utf-8")
    @ResponseBody
    public String single_table_find(Integer limit, Integer offset, String findstr, boolean isDelDataShow)
            throws CloneNotSupportedException, IOException {

        String Delflg = "";

        List<single_table> single_table_list = null;
        if (findstr == "") {
            if (!isDelDataShow) {
                Delflg = " Where single_table_a.Delflg = false";
            }
            String sql = "SELECT single_table_a.* "
                    // 【制御:開始】relationSQL①
                    + " ,CAST(related_table_a.related_pk as TEXT)||':'||CAST(related_table_a.related_data as TEXT) as related_pk__related_data "
                    // 【制御:終了】relationSQL①
                    + " FROM single_table_a "
                    // 【制御:開始】relationSQL②
                    + " left join related_table_a"
                    + " on related_table_a.related_pk = single_table_a.related_pk"
                    // 【制御:終了】relationSQL②
                    + Delflg
                    + " order by s_pk asc limit ? offset ?";
            RowMapper<single_table> rowMapper = new BeanPropertyRowMapper<single_table>(single_table.class);
            single_table_list = jdbcTemplate.query(sql, rowMapper, limit, offset);
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
                Delflg = " single_table_a.Delflg = false AND ";
            }

            // https://loglog.xyz/programming/jdbctemplate_in
            // https://qiita.com/naohide_a/items/3c78837ac7a1e05c6134
            // str を配列渡しすると、カンマが入るのでインジェクションチェックでNGになりSQL発行が出来ない。スペース区切りで%を付与するよう加工しているので、危険な構文は書けないと判断
            String sql = "SELECT single_table_a.* "
                    // 【制御:開始】relationSQL③
                    + " ,CAST(related_table_a.related_pk as TEXT)||':'||CAST(related_table_a.related_data as TEXT) as related_pk__related_data "
                    // 【制御:終了】relationSQL③
                    + " FROM single_table_a  "
                    // 【制御:開始】relationSQL④
                    + " left join related_table_a"
                    + " on related_table_a.related_pk = single_table_a.related_pk"
                    // 【制御:終了】relationSQL④
                    + " where "
                    + Delflg
                    + " ("
                    // 【制御:開始】カラム:ページネーション検索
                    + " CAST(single_table_a.s_pk AS TEXT) ~~* any(array[" + str + "])"
                    + " or"
                    + " single_table_a.natural_key1 ~~* any(array[" + str + "])"
                    + " or"
                    + " single_table_a.natural_key21 ~~* any(array[" + str + "])"
                    + " or"
                    + " single_table_a.natural_key22_33 ~~* any(array[" + str + "])"
                    + " or"
                    + " single_table_a.natural_key31 ~~* any(array[" + str + "])"
                    + " or"
                    + " single_table_a.natural_key32 ~~* any(array[" + str + "])"
                    + " or"
                    + " single_table_a.fullwidth_limited ~~* any(array[" + str + "])"
                    + " or"
                    + " single_table_a.halfwidth_limited ~~* any(array[" + str + "])"
                    + " or"
                    + " single_table_a.halfwidth_alphabetical_limited ~~* any(array[" + str + "])"
                    + " or"
                    + " single_table_a.halfwidth_number_limited ~~* any(array[" + str + "])"
                    + " or"
                    + " single_table_a.halfwidth_symbol_limited ~~* any(array[" + str + "])"
                    + " or"
                    + " single_table_a.halfwidth_kana_limited ~~* any(array[" + str + "])"
                    + " or"
                    + " single_table_a.fullwidth_kana_limited ~~* any(array[" + str + "])"
                    + " or"
                    + " CAST(single_table_a.number_limited AS TEXT) ~~* any(array[" + str + "])"
                    + " or"
                    + " CAST(single_table_a.small_number_point AS TEXT) ~~* any(array[" + str + "])"
                    + " or"
                    + " CAST(single_table_a.number_real AS TEXT) ~~* any(array[" + str + "])"
                    + " or"
                    + " CAST(single_table_a.number_double AS TEXT) ~~* any(array[" + str + "])"
                    + " or"
                    + " single_table_a.normal_string ~~* any(array[" + str + "])"
                    + " or"
                    + " single_table_a.phone_number ~~* any(array[" + str + "])"
                    + " or"
                    + " TO_CHAR(single_table_a.date, 'YYYY/MM/DD') ~~* any(array[" + str + "])"
                    + " or"
                    + " TO_CHAR(single_table_a.datetime, 'YYYY/MM/DD HH24:MI:SS') ~~* any(array[" + str + "])"
                    + " or"
                    + " single_table_a.email_address ~~* any(array[" + str + "])"
                    + " or"
                    + " single_table_a.url ~~* any(array[" + str + "])"
                    + " or"
                    + " single_table_a.regexp ~~* any(array[" + str + "])"
                    + " or"
                    + " single_table_a.memo ~~* any(array[" + str + "])"
                    + " or"
                    + " CAST(single_table_a.related_pk AS TEXT) ~~* any(array[" + str + "])"
                    + " or"
                    + " CAST(single_table_a.update_u_id AS TEXT) ~~* any(array[" + str + "])"
                    + " or"
                    + " TO_CHAR(single_table_a.update_date, 'YYYY/MM/DD HH24:MI:SS') ~~* any(array[" + str + "])"
                    // 【制御:終了】カラム:ページネーション検索
                    // 【制御:開始】relationSQL⑨
                    + " or"
                    + " related_table_a.related_data ~~* any(array[" + str + "])"
                    // 【制御:終了】relationSQL⑨
                    + ")"
                    + "order by s_pk limit :limit offset :offset ";
            MapSqlParameterSource Param = new MapSqlParameterSource()
                    .addValue("limit", limit)
                    .addValue("offset", offset);

            RowMapper<single_table> rowMapper = new BeanPropertyRowMapper<single_table>(single_table.class);
            // https://loglog.xyz/programming/jdbctemplate_in
            single_table_list = namedJdbcTemplate.query(sql, Param, rowMapper);
        }

        // 更新者の反映----------------------------------------------------------------
        Set<Integer> updateUIds = new HashSet<>();
        for (single_table data : single_table_list) {
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
            for (single_table data : single_table_list) {
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
        if (single_table_list == null || single_table_list.size() == 0) {
            return null;
        }
        // 取得したサーバーデータをJSON文字列に変換し返却
        return getJsonData(single_table_list);
    }

    /**
     * 履歴を取得する
     */
    @PostMapping(value = "/api/single_table_history_find", produces = "application/json;charset=utf-8")
    public String single_table_history_find(Integer s_pk, HttpServletResponse res)
            throws CloneNotSupportedException, IOException {
        String sql = "select single_table_b.*"
                // 【制御:開始】relationSQL⑤
                + ",CAST(related_table_b.related_pk as TEXT)||':'||CAST(related_table_b.related_data as TEXT) as related_pk__related_data "
                // 【制御:終了】relationSQL⑤
                + " FROM single_table_b "
                // 【制御:開始】relationSQL⑥
                + " left join related_table_b "
                + " on related_table_b.related_pk = single_table_b.related_pk "
                // 【制御:終了】relationSQL⑥

                // 【制御:開始】relationSQL⑦
                + "  and related_table_b.update_date = ( "
                + "    SELECT MAX(update_date) FROM related_table_b  "
                + "    WHERE related_table_b.related_pk = single_table_b.related_pk  "
                + "    and related_table_b.update_date < single_table_b.update_date   "
                + " )  "
                // 【制御:終了】relationSQL⑦

                + " where s_pk = :s_pk order by single_table_b.modify_count desc";
        RowMapper<single_table> rowMapper = new BeanPropertyRowMapper<single_table>(single_table.class);
        // https://loglog.xyz/programming/jdbctemplate_in
        MapSqlParameterSource Param = new MapSqlParameterSource()
                // 【制御:開始】カラム:履歴を取得する
                .addValue("s_pk", s_pk);
        // 【制御:終了】カラム:履歴を取得する
        List<single_table> single_table_list = namedJdbcTemplate.query(sql, Param, rowMapper);

        // データ取得できなかった場合は、null値を返す
        if (single_table_list == null || single_table_list.size() == 0) {
            return null;
        }
        // 取得データをJSON文字列に変換し返却
        return getJsonData(single_table_list);
    }

    /**
     * 全件取得する
     */
    @PostMapping(value = "/api/single_table_find_all", produces = "application/json;charset=utf-8")
    public String find_all(HttpServletResponse res) throws CloneNotSupportedException, IOException {
        List<single_table> single_table_list = single_table_service.searchAll();
        // サーバーデータが取得できなかった場合は、null値を返す
        if (single_table_list == null || single_table_list.size() == 0) {
            return null;
        }
        // 取得したサーバーデータをJSON文字列に変換し返却
        return getJsonData(single_table_list);
    }

    // 【制御:開始】relation
    /**
     * 一覧を返す(選択リストボックス用)
     */
    @PostMapping(value = "/api/single_table_related_table_related_pk_related_data_find_all_selectInput", produces = "application/json;charset=utf-8")
    public void single_table_related_table_related_pk_related_data_find_all_selectInput(HttpServletResponse res)
            throws CloneNotSupportedException, IOException, JSONException {
        String sql = "select * from related_table_a where delflg = false order by related_pk";

        RowMapper<related_table> rowMapper = new BeanPropertyRowMapper<related_table>(related_table.class);
        List<related_table> related_table_list = namedJdbcTemplate.query(sql, rowMapper);
        List<String> results = new ArrayList<String>();
        for (related_table related_table : related_table_list) {
            results.add(related_table.getRelated_pk() + ":" + related_table.getRelated_data());
        }

        // JSON形式でレスポンスを返す
        JsonResponse json = new JsonResponse();
        json.setReturn_code(HttpStatus.OK.value());
        json.AddArray("results", getJsonData(results));
        json.SetJsonResponse(res);
        return;
    }

    // 【制御:終了】relation

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
    @PostMapping(value = "/api/single_table_ExportExcel", produces = "application/json;charset=utf-8", consumes = "application/json")
    public ResponseEntity<Resource> single_table_ExportExcel(
            @RequestBody
            Map<String, Object> requestData,
            HttpServletResponse res, HttpServletRequest request)
            throws CloneNotSupportedException, IOException, JSONException {

        try {
            // 出力対象のテーブル名を取得
            String tableName = (String) requestData.get("tableName");
            int progress_status_id = Integer.parseInt(requestData.get("progress_status_id").toString());

            // リレーション
            ObjectMapper objectMapper = new ObjectMapper();
            List<String> relations = objectMapper.convertValue(requestData.get("relations"), new TypeReference<List<String>>() {
            });
            StringBuilder relationsSB = new StringBuilder();
            for (String relation : relations) {
                relationsSB.append(relation).append(",");
            }

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
                tmpFilename = myTempDir.getAbsolutePath() + File.separator + tableName + "_"
                        + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS")) + ".xlsx";
            } else {
                // 環境設定から一時フォルダを取得
                if (appEnv.getDir_tmp().endsWith(File.separator)) {
                    tmpFilename = appEnv.getDir_tmp() + tableName + ".xlsx";
                } else {
                    tmpFilename = appEnv.getDir_tmp() + File.separator + tableName + "_"
                            + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS")) + ".xlsx";
                }
            }

            // エクセル作成
            tableExportExcel.exec(tableName, tmpFilename, progress_status_id, relationsSB);

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
    @PostMapping(value = { "/api/single_table_ImportExcel" }, produces = "application/json;charset=utf-8")
    public ResponseEntity<Resource> single_table_ImportExcel(
            @RequestParam
            Map<String, String> requestData,
            @RequestPart(value = "file_excel", required = false)
            MultipartFile file_s_excel,
            HttpServletResponse res, HttpServletRequest request)
            throws CloneNotSupportedException, IOException, JSONException {

        String returnMsg = "";
        String tmpFilename = "";
        String originalFilename = "";
        try {
            // 取込対象のテーブル名を取得
            String tableName = requestData.get("tableName");
            int progress_status_id = Integer.valueOf(requestData.get("progress_status_id"));

            // リレーション
            String relationsJson = (String) requestData.get("relations");
            List<String> relations = new ArrayList<>(); // デフォルトの空リスト
            ObjectMapper objectMapper = new ObjectMapper();
            if (relationsJson != null && !relationsJson.isEmpty()) {
                relations = objectMapper.readValue(relationsJson, new TypeReference<List<String>>() {
                });
            }
            StringBuilder relationsSB = new StringBuilder();
            for (String relation : relations) {
                relationsSB.append(relation).append(",");
            }

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
                    tmpFilename = myTempDir.getAbsolutePath() + File.separator + tableName + "_"
                            + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS")) + ".xlsx";

                } else {
                    // 環境設定から一時フォルダを取得
                    if (appEnv.getDir_tmp().endsWith(File.separator)) {
                        tmpFilename = appEnv.getDir_tmp() + tableName + ".xlsx";
                    } else {
                        tmpFilename = appEnv.getDir_tmp() + File.separator + tableName + "_"
                                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"))
                                + ".xlsx";
                    }
                }

                // 保存
                File destFile = new File(tmpFilename);
                file_s_excel.transferTo(destFile);

                // エクセル取込
                returnMsg = tableImportExcel.exec(tableName, tmpFilename, progress_status_id, relationsSB);

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
