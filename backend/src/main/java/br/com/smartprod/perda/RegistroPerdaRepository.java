
package br.com.smartprod.perda;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RegistroPerdaRepository extends JpaRepository<RegistroPerda, Long> {

    @EntityGraph(attributePaths = "registradoPor")
    List<RegistroPerda> findByOrdemProducaoIdOrderByRegistradoEmDesc(Long ordemProducaoId);
}
