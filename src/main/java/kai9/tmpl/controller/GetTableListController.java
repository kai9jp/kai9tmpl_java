package kai9.tmpl.controller;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletResponse;

import org.json.JSONException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;

import kai9.libs.JsonResponse;
import kai9.libs.Kai9Utils;

@RestController
public class GetTableListController {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    NamedParameterJdbcTemplate namedJdbcTemplate;

    @PostMapping("/api/get_table_list")
    public void getTableList(@RequestBody
    Map<String, String> request, HttpServletResponse res) throws IOException {
        String crlf = System.lineSeparator();
        try {
            // リクエストからSQL文を取得
            String sql = request.get("sql");

            // SQLの結果を取得
            List<Map<String, Object>> result = jdbcTemplate.queryForList(sql);

            // 結果をJSON形式に変換
            String jsonData = getJsonData(result);

            // 正常レスポンスの設定
            JsonResponse json = new JsonResponse();
            json.setReturn_code(HttpStatus.OK.value());
            json.setMsg("正常にデータを取得しました");
            json.Add("data", jsonData);
            json.SetJsonResponse(res);

        } catch (Exception e) {
            // エラーハンドリング
            String Msg = Kai9Utils.GetException(e);
            JsonResponse json = new JsonResponse();
            json.setReturn_code(HttpStatus.INTERNAL_SERVER_ERROR.value());
            json.setMsg("サーバー内でエラーが発生しました：" + crlf + Msg);
            json.SetJsonResponse(res);
        }
    }

    /**
     * リストをJSON文字列に変換する
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
     * 一覧を返す(選択リストボックス用)
     */
    @PostMapping(value = "/api/get_sqllists", produces = "application/json;charset=utf-8")
    public void get_sqllists(HttpServletResponse res) throws CloneNotSupportedException, IOException, JSONException {
        String sql = "select sql_name from sql_a where delflg = false order by sql_name";
        List<String> results = jdbcTemplate.queryForList(sql, String.class);

        // JSON形式でレスポンスを返す
        JsonResponse json = new JsonResponse();
        json.setReturn_code(HttpStatus.OK.value());
        json.AddArray("results", getJsonData(results));
        json.SetJsonResponse(res);
        return;
    }

    /**
     * SQLを返す
     */
    @PostMapping(value = "/api/get_sql", produces = "application/json;charset=utf-8", consumes = "application/x-www-form-urlencoded")
    public void get_sql(@RequestParam
    Map<String, String> request, HttpServletResponse res) throws CloneNotSupportedException, IOException, JSONException {

        // リクエストからsql_nameを取得
        String sql_name = request.get("sql_name");

        String sql = "select sql from sql_a where sql_name = ?";
        @SuppressWarnings("deprecation")
        String result = jdbcTemplate.queryForObject(sql, new Object[] { sql_name }, String.class);

        
        // gsonを使ってJSON形式で返す
        Gson gson = new Gson();
        res.setContentType("application/json;charset=UTF-8");
        res.setCharacterEncoding("UTF-8");
        try (PrintWriter out = res.getWriter()) {
            String json = gson.toJson(Collections.singletonMap("result", result));
            out.print(json);
            out.flush();
        }
        res.setStatus(HttpStatus.OK.value());        
    }

}
