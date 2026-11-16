package br.com.smartprod.receita;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import br.com.smartprod.receita.dto.FichaTecnicaResponse;
import br.com.smartprod.receita.dto.FichaTecnicaResumoResponse;

/**
 * Regras das fichas técnicas (RF005). A conversão para DTO acontece dentro da transação, porque os
 * ingredientes e os passos são carregados sob demanda. Os erros saem como {@link ResponseStatusException}.
 */
@Service
public class FichaTecnicaService {

    private final FichaTecnicaRepository fichaTecnicaRepository;

    public FichaTecnicaService(FichaTecnicaRepository fichaTecnicaRepository) {
        this.fichaTecnicaRepository = fichaTecnicaRepository;
    }

    @Transactional(readOnly = true)
    public List<FichaTecnicaResumoResponse> listar() {
        return fichaTecnicaRepository.findAllByOrderByNomeAsc().stream()
                .map(FichaTecnicaResumoResponse::de)
                .toList();
    }

    @Transactional(readOnly = true)
    public FichaTecnicaResponse buscar(Long id) {
        FichaTecnica ficha = fichaTecnicaRepository.findById(id).orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Ficha técnica não encontrada."));
        return FichaTecnicaResponse.de(ficha);
    }
}