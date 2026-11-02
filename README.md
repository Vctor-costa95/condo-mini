# condo.mini

Aplicativo Android de gestão de condomínio. Morador, síndico, portaria e administradora usam o mesmo app; cada um vê as próprias abas, conforme o cadastro feito pela administradora.

Este README explica **como o app funciona e o que você usa para mantê-lo de pé**. A parte técnica fica no fim, e a da API em [`backend/README.md`](backend/README.md).

---

## 1. As peças

O app nunca fala direto com o banco de dados. Tudo passa pela API, que fica na Hostinger:

```
 Celular (app condo.mini)
        │  HTTPS, com o token de login
        ▼
 API em PHP  ──────────►  Banco MySQL          (os dois na Hostinger)
        │
        └─────────────►  Firebase  ──►  notificação no celular
```

| Peça | O que é | Onde fica |
|---|---|---|
| **App Android** | O que as pessoas instalam. Código Kotlin neste repositório. | Pasta `app/` |
| **API** | O "cérebro": confere login e permissões, grava e lê o banco, guarda fotos e PDFs e manda as notificações. | Hostinger, em `https://api-condomini.tcpneus-logistica.com/api-condomini` · código na pasta `backend/` |
| **Banco de dados** | Onde ficam condomínios, pessoas, avisos, reservas, boletos, encomendas... | Hostinger, MySQL `u248494375_condomini` |
| **Firebase** | Serviço gratuito do Google que entrega as notificações push. | Projeto `app-condo-mini` em console.firebase.google.com |
| **GitHub** | Guarda o código e o histórico de mudanças. | github.com/Vctor-costa95/condo-mini |
| **Site** | Página de apresentação para administradoras. | appcondomini.lovable.app (Lovable) |

---

## 2. Quem usa o quê

O login é **usuário e senha**, criados pela administradora no cadastro da pessoa. O perfil e os apartamentos vêm desse cadastro. Depois de entrar, o celular **continua logado** até a pessoa tocar em "Sair da conta".

### Morador
| Aba | O que faz |
|---|---|
| **Avisos** | Comunicados do síndico, com os fixados em destaque. |
| **Reservas** | Reserva salão, churrasqueira e outros espaços por período (manhã, tarde, noite). Espaços com taxa esperam a aprovação do síndico. Também pede **mudança** ou **reparo** (internet, elétrica, hidráulica). |
| **Boletos** | Cobranças do mês (em aberto, vencido, pago), código de barras e o PDF do boleto. |
| **Encomendas** | O que chegou na portaria, com foto e o **código de retirada** que ele mostra ao porteiro. |
| **Ocorrências** | Registra problemas com até 3 fotos, acompanha pelo protocolo (OC-1045) e conversa com o síndico. |
| **Perfil** | Dados de contato e **documentos do condomínio** (regimento, convenção, atas). |

Quem tem mais de um apartamento escolhe qual ver ao entrar e troca depois, tocando no nome do apartamento no topo.

### Síndico
| Aba | O que faz |
|---|---|
| **Avisos** | Publica avisos (com categoria e opção de fixar) e exclui. |
| **Reservas** | Aprova ou recusa reservas, mudanças e reparos. Para recusar, informa o motivo. |
| **Ocorrências** | Responde, pede mais detalhes ou marca como resolvida, com fotos. |
| **Documentos** | Publica PDFs para todos os moradores e substitui o regimento ou a convenção vigente. |

### Portaria
| Aba | O que faz |
|---|---|
| **Encomendas** | Registra a chegada com foto. Na entrega, digita o código que o morador mostra; o porteiro **não vê o código**. |
| **Agenda** | Mudanças e reparos aprovados para hoje e os próximos dias. |
| **Moradores** | Quem mora em cada apartamento, com telefone. |
| **Avisos** | O mesmo mural dos moradores. |

### Administradora
Atende vários condomínios e escolhe qual está gerenciando no topo da tela.

| Aba | O que faz |
|---|---|
| **Unidades** | Cria o condomínio e os blocos (andares, apartamentos por andar, coberturas). Envia o **PDF do boleto** de cada apartamento por mês. |
| **Cadastros** | Cria pessoas com usuário, senha, perfil e apartamentos. |
| **Espaços** | Cria espaços reserváveis (capacidade, taxa, regras) e interdita para manutenção. |

