# Plano: administradoras com a própria marca

```
Plataforma (PLATFORM, nós) → Administradora → Condomínios → síndico / porteiro / morador
```

A plataforma cadastra as administradoras, sobe a logo delas e aloca os prédios. O administrador (`ADMIN`) só vê os condomínios da administradora dele, e a logo da empresa aparece no app e no painel no lugar da nossa.

**Onde está:** branch `claude/administradoras`, criada a partir de `claude/painel-admin`, na pasta `C:\Users\jvict\AndroidStudioProjects\Condomini-administradoras` (worktree, separada da pasta principal). Ainda sem push e sem PR.

**Decisões tomadas** (dá para mudar):
- só a plataforma cria condomínios;
- a cor da administradora entra só em detalhes;
- o login continua com a nossa marca;
- fica o "Desenvolvido por condo.mini".

---

## ✅ Fase 1: servidor (feita, commit `e6a28e7`)

- Tabela `companies`, mais `company_id` em `condominiums` e `users`, e o perfil `PLATFORM`. Migração `backend/sql/migrations/004-administradoras.sql`: tudo o que já existe vai para a "Administradora principal".
- Acesso por administradora em `can_access_condo()` (`backend/lib/core.php`) e `can_edit_user()` (`backend/lib/directory.php`).
- Rotas da plataforma em `backend/lib/companies.php`:
  - `GET/POST/DELETE /companies`
  - `POST/DELETE /companies/{id}/logo`
  - `GET /companies/{id}/logo` (pública)
  - `PUT /condos/{id}/company`
- O login e o `/me` trazem `brand: {companyId, name, logoUrl, color}`.
- Administradora com `active = false` fica suspensa: ninguém dela entra.
- `backend/scripts/platform-user.php` cria o cadastro da plataforma na produção.
- Demonstração:
  - `admin`: Prime, condomínios c1 e c2;
  - `marcia.teles` / `condo123`: Horizonte, condomínio c3;
  - `plataforma`: entra sem senha no banco local.
- O teste de fumaça passou inteiro.

### Como testar localmente (nesta worktree)
- O `backend/config.php` da worktree aponta para o banco `condomini_adm` (porta 3307). Os arquivos enviados ficam no scratchpad da sessão; se a pasta sumir, troque `storage_dir` e `push.log_file`.
- O PHP precisa das extensões passadas na linha de comando:
  ```bash
  EXT=/c/Users/jvict/AppData/Local/Microsoft/WinGet/Packages/PHP.PHP.8.3_Microsoft.Winget.Source_8wekyb3d8bbwe/ext
  ```
  ```bash
  php -d extension_dir=$EXT -d extension=pdo_mysql -d extension=fileinfo -d extension=mbstring -d extension=openssl -d extension=intl -S 127.0.0.1:8081 index.php
  ```
  ```bash
  php scripts/smoke-test.php http://127.0.0.1:8081
  ```
- Para recriar o banco, entre como root no MariaDB 13 (porta 3307, sem senha) e rode `DROP/CREATE DATABASE condomini_adm`. Depois rode `scripts/install.php --seed` com as extensões acima.

---

## ⏭️ Fase 2: painel web (`backend/painel/`)

1. **Login:** hoje só entra `ADMIN` (`js/main.js`, linhas 38 e 61). Deixar entrar também `PLATFORM`, com outro menu.
2. **Marca do admin:** ler `me.brand`. Na barra lateral e na tela de carregamento, mostrar a logo e o nome da administradora no lugar do condo.mini (`brandMark`, em `js/ui.js`). Sem logo, a placa mostra as iniciais do nome. Pequeno, no rodapé: "Desenvolvido por condo.mini".
3. **Esconder "criar condomínio" para o ADMIN** (`js/views/condominio.js`). Hoje o botão dá 403.
4. **Console da plataforma** (telas novas):
   - **Administradoras:** lista com quantos condomínios e admins cada uma tem; criar e editar nome, CNPJ, cor e ativa/suspensa; enviar e remover a logo, com prévia no tema claro e no escuro; excluir só quando estiver sem condomínios.
   - **Condomínios:** todos, com filtro por administradora (`GET /condos?companyId=`, vazio = sem administradora); criar já alocado (`POST /condos` com `companyId`); mover ou desalocar (`PUT /condos/{id}/company`).
   - **Admins de cada administradora:** criar com `POST /users` (`role: ADMIN`, `companyId`).
   - A plataforma também pode entrar nas telas atuais (unidades, cadastros, espaços) de qualquer condomínio, para dar suporte.
5. **CSP:** a logo vem do mesmo domínio (`/companies/{id}/logo`). Conferir se o `img-src` do `painel/.htaccess` permite.
6. Visual premium, igual ao painel atual ([[premium-feel-every-role]]). Testar no navegador com `admin`, `marcia.teles` e `plataforma`.

## ⏭️ Fase 3: app Android

1. **Não quebrar com `PLATFORM`:** hoje `UserRole.valueOf` em `data/remote/JsonMappers.kt` derruba o app. Adicionar `PLATFORM` ao enum (`domain/model/User.kt`) ou mostrar "use o painel".
2. **Modelo:** criar `Brand(companyId, name, logoUrl, color)` em `User` ou na sessão, lido de `brand` no login e no `/me`. Guardar no cache da sessão para abrir sem internet.
3. **Logo:** carregar e guardar no aparelho (ver se o Coil já está no projeto; senão, baixar com o OkHttp do `ApiClient` para o `ApiCache`). A URL tem `?v=`, então dá para guardar sem prazo.
4. **Onde mostrar:**
   - cabeçalho da Home (hoje está fixo "Condo-mini" em `ui/screens/home/HomeScreen.kt:64`);
   - Perfil;
   - topo dos PDFs (`data/pdf/BillPdfGenerator.kt` e `DocumentPdfGenerator.kt`);
   - tela de abertura com a última logo guardada.
5. **Login:** continua com a nossa marca e o "Desenvolvido por condo.mini".
6. **Repositórios Fake:** atualizar para as duas administradoras da demonstração.

## ⏭️ Antes de publicar na Hostinger

- **Merge com `claude/cadastro-pelo-app`:** a outra branch tem a migração `003-pedidos-de-cadastro.sql` e mexe em `schema.sql`, `directory.php` e no teste de fumaça. Resolver os conflitos com cuidado; a nossa migração é a 004.
- **Merge com `claude/painel-admin`:** essa branch ainda não está no `main`. Fazer o merge dela antes, ou junto.
- **Na produção:**
  1. enviar `lib/`, `sql/`, `index.php` e `scripts/`;
  2. rodar `scripts/install.php` (aplica a 004);
  3. criar o nosso cadastro com `scripts/platform-user.php`;
  4. criar as administradoras de verdade, subir as logos e mover os condomínios para elas.
- Atualizar o site na Lovable ([[landing-site-lovable]]) para falar da marca própria da administradora (opcional).
