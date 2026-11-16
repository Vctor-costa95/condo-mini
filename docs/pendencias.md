# Pendências

O que ficou para depois, em ordem de prioridade. Ao terminar um item, apague daqui e atualize o README.

## Publicar

- [ ] **Lista de convidados na Hostinger.** O `main` já tem a função (app + API), mas a produção ainda não.
  1. Gerar de novo e enviar o pacote completo da API (`backend/dist/api-condomini-completa.zip`; o que está lá é anterior à função): mudaram `lib/community.php`, `lib/setup.php`, `sql/schema.sql` e entrou `sql/migrations/005-convidados-da-reserva.sql`.
  2. Liberar o IP em hPanel → MySQL remoto, rodar `php scripts/install.php` apontando para a produção (cria a coluna `reservations.guests`) e remover a liberação.
  3. Conferir no app: reservar com convidados como morador e ver a lista na Agenda do porteiro.

## Pedidos de cadastro

Do teste de 15/11: o pedido do `jaojao` "sumiu" porque o app do emulador estava ligado à API local de teste, não à produção. A produção estava certa (Testadores na "Administradora testes", Ceci administradora dela).

- [ ] **Refazer o teste na produção.** Reinstalar no emulador o app normal (sem `-Pcondomini.apiUrl`), pedir uma conta no Testadores e conferir que aparece para a Ceci em Cadastros → Pedidos de cadastro e que a notificação chega para ela.
- [ ] **Deixar claro quando o app está ligado a uma API que não é a de produção.** Hoje o app de teste é idêntico ao de verdade, e foi isso que confundiu. Ideia: uma faixa discreta "API local" no login e no topo, só quando o endereço da API não for o de produção (nunca aparece no app publicado).
- [ ] **Aprovar pedidos de cadastro pelo app.** Hoje só o painel web mostra os pedidos; o administrador não consegue aceitar pelo celular. Criar a tela (lista, aceitar trocando o apartamento se precisar, recusar com motivo), como no painel.
- [ ] **Notificação de pedido novo abrir a tela certa.** A API manda `tab = signup_requests`, mas o app não tem essa aba; tocar na notificação não leva a lugar nenhum. Resolve junto com o item acima.
- [ ] **Pedido sem ninguém para aprovar.** Se a administradora do prédio não tem nenhum administrador, o pedido fica parado sem aviso. Avisar a plataforma nesse caso, ou mostrar o alerta na tela de Administradoras do painel da plataforma.
- [ ] **Decidir se o síndico também aprova** pedidos do próprio prédio. Hoje só a administradora (e a plataforma) aprovam.
