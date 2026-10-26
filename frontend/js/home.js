// Página inicial. Dona: dupla 1.
import { config } from './core/config.js';
import { exigirLogin } from './core/auth.js';
import { renderizarMenu } from './core/menu.js';

const sessao = exigirLogin();

if (sessao) {
  renderizarMenu(document.getElementById('menu'), sessao.usuario.perfil);
  document.getElementById('usuario').textContent = `${sessao.usuario.nome} (${sessao.usuario.perfil})`;
  document.getElementById('api-url').textContent = config.apiUrl;
  document.getElementById('api-retry').addEventListener('click', verificarApi);

  if (new URLSearchParams(location.search).has('acessoNegado')) {
    document.getElementById('aviso').hidden = false;
  }
  verificarApi();
}

function mostrarStatus(texto, classe) {
  const elemento = document.getElementById('api-status');
  elemento.textContent = texto;
  elemento.className = `status status--${classe}`;
}

async function verificarApi() {
  mostrarStatus('Verificando…', 'checking');
  try {
    const resposta = await fetch(`${config.apiUrl}/actuator/health`);
    const corpo = await resposta.json();
    if (resposta.ok && corpo.status === 'UP') {
      mostrarStatus('API conectada', 'ok');
    } else {
      mostrarStatus(`API respondeu, mas não está saudável (${corpo.status ?? resposta.status})`, 'erro');
    }
  } catch {
    mostrarStatus('API indisponível. O backend está rodando?', 'erro');
  }
}
