package kai9.tmpl.exec;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import kai9.libs.Kai9Utils;
import kai9.tmpl.model.AppEnv;

//アプリ起動時に動く(ApplicationRunner)
@Component
@EnableScheduling
public class autoExec implements ApplicationRunner {

    @Autowired
    @Qualifier("commonjdbc")
    JdbcTemplate jdbcTemplate_com;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Value("${logging.file.path}")
    private String logDirPath;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        makeZuser();

        // 起動した事をファイルに書き込む
        Kai9Utils.makeLog("info", "起動完了", this.getClass());
        try {
            // ログファイルのディレクトリを取得
            File logDir = new File(logDirPath);

            // ファイル名を指定
            File startupLogFile = new File(logDir, "startup.log");

            // 現在日時を取得してフォーマット
            LocalDateTime now = LocalDateTime.now();
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            String formattedNow = now.format(formatter);

            // ファイルを作成して内容を書き込む
            try (FileWriter writer = new FileWriter(startupLogFile)) {
                writer.write("起動しました " + formattedNow);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // 初期ユーザが存在しない場合は作成する
    public void makeZuser() {
        String sql = "INSERT INTO m_user_a (modify_count, login_id, sei, mei, sei_kana, mei_kana, password, mail, need_password_change, ip, default_g_id, authority_lv, note, update_u_id, update_date, delflg)" +
                " SELECT 1, 'z', 'z', '初期ユーザ', 'ゼット', 'ショキユーザ', '$2a$10$uMpb0Nv9JmTpOdB272FQxOmbKsJFYd6kJkxjyXB3RiuX3soYIIN92', 'z@kai9.com', FALSE, '', 0, 3, '', 0, ?, FALSE" +
                " WHERE NOT EXISTS (" +
                "   SELECT 1 FROM m_user_a WHERE login_id = 'z'" +
                " ) RETURNING user_id;";
        Integer newUserId = null;
        try {
            newUserId = jdbcTemplate_com.queryForObject(sql, new Object[] { Timestamp.valueOf(LocalDateTime.now()) }, Integer.class);
            Kai9Utils.makeLog("info", "自動実行モード:初期ユーザ(z)の自動作成に成功しました。ID=," + newUserId, this.getClass());
        } catch (EmptyResultDataAccessException e) {
            // INSERT文が実行されず、キーが返されなかった場合、何もしない
        } catch (DataAccessException e) {
            // その他のデータベースアクセスエラー
            Kai9Utils.makeLog("error", "自動実行モード:初期ユーザ(z)の自動作成に失敗しました,", this.getClass());
        }
        if (newUserId != null) {
            // 自動生成した場合だけ、履歴テーブルへコピー
            String copySql = "INSERT INTO m_user_b SELECT * FROM m_user_a WHERE user_id = ?;";
            jdbcTemplate_com.update(copySql, newUserId);
        }
    }

    // データベースから環境設定をロードし、古いファイルを削除するメインメソッド
    @Scheduled(fixedRate = 60000 * 60) // 60分毎に実行 (60000ミリ秒(1分) × 60)
    public void DeleteOldFiles() {

        // 環境設定をロード
        String sql = "select * from app_env_a";
        RowMapper<AppEnv> rowMapper = new BeanPropertyRowMapper<AppEnv>(AppEnv.class);
        List<AppEnv> m_envList = jdbcTemplate.query(sql, rowMapper);
        if (m_envList.isEmpty()) {
            return;
            // throw new RuntimeException("環境設定のロードに失敗しました");
        }
        AppEnv AppEnv = m_envList.get(0);

        // 指定されたディレクトリの存在確認
        Path dirPath = Paths.get(AppEnv.getDir_tmp());
        if (!Files.exists(dirPath) || !Files.isDirectory(dirPath)) {
            return;
            // throw new RuntimeException("指定されたディレクトリが存在しません: " + AppEnv.getDir_tmp());
        }

        if (AppEnv.getDel_days_tmp() == 0) {
            // 経過日数が0指定の場合は何もしない
            return;
        }

        try {
            // 指定されたディレクトリ内のファイルとフォルダを再帰的に確認
            Files.walkFileTree(Paths.get(AppEnv.getDir_tmp()), new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    // ファイルが指定した日数を経過している場合は削除
                    if (isOlderThan(file, AppEnv.getDel_days_tmp())) {
                        Files.delete(file);
                        System.out.println("Deleted file: " + file);
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    // ディレクトリが、空の場合は削除
                    if (!dir.equals(dirPath) && isDirectoryEmpty(dir)) {
                        Files.delete(dir);
                        System.out.println("Deleted directory: " + dir);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // 指定したパスのファイルやディレクトリが指定した日数を経過しているかどうかを判定するヘルパーメソッド
    private static boolean isOlderThan(Path path, int daysThreshold) throws IOException {
        // ファイルの属性を取得
        BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);
        // 作成日時を取得
        FileTime creationTime = attrs.creationTime();
        // 作成日時をLocalDateTimeに変換
        LocalDateTime fileTime = LocalDateTime.ofInstant(creationTime.toInstant(), ZoneId.systemDefault());
        // 現在日時から指定した日数を引いた日時を計算
        LocalDateTime thresholdTime = LocalDateTime.now().minusDays(daysThreshold);
        // ファイルの作成日時が閾値より前かどうかを判定
        return fileTime.isBefore(thresholdTime);
    }

    // 指定したディレクトリが空かどうかを判定するヘルパーメソッド
    private static boolean isDirectoryEmpty(Path dir) throws IOException {
        try (DirectoryStream<Path> directoryStream = Files.newDirectoryStream(dir)) {
            return !directoryStream.iterator().hasNext();
        }
    }

    // データベースから環境設定をロードし、古い進捗状況を削除するメソッド
    @Scheduled(fixedRate = 60000 * 60) // 60分毎に実行 (60000ミリ秒(1分) × 60)
    public void DeleteOld_Progress_status() {
        // 環境設定をロード
        String sql = "select * from app_env_a";
        RowMapper<AppEnv> rowMapper = new BeanPropertyRowMapper<AppEnv>(AppEnv.class);
        List<AppEnv> m_envList = jdbcTemplate.query(sql, rowMapper);
        if (m_envList.isEmpty()) {
            return;
        }
        AppEnv AppEnv = m_envList.get(0);

        if (AppEnv.getDel_days_tmp() == 0) {
            return;
        }

        // 古い進捗状況の削除
        String deleteSql = "DELETE FROM progress_status WHERE update_date < ?";
        LocalDateTime thresholdTime = LocalDateTime.now().minusDays(AppEnv.getDel_days_tmp());
        jdbcTemplate.update(deleteSql, Timestamp.valueOf(thresholdTime));
    }

}