---

## 3. O caminho de uma ação

Exemplo: chegou uma encomenda.

1. O porteiro tira a foto e toca em **Registrar** no app.
2. O app envia a foto para a API, que a guarda no servidor (fora da parte pública do site).
3. A API grava a encomenda no banco e gera um código de 4 dígitos.
4. Logo depois de responder ao porteiro, a API pede ao Firebase para notificar os celulares de quem mora no apartamento: *"Encomenda na portaria"*. O código não vai na notificação, porque ela aparece na tela bloqueada.
5. O morador toca na notificação, o app abre na aba **Encomendas** e mostra o código.
6. Na portaria, o porteiro digita o código. A API confere e marca como entregue.

Toda tela funciona assim: o app pede, a API confere quem está pedindo (um morador só vê os próprios apartamentos; um porteiro não aprova reservas) e só então lê ou grava no banco. As listas se atualizam sozinhas a cada 30 segundos e logo depois de qualquer ação no app.

### Quem recebe notificação

| Quando | Quem recebe |
|---|---|
| Encomenda registrada | Moradores do apartamento |
| Aviso ou documento publicado | Todo o condomínio |
| PDF do boleto enviado | Moradores do apartamento |
| Pedido de reserva com taxa, mudança ou reparo | Síndico |
| Reserva, mudança ou reparo aprovado ou recusado | Moradores do apartamento |
| Ocorrência nova ou resposta do morador | Síndico |
| Resposta do síndico na ocorrência | Moradores do apartamento |

Quem fez a ação não recebe a própria notificação.

### Sem internet

O app guarda no celular a última versão de cada tela que foi aberta com internet. Sem conexão, ele mostra esses dados, com uma faixa no topo: *"Sem conexão · mostrando dados de 14:32"*. Assim o morador ainda vê o **código da encomenda**, a **linha digitável e o PDF do boleto**, os avisos e as reservas. Quando a internet volta, tudo recarrega sozinho e a faixa some.

Telas que nunca foram abertas com internet ficam vazias, com a faixa avisando. Ações (reservar, registrar, aprovar) continuam precisando de conexão. Ao sair da conta, tudo que estava guardado é apagado do celular.

---

## 4. Contas de teste

O banco ainda tem **dados de demonstração**: três condomínios fictícios (Residencial Jardins, Edifício Vista Mar, Parque das Águas) com moradores, avisos, reservas e boletos.

| Usuário | Senha | Quem é |
|---|---|---|
| `morador` | (vazia) | Ana Ribeiro, com dois apartamentos no Bloco A |
| `sindico` | (vazia) | Carlos Andrade, síndico do Residencial Jardins |
| `porteiro` | (vazia) | José Silva, portaria do Residencial Jardins |
| `admin` | (vazia) | Renata Prime, administradora |
| `nome.sobrenome` | `condo123` | Os outros moradores fictícios (ex.: `paulo.mendes`) |

> ⚠️ **Antes de cadastrar moradores de verdade:** desligue as contas sem senha no servidor (`'allow_passwordless_logins' => false` no `config.php` da API) e limpe os dados de demonstração. Enquanto estiverem ligadas, qualquer pessoa com o app entra como síndico ou administradora.

---

## 5. Tarefas do dia a dia

### Rodar o app no seu PC
1. Abra a pasta do projeto no **Android Studio** e espere o Gradle terminar.
2. Ligue o emulador (ou conecte o celular com depuração USB) e aperte **▶ Run**.

O app já vem apontado para a API da Hostinger. Para rodar só com dados fictícios em memória, sem internet, compile com `-Pcondomini.apiUrl=` (vazio).

### Mudar algo no app
Altere, rode no emulador, teste e só então faça commit e push. O app no celular das pessoas só muda quando você gera uma versão nova e instala (ou publica na Play Store).

