const { apiUrl } = window.SMARTPROD_CONFIG;
const statusEl = document.getElementById('api-status');

document.getElementById('api-url').textContent = apiUrl;
document.getElementById('api-retry').addEventListener('click', verificarApi);

function mostrarStatus(texto, classe) {
  statusEl.textContent = texto;
  statusEl.className = `status status--${classe}`;
}

async function verificarApi() {
  mostrarStatus('Verificando…', 'checking');
  try {
    const resposta = await fetch(`${apiUrl}/actuator/health`);
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

verificarApi();
