package br.com.smartprod.receita;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface FichaTecnicaRepository extends JpaRepository<FichaTecnica, Long> {

    List<FichaTecnica> findAllByOrderByNomeAsc();
}