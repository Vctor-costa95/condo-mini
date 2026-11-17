# Plano: administradoras com a própria marca

```
Plataforma (PLATFORM, nós) → Administradora → Condomínios → síndico / porteiro / morador
```

A plataforma cadastra as administradoras, sobe a logo delas e aloca os prédios. O administrador (`ADMIN`) só vê os condomínios da administradora dele, e a logo da empresa aparece no app e no painel no lugar da nossa.

**Onde está:** a fase 1 está **publicada na Hostinger** desde 15/11/2026 (migração 004 rodada pelo phpMyAdmin). A fase 2 (painel) está no `main`, falta publicar. No app Android, o botão de criar condomínio do administrador ainda aparece e dá 403 até a fase 3.

**Decisões tomadas** (dá para mudar):
- só a plataforma cria condomínios;
- a cor da administradora entra só em detalhes;
- o login continua com a nossa marca;
- fica o "Desenvolvido por Mini Condo".

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

### Como testar localmente
- O `backend/config.php` local aponta para um MariaDB 13 na porta 3307. O MariaDB não roda como serviço: se a porta não responder, crie uma pasta de dados com `mariadb-install-db.exe --datadir=<pasta> --port=3307`, suba com `mariadbd.exe --defaults-file=<pasta>\my.ini --console`, crie o banco e o usuário `condomini` do `config.php` e rode `scripts/install.php --seed`.
- O PHP precisa das extensões passadas na linha de comando:
  ```bash
  EXT=/c/Users/jvict/AppData/Local/Microsoft/WinGet/Packages/PHP.PHP.8.3_Microsoft.Winget.Source_8wekyb3d8bbwe/ext
  ```
  ```bash
  php -d extension_dir=$EXT -d extension=pdo_mysql -d extension=fileinfo -d extension=mbstring -d extension=openssl -d extension=intl -S 127.0.0.1:8081 index.php
  ```
  ```bash
  php -d extension_dir=$EXT -d extension=mbstring -d extension=openssl -d extension=curl scripts/smoke-test.php http://127.0.0.1:8081 storage/push-test.log
  ```
- Para recriar o banco, entre como root no MariaDB (porta 3307, sem senha), rode `DROP/CREATE DATABASE` e depois `scripts/install.php --seed` com as extensões acima.

---

## ✅ Fase 2: painel web (feita em 15/11/2026, ainda não publicada)

- **Login:** entram `ADMIN` e `PLATFORM` (`js/main.js`). A plataforma abre em Administradoras, com o menu dela em cima e, embaixo, o condomínio aberto com Unidades, Cadastros e Espaços, para dar suporte em qualquer condomínio.
- **Marca do admin:** a barra lateral mostra a logo e o nome da administradora (`brandTile`, em `js/ui.js`); sem logo, as iniciais sobre a cor dela. Rodapé com "Desenvolvido por Mini Condo". A tela de carregamento usa a última marca guardada no navegador.
- **"Criar condomínio" só para a plataforma:** some do seletor e da tela vazia do ADMIN, e `#/condominio/novo` volta para Unidades. Ao criar, a plataforma escolhe a administradora (já vem marcada quando ela filtrou uma em Condomínios).
- **Administradoras** (`js/views/administradoras.js`): cartões com logo, CNPJ, situação e contagens; painel lateral para nome, CNPJ, cor, ativa/suspensa (pede confirmação), logo com prévia no tema claro e no escuro, administradores (listar, criar, excluir) e excluir a administradora quando ela não tem condomínios.
- **Condomínios** (`js/views/condominios.js`): todos, com busca, filtro por administradora e troca de administradora na própria linha (com confirmação). Clicar na linha abre o condomínio nas telas de sempre.
- **API:** `GET /users?companyId=` (só a plataforma) lista os administradores de uma administradora. Em Cadastros, administrador criado pela plataforma vai para a administradora do condomínio aberto.
- A logo vem do mesmo domínio, então o `img-src 'self'` do `painel/.htaccess` já permite.
- Testado no navegador com `plataforma` e `admin` (desktop e celular). Teste de fumaça: 178 verificações.

**Para publicar:** enviar a pasta `painel/` inteira e o `lib/directory.php` (a rota nova). Não tem migração.

## ⏭️ Fase 3: app Android

0. **Esconder "criar condomínio" do administrador** na aba Unidades do app (hoje dá 403).
1. **Não quebrar com `PLATFORM`:** hoje `UserRole.valueOf` em `data/remote/JsonMappers.kt` derruba o app. Adicionar `PLATFORM` ao enum (`domain/model/User.kt`) ou mostrar "use o painel".
2. **Modelo:** criar `Brand(companyId, name, logoUrl, color)` em `User` ou na sessão, lido de `brand` no login e no `/me`. Guardar no cache da sessão para abrir sem internet.
3. **Logo:** carregar e guardar no aparelho (ver se o Coil já está no projeto; senão, baixar com o OkHttp do `ApiClient` para o `ApiCache`). A URL tem `?v=`, então dá para guardar sem prazo.
4. **Onde mostrar:**
   - cabeçalho da Home (hoje está fixo "Mini Condo" em `ui/screens/home/HomeScreen.kt:64`);
   - Perfil;
   - topo dos PDFs (`data/pdf/BillPdfGenerator.kt` e `DocumentPdfGenerator.kt`);
   - tela de abertura com a última logo guardada.
5. **Login:** continua com a nossa marca e o "Desenvolvido por Mini Condo".
6. **Repositórios Fake:** atualizar para as duas administradoras da demonstração.

## ⏭️ Antes de publicar na Hostinger

- ✅ **Merge com o cadastro pelo app e o painel** (feito em 15/11): o aviso de pedido de cadastro vai só para os administradores da administradora do prédio, e aceitar ou recusar um pedido só vale nos prédios dela. O teste de fumaça cobre os dois.
- ⚠️ **O painel atual tem o botão "criar condomínio"**, que dá 403 para o administrador depois desta fase. Fazer o item 3 da fase 2 antes de publicar, ou junto.
- ✅ **Na produção** (passos 1 e 2 feitos em 15/11; 3 e 4 esperam a fase 2):
  1. enviar `lib/`, `sql/`, `index.php` e `scripts/`;
  2. rodar `scripts/install.php` (aplica a 004, e a 003 se o cadastro pelo app ainda não tiver sido publicado);
  3. criar o nosso cadastro: `php scripts/platform-user.php <usuário> "<Nome>" --sql` no seu computador e colar o `INSERT` no phpMyAdmin;
  4. criar as administradoras de verdade, subir as logos e mover os condomínios para elas.
- Atualizar o site na Lovable ([[landing-site-lovable]]) para falar da marca própria da administradora (opcional).
