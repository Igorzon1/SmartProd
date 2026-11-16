package br.com.smartprod.receita;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import br.com.smartprod.auth.TokenService;
import br.com.smartprod.insumo.Insumo;
import br.com.smartprod.insumo.InsumoRepository;
import br.com.smartprod.insumo.UnidadeMedida;
import br.com.smartprod.usuario.Perfil;
import br.com.smartprod.usuario.Usuario;
import br.com.smartprod.usuario.UsuarioRepository;
import jakarta.persistence.EntityManager;
