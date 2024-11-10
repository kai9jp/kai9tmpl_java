package kai9.tmpl.auth.filter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.tomcat.util.codec.binary.Base64;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;

import kai9.libs.Kai9Utils;
import kai9.libs.kai9properties;

//認証のためのトークンを作成するクラス
public class WebUsernamePasswordAuthenticationFilter extends UsernamePasswordAuthenticationFilter {

    private final String kai9secretKey;

    public WebUsernamePasswordAuthenticationFilter(AuthenticationManager authenticationManager, String secretKey) {
        this.kai9secretKey = secretKey;

        // ログイン用パラメータの設定
        setUsernameParameter("username");
        setPasswordParameter("password");

        // ログイン失敗時
        this.setAuthenticationFailureHandler((req, res, ex) -> {
            res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);// 401エラーを返す(リクエストに HTTP 認証が必要であることを示すステータスコード)
        });
    }

    // 認証処理
    @Override
    public Authentication attemptAuthentication(HttpServletRequest req, HttpServletResponse res)
            throws AuthenticationException {

        // リクエストからユーザ名とパスワードを取り出す
        String authorization = req.getHeader("Authorization");
        String userAndPass = new String(Base64.decodeBase64(authorization.substring("Basic".length())));
        String user = userAndPass.substring(0, userAndPass.indexOf(":"));
        String password = userAndPass.substring((userAndPass.indexOf(":") + 1));

        if (user == null) {
            user = "";
        }

        if (password == null) {
            password = "";
        }

        // トークンの作成
        UsernamePasswordAuthenticationToken authRequest = new UsernamePasswordAuthenticationToken(user, password);

        // 認証リクエストの詳細プロパティにトークンをセット
        setDetails(req, authRequest);

        // 後続に認証可否を委譲
        return this.getAuthenticationManager().authenticate(authRequest);
    }

    // 認証に成功した場合の処理
    @Override
    protected void successfulAuthentication(HttpServletRequest req,
            HttpServletResponse res,
            FilterChain chain,
            Authentication auth) throws IOException, ServletException {

        // ユーザ名を取得
        Object principal = auth.getPrincipal();
        String username = ((UserDetails) principal).getUsername();

        // トークン(JWT)の作成
        // https://openid-foundation-japan.github.io/draft-ietf-oauth-json-web-token-11.ja.html
        String secretKey = kai9secretKey;
        Algorithm algorithm = Algorithm.HMAC256(secretKey);
        String token = JWT.create()
                .withSubject(username) // 題名の代わりにユーザ名を入れて使う
                .withIssuer("com.kai9") // 発行者
                .withIssuedAt(Date.from(ZonedDateTime.of(LocalDateTime.now(), ZoneId.systemDefault()).toInstant())) // 現在時刻をセットする事でトークン毎回違う値で生成させる
                .withExpiresAt(Date.from(ZonedDateTime.now().plusSeconds(kai9properties.CookieMaxAge2).toInstant())) // 有効期限を設定(トークン自体の有効期限)
                .sign(algorithm); // 利用アルゴリズムを指定してJWTを新規作成

        // ドメインをapplication.ymlからロード
        String jwt_domain = Kai9Utils.getPropertyFromYaml("jwt.domain");

        // Set-Cookieヘッダーを付ける(SameSite) https://qiita.com/nannou/items/fc86d052e356e095fcbf
        // ドメイン属性をフロントエンドのドメイン名と同じにしないとクッキーが保存されない
        String cookie = String.format("%s=%s; HttpOnly; Secure; SameSite=None; Domain=" + jwt_domain + "; Max-Age=" + kai9properties.CookieMaxAge2 + "; Path=/", "token", token);
        // String cookie = String.format("%s=%s; HttpOnly; Secure; SameSite=None; Domain=kai9.com; Max-Age=" + kai9properties.CookieMaxAge2 + "; Path=/", "token", token);
        // String cookie = String.format("%s=%s; HttpOnly; Secure; SameSite=None; Domain=kai9.com; Max-Age=600; Path=/", "token", token);
        res.addHeader("Set-Cookie", cookie);

        // ステータス200(OK)を返す
        res.setStatus(HttpStatus.OK.value());
    }

}