# API do Mini Condo

API REST em PHP 8 + MySQL, sem dependências externas, feita para a hospedagem compartilhada da Hostinger (plano Premium). O app Android conversa só com ela; a senha do banco nunca sai do servidor.

```
App Android  →  HTTPS/JSON  →  api-condomini.tcpneus-logistica.com  →  MySQL (127.0.0.1:3306)
```

## Estrutura

| Caminho | O que é |
|---|---|
| `index.php` | Ponto de entrada; o `.htaccess` manda toda requisição para cá |
| `lib/core.php` | Configuração, PDO, roteador, sessão e regras de acesso por perfil |
| `lib/directory.php` | Login, pessoas e condomínios (blocos e apartamentos) |
| `lib/companies.php` | Administradoras, logo e alocação de condomínios (plataforma) |
| `lib/community.php` | Avisos, espaços comuns, reservas, mudanças e reparos |
| `lib/records.php` | Boletos, encomendas, ocorrências e documentos |
| `lib/files.php` | Upload e download de fotos e PDFs (fora do alcance público) |
| `lib/setup.php`, `scripts/install.php` | Cria as tabelas e os dados de demonstração |
| `sql/schema.sql` | Esquema do banco |
| `config.php` | Credenciais (fora do git; modelo em `config.example.php`) |

## Autenticação

`POST /auth/login` com `{"login", "password"}` devolve `{"token", "user"}`. As demais rotas exigem `Authorization: Bearer <token>`. O banco guarda só o SHA-256 do token; `POST /auth/logout` o invalida. Quem criou a conta pelo app e ainda não foi aceito recebe 403 com a situação do pedido (aguardando ou recusado, com o motivo), só se acertar a senha.

Cada perfil só vê o próprio condomínio; o administrador escolhe com `?condoId=` (ou `condoId` no corpo). Morador só lê e altera os próprios apartamentos. O código de retirada de encomendas só aparece para o morador.

### Administradoras e plataforma

```
Plataforma (PLATFORM, a equipe Mini Condo) → Administradora → Condomínios → síndico, portaria, moradores
```

- O **administrador** (`ADMIN`) é de uma administradora (`users.company_id`) e só vê os condomínios dela (`condominiums.company_id`): `condo_id()`, `require_same_condo()` e `editable_user()` passam por `can_access_condo()`. Um condomínio de outra administradora responde como se não existisse. Ele edita nome, endereço e blocos, mas não cria condomínio.
- A **plataforma** (`PLATFORM`) passa em toda rota de administrador, em qualquer condomínio, e é a única que cadastra administradoras, sobe a logo, cria condomínios e os aloca.
- Administradora com `active = false` fica suspensa: ninguém dela entra (nem administradores nem quem é dos condomínios dela).
- `GET /me` e o login trazem `brand: {companyId, name, logoUrl, color}`, a marca da administradora de quem entrou, para o app e o painel mostrarem no lugar da nossa. Sem administradora, ou sem logo, `logoUrl` vem `null`.
- Cadastro da plataforma na produção: `php scripts/platform-user.php <usuário> "<Nome>" --sql` no seu computador pede a senha e imprime o `INSERT` (só com o hash) para colar no phpMyAdmin. Sem `--sql`, conecta no banco do `config.php` e cria ou troca a senha. Use usuário sem acento. Na demonstração, a conta de teste `plataforma` entra sem senha.

Contas de teste sem senha (`morador`, `sindico`, `porteiro`, `admin`, `plataforma`) só entram com `allow_passwordless_logins => true` e só num banco recém-semeado: na produção elas já têm senha (ver o README principal). Os demais cadastros de demonstração usam `nome.sobrenome` / `condo123`. **Desligue a opção e troque as senhas antes de cadastrar moradores de verdade.**

## Rotas

Respostas em JSON; erros vêm como `{"error": "mensagem"}` com status 4xx. Datas em `AAAA-MM-DD`, data e hora em `AAAA-MM-DDTHH:MM:SS` (horário de Brasília), enums com o mesmo nome do app (`PENDING`, `MORNING`...). O prefixo `/v1` é opcional.

