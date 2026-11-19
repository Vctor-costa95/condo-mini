# Pendências

O que ficou para depois, em ordem de prioridade. Ao terminar um item, apague daqui e atualize o README.

## Corrigir (17/11)

- [ ] **"Arquivo grande demais": fechar o teste.** O código está resolvido e publicado (18/11): o app reduz as fotos antes de enviar e a API mostra o limite na mensagem. Na Hostinger já estão a API nova e `'max_upload_bytes' => 30 * 1024 * 1024`; o `post_max_size` do servidor já passa de 35 MB (um envio de 35 MB sem login chegou até a checagem de login). Falta:
  1. testar com um PDF de ~10 MB pelo painel. Se der erro, a mensagem mostra o limite real; se for menor que 30 MB, é o `upload_max_filesize` do hPanel (Avançado → Configuração do PHP), que deve ir para 32M;
  2. gerar um APK novo (1.2) para os testadores receberem a redução de fotos.

## Publicar

- [ ] **Chegada de convidados, "Esqueci a senha" e baixa de boletos (18/11).** No `main`, testado localmente; na Hostinger, nesta ordem:
  1. no phpMyAdmin, rodar `backend/sql/migrations/006-chegadas-senha-e-baixa.sql` e registrar: `INSERT INTO schema_migrations (name, applied_at) VALUES ('006-chegadas-senha-e-baixa.sql', NOW());`
  2. enviar o pacote completo `backend/dist/api-condomini-sem-instalador.zip` (sem mexer no `config.php`);
  3. criar uma caixa de e-mail no hPanel (ex.: nao-responda@ no domínio) e pôr `'mail' => ['from' => '<essa caixa>', 'from_name' => 'Mini Condo']` no `config.php`. Sem isso, o "Esqueci a senha" responde para falar com a administração;
  4. testar: pedir o código para uma conta com o seu e-mail (conferir também se não cai no spam), marcar uma chegada numa reserva de hoje, e subir um retorno de verdade do banco do condomínio em Unidades → Retorno do banco (a prévia não grava nada).
  5. o APK 1.2 leva as telas novas para os celulares.
- [ ] **Completar o `config.php` do servidor.** Desde 16/11 ele usa `'storage_dir' => __DIR__ . '/storage'` e pode estar sem a chave `push`. Troque para `'storage_dir' => '/home/u248494375/condomini-storage'` (senão fotos de encomendas, PDFs e boletos já enviados somem do app) e confira o `'push'` apontando para o JSON do Firebase. O modelo completo está no `backend/README.md`, seção Hostinger.
- [ ] **Trocar o nome da conta da plataforma** em produção ("Equipe Condo-mini" → "Equipe Mini Condo") pelo painel.

## Pedidos de cadastro

- [ ] **Refazer o teste na produção:** com o app normal (sem a faixa âmbar no topo), pedir uma conta no Testadores e conferir que aparece para a Ceci (no app e no painel) e para o síndico do Testadores, se houver, e que a notificação chega para os dois.
