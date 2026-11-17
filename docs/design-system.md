# Mini Condo — Design System

Referência única para Android (Compose) e iOS (SwiftUI). Ao mudar um token aqui,
mude nos dois lados. Fonte no Android: `app/src/main/java/com/example/condo_mini/ui/theme/`.

**Direção:** "quiet luxury" — fundo marfim, tinta azul-noite, acento champanhe,
títulos em serifa, bordas finas em vez de blocos coloridos, muito respiro.
Sem cor dinâmica (Material You) — a marca é a mesma em todas as plataformas.

## Marca

Nome registrado: **Mini Condo**. A logo original está em `docs/brand/mini-condo-logo.png`: arco dourado sobre três prédios em traço (a casa baixa e a de duas águas em dourado, os dois prédios em azul-noite), o nome **MINI** (tinta) **CONDO** (dourado) em serifa caixa-alta, um filete em degradê e o lema **GESTÃO DE CONDOMÍNIOS**.

O símbolo foi redesenhado em vetor a partir da logo (coordenadas da imagem original, traço de 35). Ele vem em duas versões, e cada lugar usa a do seu tema:

| Versão | Fundo | Prédios | Dourado |
|---|---|---|---|
| Clara | marfim `#F7F5F0` | azul-noite `#0F1720` | `#C9962F` → `#8E5E16` |
| Escura | azul-noite `#151B26` | marfim `#ECE7DD` | `#DDB15C` → `#A9782A` |

- **Ícone do Android:** `drawable/ic_launcher_*` (claro) e `drawable-night/ic_launcher_*` (escuro): o celular troca sozinho com o tema do sistema. `ic_launcher_monochrome` para ícones temáticos e `ic_notification` (branco, traços mais grossos) para a barra de notificações. PNGs `mipmap-*` só para Android 7.
- **Dentro do app:** `ic_brand_mark_light` / `ic_brand_mark_dark`, escolhidos pelo tema do app (a placa do login segue o tema).
- **Painel:** `brandMark()` e `brandName()` em `js/ui.js`; cores pelos tokens `--mark-*` do CSS (claro e escuro). Favicon `img/icone.svg` troca com o tema do navegador.
- **Arquivos:** `docs/brand/mini-condo-simbolo-claro.svg`, `mini-condo-simbolo-escuro.svg`, `mini-condo-icone-claro-512.png`, `mini-condo-icone-escuro-512.png` e `icon-play-store-512.png` (Play Store, versão clara).

Identificadores técnicos continuam com o nome antigo e **não devem mudar**: `applicationId com.appcondomini` (mudar quebra a atualização do app e o Firebase), pacote `com.example.condo_mini`, endereço `api-condomini`, banco `u248494375_condomini`, canal de notificação `condomini`, chaves `condomini.painel.*` do navegador.

## Cores

### Modo claro
| Token | Hex | Uso |
|---|---|---|
| `background` | `#F7F5F0` | Fundo de telas (marfim) |
| `surface` / card | `#FFFFFF` | Cartões |
| `surfaceContainer` | `#F0ECE4` | Caixas internas, fundo de ícones |
| `hairline` | `#E4DED3` | Bordas de cartões e divisores (1pt) |
| `primary` (ink) | `#151B26` | Texto principal, botões, seleção |
| `onPrimary` | `#F7F5F0` | Texto sobre `primary` |
| `secondaryText` | `#5E6675` | Texto de apoio |
| `tertiaryText` | `#8C93A0` | Datas, itens inativos |
| `accent` (champanhe) | `#B08D57` | Eyebrows, anel do avatar, detalhes |
| `accentContainer` | `#F3EADB` | Fundo de blocos de destaque claros |
| `heroStart → heroEnd` | `#232B3A → #151B26` | Gradiente dos cartões escuros (135°) |
| `onHero` / `onHeroMuted` | `#F7F5F0` / `#B7BCC6` | Texto nos cartões escuros |
| `heroAccent` | `#D9BE8E` | Eyebrow e filete nos cartões escuros (borda a 35% de opacidade) |

