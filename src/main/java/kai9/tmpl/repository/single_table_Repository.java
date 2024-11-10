package kai9.tmpl.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import kai9.tmpl.model.single_table;

public interface single_table_Repository extends JpaRepository<single_table, Integer> {
}
