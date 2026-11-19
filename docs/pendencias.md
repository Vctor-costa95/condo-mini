# Pendências

O que ficou para depois, em ordem de prioridade. Ao terminar um item, apague daqui e atualize o README.

## Corrigir (17/11)

- [ ] **"Arquivo grande demais" ao enviar PDF pesado (admin) ou foto pesada (porteiro).** A mensagem sai de `store_upload()` em `backend/lib/files.php`, em dois casos:
  1. o arquivo passa do limite do PHP da Hostinger (`upload_max_filesize` / `post_max_size`: o PHP recusa antes do nosso código, erro `UPLOAD_ERR_INI_SIZE`);
  2. o arquivo passa do `max_upload_bytes` do `config.php` do servidor (o modelo usa 15 MB; o `config.php` foi refeito em 16/11, conferir se a linha está lá: sem ela, a comparação com `null` recusa qualquer arquivo).

  Pistas para resolver:
  - **Fotos:** o app envia a foto do jeito que sai da câmera, sem reduzir (não há compressão em `ui/components/Photos.kt` nem nos repositórios). Foto de celular passa fácil de 5–10 MB. Reduzir no app antes de enviar (ex.: lado maior 1600–2000 px, JPEG ~80%) resolve para o porteiro e para ocorrências, e deixa o envio mais rápido no 4G.
  - **PDFs:** não dá para reduzir no app; ver os limites do PHP no hPanel (Avançado → Configuração do PHP) e subir `upload_max_filesize`/`post_max_size` (ex.: 32 MB), alinhando com `max_upload_bytes`.
  - Mostrar na mensagem o limite ("até 15 MB") para a pessoa saber o que fazer.
  - Reproduzir primeiro: enviar um PDF de ~10 MB pelo painel e pelo app e uma foto da câmera do emulador como porteiro, olhando a resposta da API.

## Publicar

- [ ] **Completar o `config.php` do servidor.** Desde 16/11 ele usa `'storage_dir' => __DIR__ . '/storage'` e pode estar sem a chave `push`. Troque para `'storage_dir' => '/home/u248494375/condomini-storage'` (senão fotos de encomendas, PDFs e boletos já enviados somem do app) e confira o `'push'` apontando para o JSON do Firebase. O modelo completo está no `backend/README.md`, seção Hostinger.
- [ ] **Trocar o nome da conta da plataforma** em produção ("Equipe Condo-mini" → "Equipe Mini Condo") pelo painel.

## Pedidos de cadastro

- [ ] **Refazer o teste na produção:** com o app normal (sem a faixa âmbar no topo), pedir uma conta no Testadores e conferir que aparece para a Ceci (no app e no painel) e para o síndico do Testadores, se houver, e que a notificação chega para os dois.
