# Pendências

O que ficou para depois, em ordem de prioridade. Ao terminar um item, apague daqui e atualize o README.

## Publicar

- [ ] **API e painel na Hostinger.** O `main` tem duas levas que a produção ainda não tem:
  - lista de convidados da reserva (migração `005-convidados-da-reserva.sql`);
  - pedidos de cadastro no app: a notificação leva o prédio (`lib/directory.php`), a plataforma vê administradora sem administrador (`lib/companies.php`, `painel/js/views/administradoras.js`, `painel/css/painel.css`).

  Passos:
  1. Enviar o pacote completo `backend/dist/api-condomini-completa.zip` (gerado em 16/11, já com as duas levas) e descompactar por cima da pasta `api-condomini`.
  2. Liberar o IP em hPanel → MySQL remoto, rodar `php scripts/install.php` apontando para a produção (cria a coluna `reservations.guests`) e remover a liberação.
  3. Conferir com login: reservar com convidados como morador e ver a lista na Agenda do porteiro; no painel da plataforma, abrir Administradoras.
- [ ] **Versão nova do app** para quem usa: a tela de pedidos no app do administrador, a lista de convidados e a faixa de teste só chegam aos celulares com um APK novo (ou pela Play Store, quando publicar).

## Pedidos de cadastro

- [ ] **Refazer o teste na produção** depois de publicar: instalar no emulador o app normal (sem `-Pcondomini.apiUrl`; sem a faixa âmbar no topo), pedir uma conta no Testadores e conferir que aparece para a Ceci, no app e no painel, e que a notificação chega para ela.
- [ ] **Decidir se o síndico também aprova** pedidos do próprio prédio. Hoje só a administradora (e a plataforma) aprovam.