### Modo escuro
| Token | Hex |
|---|---|
| `background` | `#0E1117` |
| `surface` / card | `#151922` |
| `surfaceContainer` | `#1B202A` |
| `hairline` | `#2A303C` |
| `primary` | `#ECE7DD` |
| `onPrimary` | `#0E1117` |
| `secondaryText` | `#A8AEB9` |
| `accent` | `#D9BE8E` |
| `heroStart → heroEnd` | `#262D3C → #171B24` |

### Status (selo com ponto + texto)
| Tom | Claro (texto / fundo) | Escuro (texto / fundo) | Uso |
|---|---|---|---|
| success | `#2E6A4F` / `#E3EFE8` | `#8CC7A8` / `#1C3328` | Pago, confirmada, resolvida |
| warning | `#8A6A1F` / `#F5EDD8` | `#E0C077` / `#362D17` | Em aberto, pendente, em análise |
| error | `#9A2E2E` / `#F6E4E2` | `#E29A94` / `#3A1F20` | Vencido, importante |
| info | `#2F4A7A` / `#E5EAF4` | `#9DB4DE` / `#1D2740` | Síndico, aberta |
| accent | `#5C4524` / `#F3EADB` | `#D9BE8E` / `#3A3122` | Evento |

## Tipografia

Serifa para títulos (editorial), sans para leitura.

| Papel | Android | iOS | Tamanho / altura | Peso | Tracking |
|---|---|---|---|---|---|
| Display (valores, marca) | Noto Serif | New York | 34 / 40 | Regular | −0.5 |
| Título de tela | Noto Serif | New York | 30 / 36 | Regular | −0.4 |
| Título de destaque | Noto Serif | New York | 26 / 32 | Regular | −0.3 |
| Título de cartão | Noto Serif | New York | 20 / 26 | Regular | 0 |
| Subtítulo | Roboto | SF Pro | 16 / 22 | Semibold | 0 |
| Corpo | Roboto | SF Pro | 14 / 21 | Regular | 0.1 |
| Legenda | Roboto | SF Pro | 12 / 17 | Regular | 0.2 |
| Eyebrow (CAIXA ALTA) | Roboto | SF Pro | 11 / 14 | Semibold | 1.2 |

No SwiftUI: `.font(.system(size: 30, design: .serif))` para os títulos.
Para uma fonte de marca própria (ex.: uma serifa licenciada), troque
`DisplayFamily` em `Type.kt` e o equivalente no iOS.

## Formas e espaçamento

| Token | Raio | Uso |
|---|---|---|
| small | 10 | — |
| medium | 16 | Botões, campos, pílulas, caixas internas |
| large | 22 | Cartões |
| extraLarge | 28 | Cartões escuros (hero), capas de espaços |

- Margem lateral das telas: **20**; padding interno de cartões: **20** (hero: 24).
- Espaço entre cartões: **12–14**.
- Bordas: **1pt** na cor `hairline`. Sem sombras, exceto no botão flutuante (2pt).
- Botão principal: altura **56**, fundo `primary`, raio 16.

## Componentes (nomes iguais nas duas plataformas)

| Componente | Descrição |
|---|---|
| `CondoCard` | Cartão branco, borda hairline, raio 22, sem sombra |
| `HeroCard` | Cartão escuro com gradiente hero e filete champanhe na borda |
| `Eyebrow` | Rótulo em caixa alta, tracking largo, cor accent |
| `StatusChip` | Pílula com ponto de 6pt + texto, cores de status |
| `PillChip` | Seletor: selecionado = `primary` cheio; normal = contorno hairline |
| `PrimaryButton` | Botão principal de 56pt |
| `InitialsAvatar` | Iniciais sobre `primary`, anel champanhe de 1.5pt |
| `IconBadge` | Ícone em quadrado arredondado sobre `surfaceContainer` |
| Tab bar | Fundo de card, divisor superior, sem pílula; item ativo em `primary` com ponto champanhe de 4pt |
| Cabeçalho | Eyebrow (saudação ou unidade) + título em serifa; avatar à direita |

## Movimento
- Troca de abas: fade de 220 ms.
- Expandir cartões: animação de tamanho padrão da plataforma.
- Evitar animações chamativas (bounce, escala exagerada).

## Imagens
As capas das áreas comuns usam gradientes escuros como placeholder. Para o padrão
high ticket, substitua por **fotos reais do empreendimento**, com o mesmo
escurecimento (gradiente preto 0% → 70%) para manter o texto legível.
