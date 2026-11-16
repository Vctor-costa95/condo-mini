# Pendências

O que ficou para depois, em ordem de prioridade. Ao terminar um item, apague daqui e atualize o README.

## Publicar

- [ ] **Registrar a migração 005 no banco.** A coluna `reservations.guests` foi criada à mão (a API foi publicada em 16/11 sem o instalador). Rode no phpMyAdmin `INSERT INTO schema_migrations (name, applied_at) VALUES ('005-convidados-da-reserva.sql', NOW());`, senão um `install.php` futuro tenta criar a coluna de novo e para.
- [ ] **Versão nova do app** para quem usa: a tela de pedidos no app do administrador, a lista de convidados e a faixa de teste só chegam aos celulares com um APK novo (ou pela Play Store, quando publicar).

## Pedidos de cadastro

- [ ] **Refazer o teste na produção** depois de publicar: no emulador com o app normal (sem a faixa âmbar no topo), pedir uma conta no Testadores e conferir que aparece para a Ceci (no app e no painel) e para o síndico do Testadores, se houver, e que a notificação chega para os dois.
