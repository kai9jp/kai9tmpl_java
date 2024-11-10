package kai9.tmpl.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import kai9.tmpl.model.AppEnv;

/**
 * 環境設定 :リポジトリ
 */
public interface AppEnv_Repository extends JpaRepository<AppEnv, Integer> {
}
