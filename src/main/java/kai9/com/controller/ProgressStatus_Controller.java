package kai9.com.controller;

import java.io.IOException;
import java.sql.Timestamp;
import java.util.Map;

import javax.servlet.http.HttpServletResponse;

import org.json.JSONArray;
import org.json.JSONException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import kai9.com.dto.ProgressStatus_Request;
import kai9.com.model.ProgressStatus;
import kai9.com.service.ProgressStatus_Service;
import kai9.libs.JsonResponse;
import kai9.libs.Kai9Utils;

/**
 * 進捗状況の制御
 */
@RestController
@CrossOrigin
public class ProgressStatus_Controller {

    @Autowired
    private ProgressStatus_Service progressStatus_Service;

    @Autowired
    @Qualifier("commonjdbc")
    JdbcTemplate jdbcTemplate_com;

    @Autowired
    NamedParameterJdbcTemplate namedJdbcTemplate;

    /**
     * 進捗状況の採番
     */
    @PostMapping(value = "/api/progress_status_create", produces = "application/json;charset=utf-8")
    public void create(@RequestBody
    ProgressStatus_Request progressStatus_Request, HttpServletResponse res) throws CloneNotSupportedException, IOException, JSONException {
        String crlf = System.lineSeparator(); // 改行コード
        try {

            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String name = auth.getName();
            int user_id = getUserIDByLoginID(name);

            // 各フィールドを設定
            progressStatus_Request.setStatus("IN_PROGRESS");
            progressStatus_Request.setProgress1(0);
            progressStatus_Request.setProgress2(0);
            progressStatus_Request.setMessage("開始");
            progressStatus_Request.setIs_stop(false);
            progressStatus_Request.setUpdateUId(user_id);
            progressStatus_Request.setUpdateDate(new Timestamp(new java.util.Date().getTime()));
            ProgressStatus progressStatusResult = progressStatus_Service.create(progressStatus_Request);

            JsonResponse json = new JsonResponse();
            json.setReturn_code(HttpStatus.OK.value());
            json.setMsg("正常に登録しました");
            json.Add("id", String.valueOf(progressStatusResult.getId()));
            json.SetJsonResponse(res);
            return;

        } catch (DuplicateKeyException e) {
            String msg = Kai9Utils.GetException(e);
            JsonResponse json = new JsonResponse();
            json.setReturn_code(HttpStatus.CONFLICT.value());
            json.setMsg("重複するキーによる一意制約違反が発生しました。" + crlf + msg);
            json.SetJsonResponse(res);
        } catch (DataIntegrityViolationException e) {
            String msg = Kai9Utils.GetException(e);
            JsonResponse json = new JsonResponse();
            json.setReturn_code(HttpStatus.BAD_REQUEST.value());
            json.setMsg("データの整合性に問題が発生しました。入力値を確認してください。" + crlf + msg);
            json.SetJsonResponse(res);
        } catch (OptimisticLockingFailureException e) {
            String msg = Kai9Utils.GetException(e);
            JsonResponse json = new JsonResponse();
            json.setReturn_code(HttpStatus.CONFLICT.value());
            json.setMsg("更新の競合が発生しました。最新のデータで再試行してください。" + crlf + msg);
            json.SetJsonResponse(res);
        } catch (DataAccessException e) {
            String msg = Kai9Utils.GetException(e);
            JsonResponse json = new JsonResponse();
            json.setReturn_code(HttpStatus.INTERNAL_SERVER_ERROR.value());
            json.setMsg("データアクセス中に予期せぬエラーが発生しました：" + crlf + msg);
            json.SetJsonResponse(res);
        } catch (Exception e) {
            String msg = Kai9Utils.GetException(e);
            JsonResponse json = new JsonResponse();
            json.setReturn_code(HttpStatus.INTERNAL_SERVER_ERROR.value());
            json.setMsg("サーバー内でエラーが発生しました：" + crlf + msg);
            json.SetJsonResponse(res);
        }
    }

    /**
     * ログインIDからユーザIDを取得
     */
    public int getUserIDByLoginID(String login_id) {
        String sql = "select * from m_user_a where login_id = ?";
        Map<String, Object> map = jdbcTemplate_com.queryForMap(sql, login_id);
        int user_id = (int) map.get("user_id");
        return user_id;
    }

    /**
     * 進捗状況を返す
     */
    @PostMapping(value = "/api/progress_status_check", produces = "application/json;charset=utf-8")
    public void progress_status_check(@RequestParam
    Map<String, String> requestData, HttpServletResponse res) throws CloneNotSupportedException, IOException, JSONException {
        try {
            int id = Integer.parseInt(requestData.get("id"));
            ;
            String sql = "select * from progress_status where id = ?";
            Map<String, Object> map = jdbcTemplate_com.queryForMap(sql, id);
            int progress1 = (int) map.get("progress1");
            int progress2 = (int) map.get("progress2");

            // JSON形式でレスポンスを返す
            JsonResponse json = new JsonResponse();
            json.setReturn_code(HttpStatus.OK.value());
            json.AddArray("progress1", new JSONArray().put(progress1).toString());
            json.AddArray("progress2", new JSONArray().put(progress2).toString());
            json.SetJsonResponse(res);
            return;
        } catch (Exception e) {
            Kai9Utils.handleException(e, res);
        }

    }

    /**
     * 進捗状況に中止を登録
     */
    /**
     * 進捗状況に中止を登録
     */
    @PostMapping(value = "/api/progress_status_stop", produces = "application/json;charset=utf-8")
    public void progress_status_stop(@RequestParam
    int id, HttpServletResponse res) throws IOException, JSONException {
        try {
            String sql = "UPDATE progress_status SET is_stop = true WHERE id = ?";
            int rows = jdbcTemplate_com.update(sql, id);

            JsonResponse json = new JsonResponse();
            if (rows > 0) {
                json.setReturn_code(HttpStatus.OK.value());
                json.setMsg("進捗状況を中止に設定しました");
            } else {
                json.setReturn_code(HttpStatus.NOT_FOUND.value());
                json.setMsg("指定されたIDの進捗状況が見つかりません");
            }
            json.SetJsonResponse(res);
        } catch (Exception e) {
            Kai9Utils.handleException(e, res);
        }
    }

}