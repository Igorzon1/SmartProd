package br.com.smartprod.config;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Dados de demonstração para apresentar o protótipo (perfil {@code demo}): o Kanban e o dashboard já
 * abrem com dados de uma padaria, em vez de vazios.
 *
 * <p>Cria 14 insumos (um inativo), 4 fichas técnicas com modo de preparo, 4 funcionários (um inativo),
 * as OPs dos últimos {@value #DIAS_DE_HISTORICO} dias (concluídas, com perdas registradas) e as de hoje
 * (concluídas, uma em andamento, pendentes e uma atrasada).
 *
 * <p>Uso: {@code ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev,demo}. Roda depois da seed do
 * Gerente e <b>só quando o banco ainda não tem nenhuma ficha técnica</b>: reiniciar não duplica nada e
 * um banco com dados reais nunca é tocado. Os funcionários entram com {@link #SENHA_FUNCIONARIOS}; são
 * contas fictícias, por isso este perfil é só para demonstração.
 *
 * <p>Grava direto pelo JDBC porque precisa de horários no passado (início, conclusão e perdas), que as
 * entidades sempre preenchem com o horário atual.
 */
@Component
@Profile("demo")
class DadosDemonstracao {

    /** Senha das contas fictícias de funcionário criadas aqui. */
    static final String SENHA_FUNCIONARIOS = "Demo@1234";

    static final int DIAS_DE_HISTORICO = 13;

    private static final Logger log = LoggerFactory.getLogger(DadosDemonstracao.class);
    private static final BigDecimal MIL = BigDecimal.valueOf(1000);
    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");

    private static final List<InsumoDemo> INSUMOS = List.of(
            new InsumoDemo("Farinha de trigo", "G", "0.0055", true),
            new InsumoDemo("Água", "ML", "0.0001", true),
            new InsumoDemo("Sal", "G", "0.0020", true),
            new InsumoDemo("Fermento biológico seco", "G", "0.0600", true),
            new InsumoDemo("Fermento químico", "G", "0.0400", true),
            new InsumoDemo("Açúcar", "G", "0.0045", true),
            new InsumoDemo("Manteiga", "G", "0.0450", true),
            new InsumoDemo("Leite integral", "ML", "0.0055", true),
            new InsumoDemo("Ovo", "UN", "0.8000", true),
            new InsumoDemo("Chocolate em pó", "G", "0.0600", true),
            new InsumoDemo("Óleo de soja", "ML", "0.0080", true),
            new InsumoDemo("Polvilho azedo", "G", "0.0120", true),
            new InsumoDemo("Queijo meia cura", "G", "0.0450", true),
            new InsumoDemo("Margarina", "G", "0.0150", false));

    private static final String PAO_FRANCES = "Pão francês";
    private static final String PAO_DE_LEITE = "Pão de leite";
    private static final String BOLO = "Bolo de chocolate (fatia)";
    private static final String PAO_DE_QUEIJO = "Pão de queijo";

    private static final List<FichaDemo> FICHAS = List.of(
            new FichaDemo(PAO_FRANCES, 30,
                    List.of(new Ingrediente("Farinha de trigo", "1000", true),
                            new Ingrediente("Água", "600", false),
                            new Ingrediente("Sal", "20", false),
                            new Ingrediente("Fermento biológico seco", "10", false),
                            new Ingrediente("Açúcar", "10", false)),
                    List.of(new Passo("Pesagem", "Separe e pese todos os ingredientes da lista.", 5),
                            new Passo("Mistura e sova", "Misture os secos, junte a água aos poucos e sove até a "
                                    + "massa ficar lisa e elástica.", 12),
                            new Passo("Descanso", "Cubra a massa e deixe descansar na bancada.", 15),
                            new Passo("Modelagem", "Divida em porções de 55 g, modele os pães e coloque nas "
                                    + "assadeiras.", 20),
                            new Passo("Fermentação", "Deixe crescer até dobrar de volume, longe de corrente de "
                                    + "ar.", 90),
                            new Passo("Forno", "Faça o corte, borrife água e asse a 220 °C até dourar.", 18)),
                    List.of(new PerdaTipica("Forno", "Fornada passou do ponto e queimou"),
                            new PerdaTipica("Fermentação", "Massa fermentou demais e murchou"),
                            new PerdaTipica("Modelagem", "Pães deformados na modelagem"))),
            new FichaDemo(PAO_DE_LEITE, 20,
                    List.of(new Ingrediente("Farinha de trigo", "1000", true),
                            new Ingrediente("Leite integral", "500", false),
                            new Ingrediente("Açúcar", "100", false),
                            new Ingrediente("Manteiga", "80", false),
                            new Ingrediente("Ovo", "2", false),
                            new Ingrediente("Fermento biológico seco", "20", false),
                            new Ingrediente("Sal", "15", false)),
                    List.of(new Passo("Pesagem", "Separe e pese todos os ingredientes da lista.", 5),
                            new Passo("Mistura e sova", "Misture tudo, menos a manteiga; junte a manteiga no fim "
                                    + "e sove até a massa soltar das mãos.", 15),
                            new Passo("Primeira fermentação", "Cubra e deixe a massa dobrar de volume.", 60),
                            new Passo("Modelagem", "Divida em 20 porções, boleie e arrume nas formas.", 20),
                            new Passo("Fermentação final", "Deixe crescer de novo nas formas.", 40),
                            new Passo("Forno", "Pincele ovo e asse a 180 °C até dourar.", 25)),
                    List.of(new PerdaTipica("Primeira fermentação", "Massa não cresceu (fermento fraco)"),
                            new PerdaTipica("Forno", "Fundo queimado"))),
            new FichaDemo(BOLO, 16,
                    List.of(new Ingrediente("Farinha de trigo", "300", true),
                            new Ingrediente("Açúcar", "300", false),
                            new Ingrediente("Chocolate em pó", "100", false),
                            new Ingrediente("Ovo", "4", false),
                            new Ingrediente("Leite integral", "240", false),
                            new Ingrediente("Óleo de soja", "120", false),
                            new Ingrediente("Fermento químico", "15", false)),
                    List.of(new Passo("Pesagem", "Separe e pese todos os ingredientes da lista.", 5),
                            new Passo("Massa", "Bata os ovos, o açúcar e o óleo; junte o leite, a farinha e o "
                                    + "chocolate; por último, o fermento.", 10),
                            new Passo("Forno", "Asse em forma untada a 180 °C e confira com um palito.", 40),
                            new Passo("Resfriamento", "Desenforme morno e corte em 16 fatias depois de frio.", 30)),
                    List.of(new PerdaTipica("Forno", "Bolo solou no meio"),
                            new PerdaTipica("Resfriamento", "Quebrou ao desenformar"))),
            new FichaDemo(PAO_DE_QUEIJO, 40,
                    List.of(new Ingrediente("Polvilho azedo", "500", true),
                            new Ingrediente("Leite integral", "250", false),
                            new Ingrediente("Óleo de soja", "120", false),
                            new Ingrediente("Ovo", "3", false),
                            new Ingrediente("Queijo meia cura", "250", false),
                            new Ingrediente("Sal", "10", false)),
                    List.of(new Passo("Escaldar", "Ferva o leite, o óleo e o sal e despeje sobre o polvilho, "
                                    + "mexendo bem.", 10),
                            new Passo("Massa", "Quando amornar, junte os ovos e o queijo e sove até a massa "
                                    + "ficar lisa.", 15),
                            new Passo("Modelagem", "Boleie porções de 30 g e arrume nas assadeiras.", 20),
                            new Passo("Forno", "Asse a 200 °C até crescer e dourar.", 25)),
                    List.of(new PerdaTipica("Forno", "Murchou ao sair do forno"),
                            new PerdaTipica("Massa", "Massa mole demais, não deu ponto"))));

    private static final String BETO = "beto@smartprod.dev";
    private static final String CARLA = "carla@smartprod.dev";
    private static final String DIEGO = "diego@smartprod.dev";

    /** A produção de um dia comum (o bolo sai em dias alternados). */
    private static final List<OpDoDia> ROTINA = List.of(
            new OpDoDia(PAO_FRANCES, 150, LocalTime.of(6, 30), Duration.ofMinutes(150), BETO, false),
            new OpDoDia(PAO_DE_QUEIJO, 120, LocalTime.of(7, 0), Duration.ofMinutes(75), DIEGO, false),
            new OpDoDia(PAO_DE_LEITE, 40, LocalTime.of(9, 0), Duration.ofMinutes(180), BETO, false),
            new OpDoDia(BOLO, 32, LocalTime.of(10, 0), Duration.ofMinutes(90), CARLA, true),
            new OpDoDia(PAO_FRANCES, 120, LocalTime.of(16, 30), Duration.ofMinutes(150), BETO, false));

    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;

    DadosDemonstracao(JdbcTemplate jdbc, PasswordEncoder passwordEncoder) {
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
    }

    /** Roda depois dos {@code ApplicationRunner}, ou seja, depois da seed do Gerente. */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void criar() {
        Long fichas = jdbc.queryForObject("SELECT COUNT(*) FROM fichas_tecnicas", Long.class);
        if (fichas != null && fichas > 0) {
            log.info("Dados de demonstração não criados: o banco já tem fichas técnicas.");
            return;
        }
        List<Long> gerentes = jdbc.queryForList(
                "SELECT id FROM usuarios WHERE perfil = 'GERENTE' AND ativo = TRUE ORDER BY id", Long.class);
        if (gerentes.isEmpty()) {
            log.warn("Dados de demonstração não criados: não há Gerente ativo (configure a seed do Gerente).");
            return;
        }
        Montagem montagem = new Montagem(gerentes.get(0));
        montagem.executar();
        log.warn("Dados de demonstração criados ({} OPs e {} perdas). Perfil só para apresentação: "
                + "os funcionários fictícios têm senha conhecida.", montagem.ops, montagem.perdas);
    }

    /** Estado de uma execução: os ids gerados e os contadores para o log. */
    private final class Montagem {

        private final long gerente;
        private final Map<String, Long> insumos = new HashMap<>();
        private final Map<String, String> unidades = new HashMap<>();
        private final Map<String, Long> fichas = new HashMap<>();
        private final Map<String, BigDecimal> kgPorUnidade = new HashMap<>();
        private final Map<String, Long> funcionarios = new HashMap<>();
        private int ops;
        private int perdas;

        Montagem(long gerente) {
            this.gerente = gerente;
        }

        void executar() {
            criarCadastros();
            LocalDate hoje = LocalDate.now(FUSO);
            for (int diasAtras = DIAS_DE_HISTORICO; diasAtras >= 1; diasAtras--) {
                criarDiaPassado(hoje.minusDays(diasAtras), diasAtras);
            }
            criarHoje(Instant.now());
        }

        private void criarCadastros() {
            for (InsumoDemo insumo : INSUMOS) {
                insumos.put(insumo.nome(), inserir("insumos", colunas("nome", insumo.nome(), "unidade_medida",
                        insumo.unidade(), "custo_unitario", new BigDecimal(insumo.custo()), "ativo", insumo.ativo())));
                unidades.put(insumo.nome(), insumo.unidade());
            }
            for (FichaDemo ficha : FICHAS) {
                long id = inserir("fichas_tecnicas", colunas("nome", ficha.nome(), "rendimento_padrao",
                        ficha.rendimento()));
                fichas.put(ficha.nome(), id);
                BigDecimal massaKg = BigDecimal.ZERO;
                for (Ingrediente ingrediente : ficha.ingredientes()) {
                    BigDecimal quantidade = new BigDecimal(ingrediente.quantidade());
                    inserir("ficha_ingredientes", colunas("ficha_tecnica_id", id, "insumo_id",
                            insumos.get(ingrediente.insumo()), "quantidade_base", quantidade, "eh_farinha",
                            ingrediente.farinha()));
                    massaKg = massaKg.add(emKg(quantidade, unidades.get(ingrediente.insumo())));
                }
                kgPorUnidade.put(ficha.nome(),
                        massaKg.divide(BigDecimal.valueOf(ficha.rendimento()), 6, RoundingMode.HALF_UP));
                int ordem = 1;
                for (Passo passo : ficha.passos()) {
                    inserir("passos_preparo", colunas("ficha_tecnica_id", id, "ordem", ordem++, "etapa",
                            passo.etapa(), "descricao", passo.descricao(), "duracao_segundos", passo.minutos() * 60));
                }
            }
            String hash = passwordEncoder.encode(SENHA_FUNCIONARIOS);
            funcionarios.put(BETO, funcionario("Beto Padeiro", BETO, true, hash));
            funcionarios.put(CARLA, funcionario("Carla Confeiteira", CARLA, true, hash));
            funcionarios.put(DIEGO, funcionario("Diego Ajudante", DIEGO, true, hash));
            funcionario("Lia Souza", "lia@smartprod.dev", false, hash);
        }

        /** Todas as OPs do dia concluídas; algumas com perda, que tira unidades do produzido. */
        private void criarDiaPassado(LocalDate dia, int diasAtras) {
            for (int i = 0; i < ROTINA.size(); i++) {
                OpDoDia op = ROTINA.get(i);
                if (op.diasPares() && diasAtras % 2 != 0) {
                    continue;
                }
                Random sorteio = new Random(diasAtras * 100L + i);
                Instant entrega = dia.atTime(op.entrega()).atZone(FUSO).toInstant();
                Instant inicio = entrega.minus(op.duracao());
                Instant conclusao = entrega.minus(Duration.ofMinutes(10));
                int perdidas = 0;
                FichaDemo ficha = ficha(op.ficha());
                boolean tevePerda = sorteio.nextInt(100) < 55;
                if (tevePerda) {
                    perdidas = op.ficha().equals(BOLO) ? 1 + sorteio.nextInt(4) : 2 + sorteio.nextInt(8);
                }
                long id = inserirOp(op, entrega, "CONCLUIDA", inicio, conclusao, op.unidades() - perdidas);
                if (tevePerda) {
                    PerdaTipica tipo = ficha.perdas().get(sorteio.nextInt(ficha.perdas().size()));
                    inserirPerda(id, op, tipo, perdidas, conclusao.minus(Duration.ofMinutes(20)));
                }
            }
        }

        /** Hoje, relativo ao horário atual: concluídas, uma em andamento, uma atrasada e pendentes. */
        private void criarHoje(Instant agora) {
            OpDoDia francesManha = new OpDoDia(PAO_FRANCES, 150, null, null, BETO, false);
            long concluida = inserirOp(francesManha, agora.minus(horas(1.5)), "CONCLUIDA", agora.minus(horas(4)),
                    agora.minus(horas(1.75)), 144);
            inserirPerda(concluida, francesManha, ficha(PAO_FRANCES).perdas().get(0), 6, agora.minus(horas(2)));

            OpDoDia queijo = new OpDoDia(PAO_DE_QUEIJO, 120, null, null, DIEGO, false);
            inserirOp(queijo, agora.minus(horas(3)), "CONCLUIDA", agora.minus(horas(4.5)), agora.minus(horas(3.2)),
                    120);

            OpDoDia leite = new OpDoDia(PAO_DE_LEITE, 40, null, null, BETO, false);
            long andamento = inserirOp(leite, agora.plus(horas(1)), "EM_ANDAMENTO", agora.minus(horas(0.75)), null,
                    null);
            inserirPerda(andamento, leite, ficha(PAO_DE_LEITE).perdas().get(0), 3, agora.minus(horas(0.25)));

            inserirOp(new OpDoDia(BOLO, 32, null, null, CARLA, false), agora.minus(horas(0.33)), "PENDENTE", null,
                    null, null);
            inserirOp(new OpDoDia(PAO_FRANCES, 120, null, null, BETO, false), agora.plus(horas(4)), "PENDENTE", null,
                    null, null);
            inserirOp(new OpDoDia(PAO_DE_QUEIJO, 80, null, null, DIEGO, false), agora.plus(horas(6)), "PENDENTE",
                    null, null, null);
        }

        private long inserirOp(OpDoDia op, Instant entrega, String status, Instant inicio, Instant conclusao,
                Integer produzida) {
            ops++;
            return inserir("ordens_producao", colunas("ficha_tecnica_id", fichas.get(op.ficha()),
                    "quantidade_unidades", op.unidades(), "horario_entrega", Timestamp.from(entrega), "status", status,
                    "iniciada_em", inicio == null ? null : Timestamp.from(inicio),
                    "concluida_em", conclusao == null ? null : Timestamp.from(conclusao),
                    "quantidade_produzida", produzida, "responsavel_id", funcionarios.get(op.responsavel()),
                    "criado_por_id", gerente, "versao", 0L));
        }

        /** O peso sai das unidades perdidas × peso de uma unidade da receita (massa ÷ rendimento). */
        private void inserirPerda(long op, OpDoDia origem, PerdaTipica tipo, int unidadesPerdidas, Instant quando) {
            perdas++;
            BigDecimal pesoKg = kgPorUnidade.get(origem.ficha()).multiply(BigDecimal.valueOf(unidadesPerdidas))
                    .setScale(3, RoundingMode.HALF_UP).max(new BigDecimal("0.001"));
            inserir("registros_perda", colunas("ordem_producao_id", op, "etapa", tipo.etapa(), "peso_kg", pesoKg,
                    "unidades", unidadesPerdidas, "motivo", tipo.motivo(),
                    "registrado_por_id", funcionarios.get(origem.responsavel()), "registrado_em", Timestamp.from(quando)));
        }

        private long funcionario(String nome, String email, boolean ativo, String hash) {
            List<Long> existente = jdbc.queryForList("SELECT id FROM usuarios WHERE email = ?", Long.class, email);
            if (!existente.isEmpty()) {
                return existente.get(0);
            }
            return inserir("usuarios", colunas("nome", nome, "email", email, "senha_hash", hash,
                    "perfil", "FUNCIONARIO", "ativo", ativo));
        }
    }

    private static FichaDemo ficha(String nome) {
        return FICHAS.stream().filter(f -> f.nome().equals(nome)).findFirst().orElseThrow();
    }

    private static Duration horas(double horas) {
        return Duration.ofMinutes(Math.round(horas * 60));
    }

    /** g e ml viram kg (1 ml = 1 g); ingrediente em UN não entra no peso, como no custo das perdas. */
    private static BigDecimal emKg(BigDecimal quantidade, String unidade) {
        return switch (unidade) {
            case "G", "ML" -> quantidade.divide(MIL);
            case "KG", "L" -> quantidade;
            default -> BigDecimal.ZERO;
        };
    }

    /** Pares coluna, valor; valores nulos ficam de fora (a coluna fica NULL). */
    private static Map<String, Object> colunas(Object... pares) {
        Map<String, Object> mapa = new LinkedHashMap<>();
        for (int i = 0; i < pares.length; i += 2) {
            if (pares[i + 1] != null) {
                mapa.put((String) pares[i], pares[i + 1]);
            }
        }
        return mapa;
    }

    private long inserir(String tabela, Map<String, Object> colunas) {
        return new SimpleJdbcInsert(jdbc)
                .withTableName(tabela)
                .usingColumns(colunas.keySet().toArray(String[]::new))
                .usingGeneratedKeyColumns("id")
                .executeAndReturnKey(colunas)
                .longValue();
    }

    private record InsumoDemo(String nome, String unidade, String custo, boolean ativo) {
    }

    private record Ingrediente(String insumo, String quantidade, boolean farinha) {
    }

    private record Passo(String etapa, String descricao, int minutos) {
    }

    private record PerdaTipica(String etapa, String motivo) {
    }

    private record FichaDemo(String nome, int rendimento, List<Ingrediente> ingredientes, List<Passo> passos,
            List<PerdaTipica> perdas) {
    }

    private record OpDoDia(String ficha, int unidades, LocalTime entrega, Duration duracao, String responsavel,
            boolean diasPares) {
    }
}
