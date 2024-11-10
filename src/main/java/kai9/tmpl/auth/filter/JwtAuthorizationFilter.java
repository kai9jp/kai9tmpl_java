package kai9.tmpl.auth.filter;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.google.gson.Gson;

import kai9.libs.JsonResponse;

public class JwtAuthorizationFilter extends BasicAuthenticationFilter {

    private final String kai9secretKey;

    /**
     * コンストラクタ
     * 
     * @param authenticationManager
     */
    public JwtAuthorizationFilter(AuthenticationManager authenticationManager, String secretKey) {
        super(authenticationManager);
        this.kai9secretKey = secretKey;
    }

    private Gson gson = new Gson();

    /**
     * Jwtの認可処理
     */
    @Override
    protected void doFilterInternal(HttpServletRequest req,
            HttpServletResponse res,
            FilterChain chain) throws IOException, ServletException {
        try {
            // トークンを取得
            String token = "";
            Cookie cookie[] = req.getCookies();
            if (cookie != null) {
                for (int i = 0; i < cookie.length; i++) {
                    if (cookie[i].getName().equals("token")) {
                        token = cookie[i].getValue();
                    }
                }
            }

            if (token == "") {
                // トークンが無い場合、「token is not found」と、戻り値2を返す
                JsonResponse authresult = new JsonResponse();
                authresult.setReturn_code(2);
                authresult.setMsg("token is not found");
                String authresultJsonString = this.gson.toJson(authresult);
                PrintWriter out = res.getWriter();
                res.setContentType("application/json");
                res.setCharacterEncoding("UTF-8");
                // res.setStatus(HttpStatus.BAD_REQUEST.value());異常値を返すとReact側でリターンコードを取れなくなるので正常値で戻す
                res.setStatus(HttpStatus.OK.value());
                out.print(authresultJsonString);
                return;
            }

            // 認証トークン作成
            UsernamePasswordAuthenticationToken authentication = getAuthentication(token);

            // 独自に認可
            // 認証オブジェクトを渡すことによってセキュリティコンテキストを確立させる
            SecurityContextHolder.getContext().setAuthentication(authentication);

            res.setStatus(HttpStatus.OK.value());
            chain.doFilter(req, res);

        } catch (Exception e) {
            // 認可に失敗した場合、戻り値1を返す
            // https://ja.getdocs.org/servlet-json-response
            JsonResponse authresult = new JsonResponse();
            authresult.setReturn_code(1);
            authresult.setMsg(e.getMessage());
            String authresultJsonString = this.gson.toJson(authresult);
            PrintWriter out = res.getWriter();
            res.setContentType("application/json");
            res.setCharacterEncoding("UTF-8");
            res.setStatus(HttpStatus.OK.value());
            // res.setStatus(HttpStatus.BAD_REQUEST.value());React側でリターンコードを取るので正常で戻す
            out.print(authresultJsonString);
            out.flush();
            return;
        }
    }

    /**
     * 認証token作成
     * 
     * @param token
     * @return
     */
    private UsernamePasswordAuthenticationToken getAuthentication(String token) {

        if (token != null) {
            // インプットされたトークンを解析しユーザ名を取り出す
            String secretKey = kai9secretKey;
            Algorithm algorithm = Algorithm.HMAC256(secretKey);
            JWTVerifier verifier = JWT.require(algorithm)
                    .withIssuer("com.kai9")
                    .build();
            DecodedJWT jwt = verifier.verify(token);
            String user = jwt.getSubject();

            if (user != null) {
                // トークンから取り出したユーザ名を渡して、Managerに認証可否を委譲
                return new UsernamePasswordAuthenticationToken(user, null, new ArrayList<>());
            }
            return null;
        }
        return null;
    }
}