package br.com.smartprod.producao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import br.com.smartprod.receita.FichaTecnica;
import br.com.smartprod.receita.FichaTecnicaRepository;
import br.com.smartprod.usuario.Perfil;
import br.com.smartprod.usuario.Usuario;
import br.com.smartprod.usuario.UsuarioRepository;

/**
 * Duas pessoas concluem a mesma OP ao mesmo tempo (dois tablets): a segunda não pode sobrescrever a
 * quantidade produzida da primeira. Sem {@code @Transactional} de propósito: cada lado precisa da própria
 * transação.
 */
@SpringBootTest
class ConclusaoSimultaneaTest {

    @Autowired
    private OrdemProducaoRepository ordemRepository;
    @Autowired
    private FichaTecnicaRepository fichaRepository;
    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private PlatformTransactionManager transacoes;

    private Usuario chefe;
    private FichaTecnica ficha;
    private OrdemProducao ordem;

    @BeforeEach
    void criarOrdemEmAndamento() {
        chefe = usuarioRepository.save(new Usuario("Chefe", "chefe.simultanea@padaria.com", "hash", Perfil.GERENTE));
        ficha = fichaRepository.save(new FichaTecnica("Pão de teste simultâneo", 50));
        OrdemProducao nova = new OrdemProducao(ficha, 120, Instant.now(), chefe, chefe);
        nova.iniciar();
        ordem = ordemRepository.save(nova);
    }

    @AfterEach
    void apagarDados() {
        ordemRepository.deleteById(ordem.getId());
        fichaRepository.deleteById(ficha.getId());
        usuarioRepository.deleteById(chefe.getId());
    }

    @Test
    void segundaConclusaoAoMesmoTempoFalhaEmVezDeSobrescrever() {
        TransactionTemplate primeiroTablet = new TransactionTemplate(transacoes);
        TransactionTemplate segundoTablet = new TransactionTemplate(transacoes);
        segundoTablet.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        assertThatThrownBy(() -> primeiroTablet.executeWithoutResult(status -> {
            OrdemProducao vistaPeloPrimeiro = ordemRepository.findById(ordem.getId()).orElseThrow();

            // o segundo tablet conclui e grava enquanto o primeiro ainda está com a OP aberta
            segundoTablet.executeWithoutResult(outro -> ordemRepository.findById(ordem.getId()).orElseThrow()
                    .concluir(118));

            vistaPeloPrimeiro.concluir(5);
        })).isInstanceOf(ObjectOptimisticLockingFailureException.class);

        assertThat(ordemRepository.findById(ordem.getId()).orElseThrow().getQuantidadeProduzida()).isEqualTo(118);
    }
}
