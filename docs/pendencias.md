# Pendências

O que ficou para depois, em ordem de prioridade. Ao terminar um item, apague daqui e atualize o README.

## Publicar

- [ ] **Correções de segurança de 20/11.** A portaria deixou de ver boletos e ocorrências, o e-mail do cadastro passou a ser validado e o "Esqueci a senha" ficou limitado a 5 códigos por dia, com as tentativas contadas de uma vez. **Nesta ordem:**
  1. No phpMyAdmin, rode `backend/sql/migrations/007-limite-de-codigos.sql` e depois `INSERT INTO schema_migrations (name, applied_at) VALUES ('007-limite-de-codigos.sql', NOW());`. Sem a migração, o "Esqueci a senha" dá erro com a API nova.
  2. Envie `backend/dist/api-condomini-completa.zip` pelo Gerenciador de Arquivos e extraia por cima.
  3. Entre no painel para conferir que o login funciona.

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

## Crescer

- [ ] **Etapa 1 do [plano de capacidade](plano-capacidade.md) antes dos primeiros prédios reais:** push silencioso no lugar da consulta a cada 30 s, respostas 304, listas paginadas, menos consultas por item e os índices que faltam.