### Atualizar a API no servidor
A Hostinger não recebe o código pelo GitHub automaticamente. Para publicar uma mudança em `backend/`:
1. Gere o zip com os arquivos alterados (`index.php`, `.htaccess`, `lib/`, `sql/`).
2. No **hPanel** → Sites → tcpneus-logistica.com → **Gerenciador de arquivos**, entre em `public_html/api-condomini/api-condomini`.
3. Envie e extraia **na própria pasta**. **Não substitua o `config.php`** do servidor, que tem a senha do banco.

### Mudar a estrutura do banco
Mudanças em `backend/sql/schema.sql` precisam rodar no MySQL da Hostinger. Como o banco só aceita conexões do próprio servidor, libere seu IP por um momento em hPanel → Bancos de dados → **MySQL remoto**, rode `php scripts/install.php` com um `config.php` apontando para `srv880.hstgr.io` e **remova a liberação** depois.

### Testar a API
`backend/scripts/smoke-test.php` faz login com cada perfil e passa por todas as telas, inclusive permissões e notificações. Rode só contra uma cópia local (ele cria registros). Veja [`backend/README.md`](backend/README.md#rodar-localmente).

---

## 6. Senhas e arquivos secretos

Estes arquivos **nunca vão para o GitHub** (já estão no `.gitignore`):

| Arquivo | O que tem | Onde está |
|---|---|---|
| `backend/config.php` | Senha do banco e configurações do servidor | No servidor, ao lado do `index.php`, e uma cópia local para testes |
| Chave do Firebase (`condomini-firebase.json`) | Permite mandar notificações em nome do app | No servidor, em `domains/tcpneus-logistica.com/`, fora do `public_html`. O original está na sua pasta Downloads. |
| `backend/dist/` | Zips preparados para subir na Hostinger | Só no seu PC |

O `app/google-services.json` **não** é secreto: ele identifica o app no Firebase e vai junto no código.

O token de login fica no celular **cifrado** com uma chave do próprio aparelho (Android Keystore) e não entra em backups.

---

## 7. O que ainda não existe

- **Criar cobranças pelo app.** A lista de boletos (valor, vencimento, pago ou não) vem do banco; hoje só existe a dos dados de demonstração. A administradora consegue enviar o **PDF** de cada mês, mas falta uma tela para lançar ou importar os valores.
- **Pagamento pelo app** (Pix, cartão).
- **Recuperar senha** sozinho: hoje a administradora define uma senha nova no cadastro.
- **Fazer ações sem internet** (reservar, registrar ocorrência, entregar encomenda): sem conexão, o app avisa e a pessoa tenta de novo. Ver e consultar funciona offline (seção 3).
- **Versão iOS**, prevista para o início de 2027.
- **Publicação na Play Store.**

---

## 8. Parte técnica

**App:** Kotlin, Jetpack Compose (Material 3), MVVM com `StateFlow`, Navigation Compose, OkHttp, Coil e Firebase Cloud Messaging. Pacote `com.appcondomini`, Android 7.0+ (minSdk 24, targetSdk 37).

**API:** PHP 8 sem dependências e MySQL. Rotas, permissões e notificações estão em [`backend/README.md`](backend/README.md).

```
app/src/main/java/com/example/condo_mini/
├── data/
│   ├── AppContainer.kt   # Escolhe API (data/remote) ou dados fictícios (data/fake)
│   ├── remote/           # Cliente da API, conversão JSON, sessão salva cifrada
│   ├── fake/             # Dados de demonstração em memória
│   └── pdf/              # PDFs gerados no aparelho (boleto e documentos de demonstração)
├── domain/
│   ├── model/            # User, Notice, Reservation, Bill, Parcel, Occurrence...
│   └── repository/       # Interfaces que as telas usam
├── push/                 # Firebase: registro do aparelho e exibição das notificações
└── ui/
    ├── screens/          # Telas por perfil: morador, manager/, doorman/, admin/
    ├── components/       # Cards, fotos, visualizador de PDF
    ├── navigation/       # Login, escolha de apartamento e tela principal
    └── theme/            # Cores, fontes e formas
backend/                  # API em PHP (ver backend/README.md)
docs/design-system.md     # Cores, tipografia e componentes (referência para o iOS)
```

Compilar pela linha de comando (Windows):

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat :app:assembleDebug
```

## Licença

Projeto privado. Todos os direitos reservados.
