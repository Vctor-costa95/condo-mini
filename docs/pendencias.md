# Pendências

O que ficou para depois, em ordem de prioridade. Ao terminar um item, apague daqui e atualize o README.

## Publicar

- [ ] **Apagar o boleto de teste do apto 906 do Testadores** (R$ 450, vence 10/12; sobrou do teste de envio de 20/11): no phpMyAdmin, `DELETE FROM bills WHERE id = '51fa38cd-6250-4227-93b2-f43c77c24efa';`. O painel não tem como excluir boleto, só o PDF.

- [ ] **Versão 1.2 para os testadores.** APK gerado em 18/11 (`app/build/outputs/apk/release/MiniCondo-1.2.apk`): redução de fotos, marca da administradora, chegada de convidados, "Esqueci a senha" e baixa de boletos. Na Hostinger já estão a migração 006, a API nova e o e-mail por SMTP (o código chega com o visual do app). Falta compartilhar o APK e testar no celular: marcar uma chegada numa reserva de hoje e subir um retorno de verdade do banco em Unidades → Retorno do banco (a prévia não grava nada).
- [ ] **Completar o `config.php` do servidor.** Desde 16/11 ele usa `'storage_dir' => __DIR__ . '/storage'` e pode estar sem a chave `push`. Troque para `'storage_dir' => '/home/u248494375/condomini-storage'` (senão fotos de encomendas, PDFs e boletos já enviados somem do app) e confira o `'push'` apontando para o JSON do Firebase. O modelo completo está no `backend/README.md`, seção Hostinger.
- [ ] **Trocar o nome da conta da plataforma** em produção ("Equipe Condo-mini" → "Equipe Mini Condo") pelo painel.

## Pedidos de cadastro

- [ ] **Refazer o teste na produção:** com o app normal (sem a faixa âmbar no topo), pedir uma conta no Testadores e conferir que aparece para a Ceci (no app e no painel) e para o síndico do Testadores, se houver, e que a notificação chega para os dois.
