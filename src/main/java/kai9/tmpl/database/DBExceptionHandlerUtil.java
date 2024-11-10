package kai9.tmpl.database;

import org.hibernate.exception.ConstraintViolationException;
import org.hibernate.exception.DataException;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;

import kai9.libs.Kai9Utils;

/**
 * DBに関する例外メッセージを作成し再スローするクラス
 */
public class DBExceptionHandlerUtil {

    private static final String CRLF = System.lineSeparator();

    public static void handleException(Exception e) throws Exception {
        StringBuilder errorMessages = new StringBuilder();
        Throwable cause = e;

        // 原因を再帰処理で全て表示する
        while (cause != null) {
            errorMessages.append(cause.getClass().getName())
                    .append(": ")
                    .append(cause.getMessage())
                    .append(CRLF);
            cause = cause.getCause();
        }

        // 他の例外に対応
        String Msg = e.getMessage();
        if (e instanceof DuplicateKeyException) {
            throw new Exception("重複するキーによる一意制約違反が発生しました。" + CRLF + Msg + CRLF + errorMessages.toString(), e.getCause());
        } else if (e instanceof DataException) {
            throw new Exception("データベースの入力値が不正です。文字数や形式を確認してください。" + CRLF + Msg + CRLF + errorMessages.toString(), e.getCause());
        } else if (e instanceof DataIntegrityViolationException) {
            if (e.getCause() instanceof ConstraintViolationException) {
                ConstraintViolationException cve = (ConstraintViolationException) e.getCause();
                if (cve.getConstraintName() != null && cve.getConstraintName().contains("_unqidx")) {
                    throw new Exception("重複するキーによる一意制約違反が発生しました。" + CRLF + Msg + CRLF + errorMessages.toString(), e.getCause());
                } else {
                    throw new Exception("データの整合性に問題が発生しました。入力値を確認してください。" + CRLF + Msg + CRLF + errorMessages.toString(), e.getCause());
                }
            } else {
                throw new Exception("データの整合性に問題が発生しました。" + CRLF + Msg + CRLF + errorMessages.toString(), e.getCause());
            }
        } else if (e instanceof OptimisticLockingFailureException) {
            throw new Exception("更新の競合が発生しました。最新のデータで再試行してください。" + CRLF + Msg + CRLF + errorMessages.toString(), e.getCause());
        } else if (e instanceof DataAccessException) {
            Msg = Kai9Utils.GetException(e);
            throw new Exception("データアクセス中に予期せぬエラーが発生しました：" + CRLF + Msg + CRLF + errorMessages.toString(), e.getCause());
        } else {
            Msg = Kai9Utils.GetException(e);
            throw new Exception("サーバー内でエラーが発生しました：" + CRLF + Msg + CRLF + errorMessages.toString(), e.getCause());
        }
    }

}