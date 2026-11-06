# API do Condo-mini

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
| `lib/community.php` | Avisos, espaços comuns, reservas, mudanças e reparos |
| `lib/records.php` | Boletos, encomendas, ocorrências e documentos |
| `lib/files.php` | Upload e download de fotos e PDFs (fora do alcance público) |
| `lib/setup.php`, `scripts/install.php` | Cria as tabelas e os dados de demonstração |
| `sql/schema.sql` | Esquema do banco |
| `config.php` | Credenciais (fora do git; modelo em `config.example.php`) |

## Autenticação

`POST /auth/login` com `{"login", "password"}` devolve `{"token", "user"}`. As demais rotas exigem `Authorization: Bearer <token>`. O banco guarda só o SHA-256 do token; `POST /auth/logout` o invalida. Quem criou a conta pelo app e ainda não foi aceito recebe 403 com a situação do pedido (aguardando ou recusado, com o motivo), só se acertar a senha.

Cada perfil só vê o próprio condomínio; o administrador escolhe com `?condoId=` (ou `condoId` no corpo). Morador só lê e altera os próprios apartamentos. O código de retirada de encomendas só aparece para o morador.

Contas de teste sem senha (`morador`, `sindico`, `porteiro`, `admin`) só entram com `allow_passwordless_logins => true` e só num banco recém-semeado: na produção elas já têm senha (ver o README principal). Os demais cadastros de demonstração usam `nome.sobrenome` / `condo123`. **Desligue a opção e troque as senhas antes de cadastrar moradores de verdade.**

## Rotas

Respostas em JSON; erros vêm como `{"error": "mensagem"}` com status 4xx. Datas em `AAAA-MM-DD`, data e hora em `AAAA-MM-DDTHH:MM:SS` (horário de Brasília), enums com o mesmo nome do app (`PENDING`, `MORNING`...). O prefixo `/v1` é opcional.

| Rota | Quem |
|---|---|
| `POST /auth/login`, `POST /auth/logout`, `GET /me`, `PATCH /me` | todos |
| `GET /condos`, `POST /condos` | todos lê o seu / admin salva |
| `GET /users`, `POST /users`, `DELETE /users/{id}` | admin, síndico e portaria leem / admin salva |
| `POST /users/{id}/units`, `DELETE /users/{id}/units?unit=` | admin |
| `GET /logins/available?login=&exceptUserId=` | admin (considera também pedidos de cadastro pendentes) |
| `GET /public/condos` | sem login: prédios e blocos para quem vai criar a conta (sem moradores) |
| `POST /signup` `{condoId, name, email, phone, login, password, unit}` | sem login: pedido de cadastro do morador; até 5 pendentes por endereço por hora |
| `GET /signup-requests?condoId=`, `POST /signup-requests/{id}/approve` `{unit?}`, `POST /signup-requests/{id}/reject` `{reason}` | admin: aceitar vira morador com a senha que a pessoa escolheu |
| `GET /notices`, `POST /notices`, `DELETE /notices/{id}` | todos leem / síndico e admin publicam |
| `GET /areas`, `POST /areas`, `DELETE /areas/{id}`, `PUT /areas/{id}/closed` | todos leem / síndico e admin editam |
| `GET /areas/{id}/booked?date=` | todos |
| `GET /reservations[?unit=]`, `POST /reservations` | morador com `unit`; síndico e portaria sem |
| `GET /reservations?history=1[&unit=]`, `GET /service-bookings?history=1[&unit=]` | histórico com todos os status e datas, mais recente primeiro: morador com `unit`; síndico e admin sem (condomínio todo) |
| `POST /reservations/{id}/cancel\|approve\|reject` | morador cancela / síndico aprova, recusa e cancela (com `reason` obrigatório) |
| `GET /service-bookings[?unit=]`, `GET /service-bookings/taken-moves?date=`, `POST /service-bookings` | idem |
| `POST /service-bookings/{id}/cancel\|approve\|reject` | idem |
| `GET /bills?unit=`, `GET /bills/{id}`, `GET /bills/{id}/pdf` | morador do apartamento |
| `GET /unit-documents`, `POST /unit-documents` (multipart), `DELETE /unit-documents/{id}` | admin |
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
| Pedido de cadastro feito pelo app | administradores |
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

`painel/` é o painel para o administrador usar no computador, com as mesmas funções do perfil administrador no app: condomínios (criar, editar blocos e andares, trocar), unidades (boletos em PDF do mês, alocar e desalocar moradores), cadastros e espaços. Só o perfil `ADMIN` entra; os outros são mandados para o app.

- São só arquivos estáticos (HTML, CSS e JavaScript em módulos, sem framework e sem build), na mesma pasta da API: sem CORS, e o painel chama as mesmas rotas do app.
- Endereço: `<endereço da API>/painel/`. Localmente, `http://127.0.0.1:8080/painel/` (o `index.php` deixa o `php -S` entregar os arquivos do painel).
- O `.htaccess` da API deixa `painel/` passar direto, e o `painel/.htaccess` liga os cabeçalhos de segurança (CSP só com arquivos próprios, sem embutir em outros sites).
- O token fica no navegador (`localStorage` com "Manter conectado", senão `sessionStorage`). PDFs são baixados com o token e abertos numa aba nova.
- A numeração dos apartamentos (`painel/js/units.js`) é a mesma do app (`CondoUnit.kt`) e da API (`tower_unit_labels`): mude as três juntas.

## Hostinger

- Endereço da API: `https://api-condomini.tcpneus-logistica.com/api-condomini` (pasta `public_html/api-condomini/api-condomini`; o app usa este endereço por padrão).
- Banco: `u248494375_condomini` (o `config.php` do servidor usa `127.0.0.1:3306`).
- Arquivos enviados: `/home/u248494375/condomini-storage`, fora do `public_html`.
- Chave do Firebase: `domains/tcpneus-logistica.com/condomini-firebase.json`, fora do `public_html` (configurada em `push.service_account_file`).
- Para atualizar: envie `index.php`, `.htaccess`, `lib/` e `sql/` pelo Gerenciador de Arquivos, sem sobrescrever o `config.php` do servidor.
- Painel: envie a pasta `painel/` inteira (inclusive o `painel/.htaccess`, que fica oculto em alguns gerenciadores) para dentro da pasta da API. Endereço: `https://api-condomini.tcpneus-logistica.com/api-condomini/painel/`.
- Mudanças no esquema: libere seu IP em hPanel → Bancos de dados → MySQL remoto, rode `scripts/install.php` com um `config.php` apontando para `srv880.hstgr.io` e remova a liberação depois.
