package kai9.tmpl.controller;

import java.io.IOException;
import java.util.Map;
import java.util.Properties;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.json.JSONException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import com.google.gson.Gson;
import kai9.libs.JsonResponse;

/**
 * APIの認証制御、テストページ
 */
@RestController
@CrossOrigin
public class Api {

    @Autowired
    JdbcTemplate jdbcTemplate;

    // ページ疎通テスト用のURL
    @GetMapping("/api/test")
    public String test() {
        return "TEST OK";
    }

    @Autowired
    @Qualifier("commonjdbc")
    JdbcTemplate jdbcTemplate_com;

    @Autowired
    NamedParameterJdbcTemplate namedJdbcTemplate;

    // 認証
    @PostMapping("/api/check-auth")
    public void check_auth(HttpServletResponse res) throws IOException, JSONException {

        // 現在のログインユーザ名をSPRING SECURITYで取得
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String name = "";
        if (authentication != null) {
            Object principal = authentication.getPrincipal();
            name = principal.toString();
        }

        // ログインIDからユーザ情報を取得
        String sql = "SELECT * FROM m_user_a WHERE login_id = ?";
        try {
            Map<String, Object> map = jdbcTemplate_com.queryForMap(sql, name);// ヒットしない場合例外が発生しcatch区へ遷移

            // 各情報をJSON形式のレスポンスとして返す
            JsonResponse json = new JsonResponse();
            json.setReturn_code(HttpStatus.OK.value());
            json.setMsg("check-auth-OK:" + name);
            json.Add("user_id", String.valueOf(map.get("user_id")));
            json.Add("login_id", String.valueOf(map.get("login_id")));
            json.Add("modify_count", String.valueOf(map.get("modify_count")));
            json.Add("default_g_id", String.valueOf(map.get("default_g_id")));
            json.Add("authority_lv", String.valueOf(map.get("authority_lv")));
            json.SetJsonResponse(res);
            return;

        } catch (Exception e) {
            // ユーザが存在しなければJSON形式でエラーレスポンスを返す
            JsonResponse authresult = new JsonResponse();
            authresult.setMsg("check-auth-NG:" + name);
            authresult.SetJsonResponse(res);
            return;
        }
    }

    // サインアウト
    @PostMapping("/api/signout")
    public String signout(HttpServletRequest req, HttpServletResponse response) {

        // cookieの削除をブラウザへ指示
        // [参考URL]https://qiita.com/hitsumabushi845/items/e2f3467c1493b0dae932
        Cookie cookies[] = req.getCookies();
        for (Cookie cookie : cookies) {
            if ("token".equals(cookie.getName())) {
                // ドメインをapplication.ymlからロード
                YamlPropertiesFactoryBean factory = new YamlPropertiesFactoryBean();
                factory.setResources(new ClassPathResource("application.yml"));
                Properties properties = factory.getObject();
                String jwt_domain = properties.getProperty("jwt.domain");

                // 有効期限を0にし、値も空で返せば消える
                String cookieStr = String.format("%s=%s; HttpOnly; Secure; SameSite=None; Domain=" + jwt_domain + "; Max-Age=0; Path=/", "token", "");
                response.addHeader("Set-Cookie", cookieStr);
            }
        }
        // JSON形式でレスポンスを返す
        Gson gson = new Gson();
        JsonResponse authresult = new JsonResponse();
        authresult.setMsg("OK");
        String res = gson.toJson(authresult);
        return res;
    }

}