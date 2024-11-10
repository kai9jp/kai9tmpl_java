package kai9.tmpl.controller;

import java.io.IOException;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.json.JSONException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import kai9.libs.JsonResponse;
import kai9.libs.Kai9Utils;
import kai9.tmpl.dto.AppEnv_Request;
import kai9.tmpl.model.AppEnv;
import kai9.tmpl.service.AppEnv_Service;

/**
 * 環境設定:コントローラ
 */
@RestController
public class AppEnv_Controller {

    @Autowired
    private AppEnv_Service app_env_service;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    NamedParameterJdbcTemplate namedJdbcTemplate;

    /**
     * CUD操作
     */
    @PostMapping(value = { "/api/app_env_update" }, produces = "application/json;charset=utf-8")
    public void create(@RequestBody
    AppEnv_Request app_env, HttpServletResponse res, HttpServletRequest request) throws CloneNotSupportedException, IOException, JSONException {
        try {
            AppEnv app_env_result = null;
            // 排他制御
            // 更新回数が異なる場合エラー
            String sql = "select modify_count from app_env_a";
            List<String> results = jdbcTemplate.query(sql, (rs, rowNum) -> rs.getString("modify_count"));
            if (!results.isEmpty()) {
                String modify_count = results.get(0);
                // レコードが存在する場合
                if (!modify_count.equals(String.valueOf(app_env.getModify_count()))) {
                    // JSON形式でレスポンスを返す
                    JsonResponse json = new JsonResponse();
                    json.setReturn_code(HttpStatus.CONFLICT.value());
                    json.setMsg("【単一レコード】　で排他エラー発生。ページリロード後に再登録して下さい。");
                    json.SetJsonResponse(res);
                    return;
                }
            } else {
                // レコードが存在しない場合、ノーチェック
            }

            // delete & insert
            app_env_result = app_env_service.create(app_env);

            JsonResponse json = new JsonResponse();
            json.setReturn_code(HttpStatus.OK.value());
            json.setMsg("正常に登録しました");
            json.Add("modify_count", String.valueOf(app_env_result.getModify_count()));
            json.SetJsonResponse(res);
            return;

        } catch (Exception e) {
            Kai9Utils.handleException(e, res);
        }
    }

    /**
     * 全量検索
     */
    @PostMapping(value = "/api/app_env_find", produces = "application/json;charset=utf-8")
    @ResponseBody
    public void app_env_find(HttpServletResponse res, HttpServletRequest request) throws CloneNotSupportedException, IOException {
        try {
            String sql = "select * from app_env_a";
            RowMapper<AppEnv> rowMapper = new BeanPropertyRowMapper<AppEnv>(AppEnv.class);
            List<AppEnv> app_env_list = namedJdbcTemplate.query(sql, rowMapper);

            // データが取得できなかった場合は、null値を返す
            if (app_env_list == null || app_env_list.size() == 0) {
                return;
            }
            // 取得データをJSON文字列に変換し返却
            // JSON形式でレスポンスを返す
            JsonResponse json = new JsonResponse();
            json.setReturn_code(HttpStatus.OK.value());
            json.setData(Kai9Utils.getJsonData(app_env_list));
            json.SetJsonResponse(res);
        } catch (Exception e) {
            Kai9Utils.handleException(e, res);
        }
    }

    /**
     * 履歴検索
     */
    @PostMapping(value = "/api/app_env_history_find", produces = "application/json;charset=utf-8")
    public void app_env_history_find(HttpServletResponse res) throws CloneNotSupportedException, IOException {
        String sql = "select * from app_env_b order by modify_count desc";
        RowMapper<AppEnv> rowMapper = new BeanPropertyRowMapper<AppEnv>(AppEnv.class);
        MapSqlParameterSource Param = new MapSqlParameterSource();
        List<AppEnv> app_env_list = namedJdbcTemplate.query(sql, Param, rowMapper);

        // データが取得できなかった場合は、null値を返す
        if (app_env_list == null || app_env_list.size() == 0) {
            return;
        }
        // JSON形式でレスポンスを返す
        JsonResponse json = new JsonResponse();
        json.setReturn_code(HttpStatus.OK.value());
        json.setData(Kai9Utils.getJsonData((Object) app_env_list));
        json.SetJsonResponse(res);
        return;
    }

}