| Rota | Quem |
|---|---|
| `POST /auth/login`, `POST /auth/logout`, `GET /me`, `PATCH /me` | todos |
| `POST /auth/password-reset` `{login}`, `POST /auth/password-reset/confirm` `{login, code, password}` | sem login: "Esqueci a senha". Manda um código de 6 dígitos para o e-mail do cadastro (vale 15 min, 5 tentativas, um pedido por minuto) e devolve o e-mail mascarado; o código certo troca a senha e encerra as sessões. Sem `mail` no `config.php`, responde 503; cadastro sem e-mail, 422 (`lib/recovery.php`) |
| `GET /condos`, `POST /condos` | todos leem os seus / admin salva os da administradora; só a plataforma cria (com `companyId`) |
| `PUT /condos/{id}/company` (`{"companyId"}`, vazio desaloca) | plataforma |
| `GET /companies`, `POST /companies`, `DELETE /companies/{id}` (só sem condomínios) | plataforma |
| `POST /companies/{id}/logo` (multipart: PNG, WebP ou JPEG, até 1 MB, mínimo 128 px), `DELETE /companies/{id}/logo` | plataforma |
| `GET /companies/{id}/logo` | pública (a URL vem em `brand.logoUrl`, com `?v=` que muda a cada troca) |
| `GET /users`, `POST /users`, `DELETE /users/{id}` | admin, síndico e portaria leem (a plataforma, com `?companyId=`, lista os administradores daquela administradora) / admin salva (administrador novo fica na administradora de quem cria; a plataforma informa `companyId`) |
| `POST /users/{id}/units`, `DELETE /users/{id}/units?unit=` | admin |
| `GET /logins/available?login=&exceptUserId=` | admin (considera também pedidos de cadastro pendentes) |
| `GET /public/condos` | sem login: prédios e blocos para quem vai criar a conta (sem moradores) |
| `POST /signup` `{condoId, name, email, phone, login, password, unit}` | sem login: pedido de cadastro do morador; até 5 pendentes por endereço por hora |
| `GET /signup-requests?condoId=`, `POST /signup-requests/{id}/approve` `{unit?}`, `POST /signup-requests/{id}/reject` `{reason}` | admin (prédios da administradora dele; no app, em Cadastros, e no painel) e síndico (o próprio prédio; no app, em Aprovações): aceitar vira morador com a senha que a pessoa escolheu. A notificação de pedido novo leva `condoId`, para o app abrir no prédio certo. `GET /companies` traz `unattendedSignups` (pedidos em prédios sem síndico), para a plataforma ver pedidos parados em administradora sem administrador |
| `GET /notices`, `POST /notices`, `DELETE /notices/{id}` | todos leem / síndico e admin publicam |
| `GET /areas`, `POST /areas`, `DELETE /areas/{id}`, `PUT /areas/{id}/closed` | todos leem / síndico e admin editam |
| `GET /areas/{id}/booked?date=` | todos |
| `GET /reservations[?unit=]`, `POST /reservations` | morador com `unit`; síndico e portaria sem |
| `GET /reservations?history=1[&unit=]`, `GET /service-bookings?history=1[&unit=]` | histórico com todos os status e datas, mais recente primeiro: morador com `unit`; síndico e admin sem (condomínio todo) |
| `POST /reservations/{id}/cancel\|approve\|reject` | morador cancela / síndico aprova, recusa e cancela (com `reason` obrigatório) |
| `POST /reservations/{id}/arrivals` `{name}`, `POST /reservations/{id}/arrivals/undo` `{name}` | portaria, síndico e admin, só no dia de uma reserva confirmada: marca (ou desfaz) a chegada de um nome da lista (sem diferença de maiúsculas e espaços). A primeira marcação avisa os moradores do apartamento. `reservation_json` traz `arrivals`: `[{name, arrivedAt}]`, em ordem de chegada |
| `PUT /reservations/{id}/guests` | morador do apartamento (ou síndico e admin) troca a lista inteira de convidados (`guests`: lista de nomes), enquanto a reserva está pendente ou confirmada e não passou. O `POST /reservations` também aceita `guests`. Nomes aparados, sem repetidos, até a capacidade do espaço (máx. 200); a portaria lê pelo `GET /reservations` |
| `GET /service-bookings[?unit=]`, `GET /service-bookings/taken-moves?date=`, `POST /service-bookings` | idem |
| `POST /service-bookings/{id}/cancel\|approve\|reject` | idem |
| `GET /bills?unit=`, `GET /bills/{id}`, `GET /bills/{id}/pdf` | morador do apartamento. Com `?reference=AAAA-MM` e sem `unit`: admin e síndico listam os boletos do mês no condomínio. `paidVia` diz como foi a baixa (`MANUAL` ou `BANK_RETURN`) |
| `POST /bills/{id}/pay` `{paidAt?}`, `POST /bills/{id}/unpay` | admin: baixa na mão (data de hoje se não vier) e desfaz; a baixa avisa os moradores |
| `POST /bills/bank-return` (multipart: `file`, `preview=1` opcional) | admin: arquivo de retorno do banco, CNAB 240 (segmentos T e U) ou 400. Pagamentos são as ocorrências 06, 07, 08, 15 e 17. Cada um é ligado ao boleto em aberto de mesmo valor cujo campo livre (25 dígitos do meio da linha digitável) tem o trecho mais longo em comum com o nosso número (mínimo 6 dígitos, sem empate). Devolve `paid`, `alreadyPaid`, `unmatched`, `otherEntries` e `applied`; com `preview=1` não grava nada (`lib/bank_return.php`) |
| `GET /unit-documents`, `POST /unit-documents` (multipart: `unit`, `reference`, `file` e, opcional, `barcode`), `DELETE /unit-documents/{id}` | admin. Com `barcode` (a linha digitável, 47 dígitos), a API confere os dígitos verificadores, tira valor e vencimento da própria linha e cria ou atualiza o boleto do mês (`bills`); `amount` e `dueDate` só entram quando a linha não traz. Boleto já pago continua pago. |
| `GET /parcels[?unit=]`, `POST /parcels`, `POST /parcels/{id}/deliver` | morador lê / portaria registra e entrega |
| `GET /occurrences[?unit=]`, `POST /occurrences` | morador registra / síndico vê todas |
| `POST /occurrences/{id}/respond\|request-details` | síndico |
| `POST /occurrences/{id}/reply` | morador |
| `GET /documents`, `GET /documents/{id}`, `POST /documents` (multipart), `DELETE /documents/{id}` | todos leem / síndico publica |
| `POST /files` (multipart, foto), `GET /files/{id}` | todos do condomínio |

