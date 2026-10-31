# Condo-mini

Aplicativo de gestão condominial para Android, voltado ao cliente final. Reúne avisos, reservas de áreas comuns, boletos, encomendas e o livro de ocorrências do condomínio em uma experiência com visual premium ("quiet luxury"), pensada para manter paridade com uma futura versão iOS.

> **Status:** protótipo funcional com dados simulados (mock). Ainda não há backend; veja [Próximos passos](#próximos-passos).

## Perfis de usuário

| Perfil | Situação | O que faz |
|---|---|---|
| **Morador** | ✅ Pronto | Avisos, reservas, boletos, encomendas, ocorrências e perfil |
| **Síndico** | ✅ Pronto | Publica avisos e documentos, aprova/recusa reservas, responde ocorrências |
| **Porteiro** | ⏳ Planejado | Registrar encomendas com foto |
| **Administrador** | ⏳ Planejado | Gerenciar condomínios e perfis de acesso |

O perfil é escolhido na tela de login ("Acessar como").

## Funcionalidades

### Morador
- **Avisos** (aba inicial): comunicados do síndico e informações importantes, com destaque para avisos fixados.
- **Reservas**: agendamento de salão, churrasqueira e outras áreas por período (manhã, tarde, noite). Reservas ficam pendentes até a aprovação do síndico.
- **Boletos**: cobranças mensais com status (em aberto, vencido, pago), cópia do código de barras e visualização/compartilhamento do PDF.
- **Encomendas**: itens aguardando retirada com código e foto tirada pela portaria, que abre em tela cheia com zoom.
- **Ocorrências**: registro no livro de ocorrências com até 3 fotos (câmera ou galeria), acompanhamento por protocolo e conversa com a administração.
- **Perfil**: dados da unidade, edição de contato e acesso aos **Documentos do condomínio** (regimento interno, convenção e atas de assembleia em PDF, com zoom e compartilhamento).

### Síndico
- **Avisos**: criação com categoria e opção de fixar; exclusão com confirmação.
- **Aprovações**: aprovar ou recusar reservas (a recusa exige um motivo).
- **Ocorrências**: responder, pedir mais detalhes ao morador ou resolver, com anexo de fotos.
- **Documentos**: publicar PDFs (regimento, convenção, atas e outros) para todos os moradores, substituir a versão vigente do regimento ou da convenção e excluir documentos.

## Stack

- **Kotlin** + **Jetpack Compose** (Material 3)
- **MVVM** com `StateFlow` e ViewModels por tela
- **Navigation Compose**
- **Coil** para imagens
- `PdfDocument` / `PdfRenderer` para gerar e exibir boletos
- `FileProvider` + Photo Picker para fotos, sem permissões de câmera ou armazenamento
- Core library desugaring para `java.time` em APIs antigas

| | Versão |
|---|---|
| minSdk | 24 (Android 7.0) |
| targetSdk / compileSdk | 37 |
| AGP | 9.4.1 |
| Kotlin | 2.2.10 |
| Java | 11 |

## Como rodar

**Requisitos:** Android Studio recente (com o JDK embutido) e um emulador ou aparelho com Android 7.0+.

1. Clone o repositório:
   ```bash
   git clone https://github.com/Vctor-costa95/condo-mini.git
   ```
2. Abra a pasta no Android Studio e aguarde o sync do Gradle.
3. Rode a configuração **app** (▶).

Pela linha de comando (Windows):

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat :app:assembleDebug
```

Na tela de login, escolha o perfil (Morador ou Síndico) e informe qualquer nome; os dados são simulados.

## Estrutura do projeto

```
app/src/main/java/com/example/condo_mini/
├── data/
│   ├── AppContainer.kt      # Injeção de dependência manual (troque os fakes aqui)
│   ├── fake/                # Repositórios simulados em memória
│   └── pdf/                 # PDFs de demonstração (boleto, documentos) e cópia dos PDFs enviados
├── domain/
│   ├── model/               # Modelos: User, Notice, Reservation, Bill, Parcel, Occurrence, CondoDocument
│   └── repository/          # Interfaces dos repositórios
└── ui/
    ├── components/          # Componentes compartilhados (cards, chips, fotos, zoom)
    ├── navigation/          # Rotas do app
    ├── screens/             # Telas do morador e do síndico (manager/)
    ├── theme/               # Cores, tipografia e formas
    └── util/                # Formatação de datas e valores
```

As telas dependem apenas das **interfaces** em `domain/repository`. Para ligar um backend real, basta criar novas implementações e registrá-las no `AppContainer`, sem mexer nas telas.

## Design system

Tokens de cor, tipografia, formas e componentes estão documentados em [`docs/design-system.md`](docs/design-system.md), servindo de referência para a versão iOS.

## Próximos passos

- [ ] Backend (proposta: Supabase com Postgres, Auth, Storage e Row Level Security, região São Paulo)
- [ ] Cache local com Room e sessão com DataStore
- [ ] Perfis de porteiro e administrador
- [ ] Notificações push (FCM)
- [ ] Integração de boletos com gateway de pagamento
- [ ] Ambientes dev/staging/prod e CI com GitHub Actions
- [ ] Versão iOS

## Licença

Projeto privado. Todos os direitos reservados.
