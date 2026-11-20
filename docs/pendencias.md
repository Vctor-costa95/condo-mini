# Pendências

O que ficou para depois, em ordem de prioridade. Ao terminar um item, apague daqui e atualize o README.

## Publicar

- [ ] **Apagar o boleto de teste do apto 906 do Testadores** (R$ 450, vence 10/12; sobrou do teste de envio de 20/11): no phpMyAdmin, `DELETE FROM bills WHERE id = '51fa38cd-6250-4227-93b2-f43c77c24efa';`. O painel não tem como excluir boleto, só o PDF.

- [ ] **Versão 1.2 com os testadores.** APK gerado de novo em 20/11 a partir do `main` (`app/build/outputs/apk/release/MiniCondo-1.2.apk`, código 3, chave de release, API de produção) e sendo enviado aos testadores: redução de fotos, marca da administradora, chegada de convidados, "Esqueci a senha" e baixa de boletos. Na Hostinger já estão a migração 006, a API nova e o e-mail por SMTP. Falta testar no celular:
  1. marcar uma chegada numa reserva de hoje;
  2. subir um retorno de verdade do banco em Unidades → Retorno do banco (a prévia não grava nada);
  3. conferir que as notificações chegam (a chave `push` do `config.php` ainda não foi testada na produção);
  4. abrir uma foto de encomenda ou um boleto enviado antes de 20/11, para saber se os arquivos antigos foram para a pasta nova.

  O `config.php` do servidor foi trocado em 20/11: um PDF de 10 MB enviado pelo painel foi gravado e lido de volta idêntico na pasta nova.
- [ ] **Trocar o nome da conta da plataforma** em produção ("Equipe Condo-mini" → "Equipe Mini Condo") pelo painel.

## Pedidos de cadastro

- [ ] **Refazer o teste na produção:** com o app normal (sem a faixa âmbar no topo), pedir uma conta no Testadores e conferir que aparece para a Ceci (no app e no painel) e para o síndico do Testadores, se houver, e que a notificação chega para os dois.
