import { exigirLogin } from '../../js/core/auth.js';
import { renderizarMenu } from '../../js/core/menu.js';

const sessao = exigirLogin(['GERENTE']);
if (sessao) {
  renderizarMenu(document.getElementById('menu'), sessao.usuario.perfil);
  // Dupla 3: implemente a tela aqui (RF008). Chamadas à API: apiFetch() de js/core/api.js.
}