Fotos: o app envia a imagem em `POST /files`, recebe `{"id", "url"}` e manda a `url` no registro (`photoUrls` da ocorrência, `photoUrl` da encomenda).

## Notificações push

O app registra o celular em `POST /devices` (`{"token", "platform"}`) depois de entrar ou ao abrir com a sessão salva. Cada aparelho fica ligado à sessão: sair da conta ou excluir o cadastro para os envios. Tokens que o Firebase recusa são apagados sozinhos.

| Evento | Quem recebe |
|---|---|
| Encomenda registrada | moradores do apartamento (sem o código de retirada) |
| Aviso ou documento publicado | todo o condomínio |
| Boleto (PDF) enviado | moradores do apartamento |
| Reserva com taxa, pedido de mudança ou reparo | síndico |
| Reserva, mudança ou reparo aprovado, recusado ou cancelado pelo síndico | moradores do apartamento |
| Ocorrência nova; resposta do morador | síndico |
| Pedido de cadastro feito pelo app | administradores da administradora do prédio e o síndico dele |
| Resposta, pedido de detalhes ou ocorrência resolvida | moradores do apartamento |

Quem faz a ação não recebe a própria notificação. O envio usa a API HTTP v1 do FCM, com o token OAuth assinado em PHP (`openssl`), e roda depois da resposta ao app. Configure `push.service_account_file` no `config.php`; com `push.log_file`, as mensagens vão para um arquivo (usado pelo teste de fumaça).

## Rodar localmente

Requer PHP 8.1+ (`pdo_mysql`, `fileinfo`, `mbstring`) e MySQL 8 ou MariaDB 10.6+.

```bash
cp config.example.php config.php   # e preencha o banco local
php scripts/install.php --seed
php -S 127.0.0.1:8080 index.php
php scripts/smoke-test.php http://127.0.0.1:8080 storage/push-test.log   # com push.log_file apontando para o mesmo arquivo
```

O teste de fumaça cobre login, permissões por perfil e os fluxos de cada tela; ele cria registros, então use um banco de desenvolvimento.

## Painel web da administradora

`painel/` é o painel para usar no computador. Entram dois perfis; os outros são mandados para o app.

- **Administrador (`ADMIN`):** as mesmas funções do perfil no app: condomínios da administradora (editar blocos e andares, trocar), unidades (boletos em PDF do mês, alocar e desalocar moradores), cadastros, pedidos de cadastro e espaços. A barra lateral mostra a marca da administradora (`me.brand`): logo, ou iniciais sobre a cor dela. Não cria condomínio.
- **Plataforma (`PLATFORM`):** telas Administradoras (`js/views/administradoras.js`) e Condomínios (`js/views/condominios.js`), além das telas acima em qualquer condomínio. Usa `GET /users?companyId=` para listar os administradores de cada administradora.

- São só arquivos estáticos (HTML, CSS e JavaScript em módulos, sem framework e sem build), na mesma pasta da API: sem CORS, e o painel chama as mesmas rotas do app.
- Endereço: `<endereço da API>/painel/`. Localmente, `http://127.0.0.1:8080/painel/` (o `index.php` deixa o `php -S` entregar os arquivos do painel).
- O `.htaccess` da API deixa `painel/` passar direto, e o `painel/.htaccess` liga os cabeçalhos de segurança (CSP só com arquivos próprios, sem embutir em outros sites).
- O token fica no navegador (`localStorage` com "Manter conectado", senão `sessionStorage`). PDFs são baixados com o token e abertos numa aba nova.
- Boletos em lote (`js/boletos.js` e `js/views/boletos-lote.js`): o PDF é lido e separado no navegador, com pdf.js e pdf-lib copiados em `painel/js/vendor/` (a CSP não deixa carregar scripts de fora; versões e licenças no README de lá). Para atualizar, troque os arquivos e confira com a CSP ligada.
- A numeração dos apartamentos (`painel/js/units.js`) é a mesma do app (`CondoUnit.kt`) e da API (`tower_unit_labels`): mude as três juntas.

