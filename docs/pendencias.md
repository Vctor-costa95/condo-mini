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

- [ ] **Mesclar a versão 1.1 no `main`.** A branch `claude/versao-1-1` tem `versionCode 2` / `versionName "1.1"` e o Perfil mostrando a versão real. O APK de release 1.1 já foi gerado a partir dela (`app/build/outputs/apk/release/MiniCondo-1.1.apk`) e foi compartilhado com os testadores.
- [ ] **Completar o `config.php` do servidor.** Desde 16/11 ele usa `'storage_dir' => __DIR__ . '/storage'` e pode estar sem a chave `push`. Troque para `'storage_dir' => '/home/u248494375/condomini-storage'` (senão fotos de encomendas, PDFs e boletos já enviados somem do app) e confira o `'push'` apontando para o JSON do Firebase. O modelo completo está no `backend/README.md`, seção Hostinger.
- [ ] **Trocar o nome da conta da plataforma** em produção ("Equipe Condo-mini" → "Equipe Mini Condo") pelo painel.

## Pedidos de cadastro

- [ ] **Refazer o teste na produção:** com o app normal (sem a faixa âmbar no topo), pedir uma conta no Testadores e conferir que aparece para a Ceci (no app e no painel) e para o síndico do Testadores, se houver, e que a notificação chega para os dois.

## Diferencial (feedback de 18/11: "o app não tem nada demais")

Avisos, reservas, boletos, encomendas, ocorrências e documentos já são o básico do mercado (Superlógica, uCondo, TownSq). O que nos diferencia precisa ser o que a administradora (quem paga) não copia fácil e consegue medir. O "Arquivo grande demais" (acima) vem antes de tudo, porque quebra o envio de boletos, que é o nosso diferencial.

### Landing (appcondomini.lovable.app)

- [ ] **Tirar ou corrigir "a taxa vai direto para o boleto".** Não é verdade hoje: a taxa só deixa a reserva pendente para o síndico (`backend/lib/community.php`); o boleto vem do PDF da administradora. Aparece em 3 lugares (card "Reservas no caderno", passo 02 e FAQ). Trocar por "reserva com taxa passa pela aprovação do síndico", ou implementar a taxa no boleto antes.
- [ ] **Mostrar o envio de boletos em lote como destaque**, com GIF da tela de conferência: solta o PDF do sistema de cobrança, o painel lê a linha digitável, acha o apartamento e só mostra as exceções. Hoje a landing nem cita.
- [ ] **Mostrar o painel web da administradora** (hoje só aparecem telas de morador, síndico e portaria; quem paga não vê a tela dela).
- [ ] Citar também: marca da administradora, lista de convidados na portaria, funciona sem internet, síndico aprova cadastros do próprio prédio.

### Funcionalidades, em ordem de prioridade

- [ ] **1. Marca da administradora no app Android (fase 3 de `docs/plano-administradoras.md`).** É a resposta direta ao "a administradora pode copiar": ela não precisa, já ganha o app com a marca dela (white-label), sem desenvolver nem manter.
- [ ] **2. Lembrete de vencimento por push.** Quase de graça: o vencimento já sai da linha digitável e o Firebase já está ligado. Avisar 3 dias antes, no dia e depois de vencido (um cron na API). Transforma "reduz inadimplência" de promessa em funcionalidade.
- [ ] **3. Painel com visão da carteira.** Tela inicial do painel com um semáforo por condomínio: boletos vencidos, ocorrências abertas e há quanto tempo, pedidos esperando o síndico, prédio sem síndico. Os dados já estão no banco. Depois, relatório mensal automático para síndico e conselho.
- [ ] **4. Fechar o ciclo da cobrança.**
  - Baixa automática do boleto (retorno CNAB do banco ou API da Superlógica e outros). Integração com o sistema de cobrança também é barreira contra cópia.
  - Pix com baixa automática.
  - Acordo de inadimplência pelo app: o morador simula e fecha o parcelamento sozinho.
  - Meta de venda: poder dizer "reduz X% da inadimplência" com número de cliente real.
- [ ] **5. Check-in de convidados na portaria** e aviso ao morador quando alguém da lista chega (a lista vira controle de entrada).
- [ ] **6. IA com os documentos de cada condomínio.** O morador pergunta ("posso fazer obra no sábado?", "posso ter cachorro grande?") e a resposta vem do regimento e da convenção daquele prédio (os PDFs já estão guardados). Sem resposta, abre ocorrência. Avaliar também pelo WhatsApp, porque muito morador não instala app. Concorrentes já têm IA genérica (Móra da uCondo, Sindia do CondoConta); o diferencial é ser do prédio.
- [ ] **7. Assembleia digital.** Lista de presença, quórum, voto por fração ideal e ata gerada no fim. Muito difícil de trocar de sistema depois. Confirmar com advogado os requisitos da assembleia virtual antes de prometer validade jurídica.
- [ ] **8. Laudos e manutenção obrigatória com vencimento.** AVCB, elevador, para-raios, limpeza da caixa d'água, com alerta antes de vencer. Tira risco legal do síndico.
- [ ] **9. Prestação de contas para o morador.** Para onde foi o dinheiro, por categoria, com as notas anexadas. Gera confiança e ajuda a administradora a conquistar prédio novo.
- [ ] **10. Parceiros com comissão para a administradora.** Dedetização, caixa d'água, seguro etc. Ela passa a ganhar dinheiro com o app, e copiar deixa de compensar.
