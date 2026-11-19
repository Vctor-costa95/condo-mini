# Pendências

O que ficou para depois, em ordem de prioridade. Ao terminar um item, apague daqui e atualize o README.

## Corrigir (17/11)

- [ ] **"Arquivo grande demais": falta a parte do servidor.** O código está resolvido (18/11): o app reduz as fotos antes de enviar e a API mostra o limite na mensagem, inclusive quando o PDF passa do `post_max_size` (antes respondia "Escolha um condomínio"). Para fechar:
  1. publicar a API nova (zip completo, ver README);
  2. no hPanel (Avançado → Configuração do PHP), subir `upload_max_filesize` e `post_max_size` para 32M;
  3. no `config.php` do servidor, pôr `'max_upload_bytes' => 30 * 1024 * 1024`;
  4. gerar um APK novo (1.2) para os testadores receberem a redução de fotos;
  5. testar com um PDF de ~10 MB pelo painel.

## Publicar

- [ ] **Completar o `config.php` do servidor.** Desde 16/11 ele usa `'storage_dir' => __DIR__ . '/storage'` e pode estar sem a chave `push`. Troque para `'storage_dir' => '/home/u248494375/condomini-storage'` (senão fotos de encomendas, PDFs e boletos já enviados somem do app) e confira o `'push'` apontando para o JSON do Firebase. O modelo completo está no `backend/README.md`, seção Hostinger.
- [ ] **Trocar o nome da conta da plataforma** em produção ("Equipe Condo-mini" → "Equipe Mini Condo") pelo painel.

## Pedidos de cadastro

- [ ] **Refazer o teste na produção:** com o app normal (sem a faixa âmbar no topo), pedir uma conta no Testadores e conferir que aparece para a Ceci (no app e no painel) e para o síndico do Testadores, se houver, e que a notificação chega para os dois.