## Hostinger

- Endereço da API: `https://api-condomini.tcpneus-logistica.com/api-condomini` (pasta `public_html/api-condomini/api-condomini`; o app usa este endereço por padrão).
- Banco: `u248494375_condomini` (o `config.php` do servidor usa `127.0.0.1:3306`).
- Arquivos enviados: `/home/u248494375/condomini-storage`, fora do `public_html`.
- Chave do Firebase: `domains/tcpneus-logistica.com/condomini-firebase.json`, fora do `public_html` (configurada em `push.service_account_file`).
- Para atualizar: envie `dist/api-condomini-sem-instalador.zip` (`.htaccess`, `index.php`, `lib/` e `painel/`) pelo Gerenciador de Arquivos e extraia por cima, sem apagar nem sobrescrever o `config.php` do servidor. Painel: `https://api-condomini.tcpneus-logistica.com/api-condomini/painel/`.
- `config.php` do servidor (não está no git; se precisar refazer):
  ```php
  return [
      'db' => ['host' => '127.0.0.1', 'port' => 3306, 'name' => 'u248494375_condomini',
               'user' => 'u248494375_condomini', 'pass' => '<senha do banco>'],
      'storage_dir' => '/home/u248494375/condomini-storage',
      'allow_passwordless_logins' => false,
      'timezone' => 'America/Sao_Paulo',
      'max_upload_bytes' => 15 * 1024 * 1024,
      'push' => ['service_account_file' => '/home/u248494375/domains/tcpneus-logistica.com/condomini-firebase.json'],
      'mail' => ['from' => 'avisos@tcpneus-logistica.com', 'from_name' => 'Mini Condo',
                 'smtp' => ['host' => 'smtp.hostinger.com', 'port' => 465, 'secure' => 'ssl',
                            'user' => 'avisos@tcpneus-logistica.com', 'pass' => '<senha da caixa avisos@>']],
  ];
  ```
  **E-mail ("Esqueci a senha"):** crie uma caixa de e-mail no hPanel (E-mails → Contas de e-mail) no mesmo domínio e ponha o endereço em `mail.from` (hoje: `avisos@tcpneus-logistica.com`). Com `mail.smtp` e a senha da caixa, a API entrega pelo SMTP da Hostinger autenticada na caixa: o Gmail deixa de mostrar "por srv880.main-hosting.eu" e o e-mail tem menos chance de cair no spam. Sem `smtp`, usa o `mail()` do PHP. Se o envio falhar (senha errada, por exemplo), o app mostra "Não foi possível enviar o e-mail" e o motivo vai para o log de erros do PHP, sem a senha. No hPanel, em E-mails → Configurações de DNS, confira se SPF e DKIM estão ativos (o SPF já está). Sem a chave `mail`, o app diz para falar com a administração. Localmente, `'mail' => ['log_file' => ...]` grava os e-mails num arquivo.

  **Tamanho dos arquivos:** a API aceita até o **menor** entre `max_upload_bytes` e os limites do PHP do servidor (`upload_max_filesize` e `post_max_size`, em hPanel → Avançado → Configuração do PHP), e o aviso "Arquivo grande demais. O limite é de X MB." já mostra esse número. O login e o `/me` devolvem esse limite em `maxUploadBytes`, e o painel usa o mesmo número para avisar ("Só PDF, até X MB") e recusar o arquivo antes de enviar. Para PDFs grandes, suba os dois limites do PHP para 32M e ponha `'max_upload_bytes' => 30 * 1024 * 1024`. Quando o envio passa do `post_max_size`, o PHP descarta o corpo inteiro; a API reconhece isso (`reject_oversized_body`, em `lib/files.php`) em vez de responder "Escolha um condomínio". As fotos já chegam reduzidas pelo app.

  `srv880.hstgr.io` no host derruba a API inteira (500 em todas as rotas): ele só serve para conexão de fora. Com `__DIR__ . '/storage'` no lugar de `condomini-storage`, as fotos e PDFs já enviados somem do app.
- Mudanças no esquema: rode o SQL da migração no phpMyAdmin e registre em `schema_migrations` (ver o README principal), ou libere seu IP em hPanel → Bancos de dados → MySQL remoto, rode `scripts/install.php` do seu PC com um `config.php` apontando para `srv880.hstgr.io` e remova a liberação depois. Esse `config.php` do PC nunca vai para o servidor.
