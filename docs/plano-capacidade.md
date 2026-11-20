# Plano de capacidade

Como a API aguenta mais gente ao mesmo tempo, em etapas. Hoje não há usuários reais, então nada aqui é urgente: a ideia é saber o que fazer antes de cada salto e não reescrever nada às pressas.

Revisão feita em 20/11/2026 lendo a API (`backend/`) e o cliente do app (`ApiClient.kt`).

## Onde estamos

- **Hospedagem:** Hostinger compartilhada (LiteSpeed + PHP 8.3 + MariaDB). O limite que pesa primeiro é o de **processos simultâneos** (entry processes / workers do PHP), não CPU nem banco. Cada requisição ocupa um processo do começo ao fim.
- **O app pergunta à API a cada 30 segundos** em cada lista aberta (`POLL_MS` em `ApiClient.kt`), mesmo que nada tenha mudado.
- **Conta por alto:** 1 celular com o app aberto ≈ 2 a 3 listas ≈ 5 a 6 requisições por minuto.

| Pessoas com o app aberto ao mesmo tempo | Requisições por segundo | Situação na Hostinger compartilhada |
| --- | --- | --- |
| até 100 | ~10 | tranquilo |
| 300 | ~30 | começa a enfileirar nos horários de pico |
| 1.000 | ~100 | passa do limite: lentidão e erro 503/508 |

"Com o app aberto" é bem menos que "cadastrados": 20 prédios de 60 apartamentos (~1.500 pessoas) devem dar 50 a 150 abertos no pico da noite. A primeira etapa resolve com folga até algumas centenas de prédios.

## Etapa 1: parar de perguntar à toa (fazer antes dos primeiros prédios reais)

Maior ganho, sem trocar de hospedagem. Reduz o tráfego em 10 vezes ou mais.

1. **O push avisa que algo mudou; o app só pergunta quando precisa.**
   - O app já recebe notificações (FCM). Toda gravação que hoje gera notificação passa a mandar também uma mensagem silenciosa ("encomendas mudou").
   - O app recarrega só aquela lista ao receber.
   - A consulta automática fica como reserva: a cada 5 minutos com a tela aberta, e **nunca em segundo plano**.
2. **Resposta "nada mudou" (304).** A API calcula uma etiqueta (ETag) de cada lista. O app manda a última que recebeu e, se nada mudou, a resposta é vazia e leve.
3. **Listas com limite.**
   - Reservas, encomendas, ocorrências, avisos e boletos passam a vir paginados ou por período (por exemplo, os últimos 90 dias, com "ver mais").
   - Hoje `GET /reservations` devolve todo o histórico.
4. **Menos consultas por item (N+1).** Montar a lista com uma consulta só, usando `IN (...)` ou JOIN:
   - `reservation_json`: 2 consultas por reserva;
   - `occurrence_json`: 1 por ocorrência;
   - `user_json`: 1 por usuário;
   - `condo_json`: 1 por prédio;
   - `company_json`: 3 por administradora.
5. **Índices que faltam** (migração nova):
   - `parcels (photo_file_id)`;
   - `parcels (condo_id, status, arrived_at)`;
   - `bills (condo_id, reference)`;
   - `reservations (condo_id, status, date)`;
   - `occurrences (condo_id, created_at)`.

## Etapa 2: tirar trabalho pesado de dentro da requisição (quando passar de ~50 prédios)

1. **Push por tópico do condomínio.**
   - Hoje um aviso para o prédio inteiro manda um envio por celular, um depois do outro (`lib/push.php`), e prende um processo do PHP por minutos.
   - Com um tópico `condo_<id>` no Firebase, vira **um** envio.
   - Os avisos por apartamento continuam por aparelho.
2. **Fila para push e e-mail.**
   - Uma tabela `outbox` recebe o que precisa ser enviado, e uma tarefa agendada (cron da Hostinger, a cada minuto) despacha.
   - A requisição responde na hora e não segura processo.
   - O e-mail do código de senha hoje espera o SMTP dentro da requisição (até 15 s).
3. **Arquivos servidos pelo LiteSpeed, não pelo PHP.**
   - Depois de checar a permissão, a API responde com o cabeçalho `X-LiteSpeed-Location` apontando para o arquivo, e o servidor entrega sozinho.
   - Hoje o PHP lê o arquivo inteiro (`send_file`) e fica ocupado durante o download.
4. **Menos gravação por requisição.**
   - Toda chamada faz `UPDATE sessions SET last_used_at`, mais 2 ou 3 consultas para saber a administradora e se ela está ativa, mais o `SET time_zone`.
   - Atualizar a sessão no máximo 1 vez por hora e juntar as consultas numa só.

## Etapa 3: sair da hospedagem compartilhada (quando houver centenas de prédios pagando)

- **VPS** (a própria Hostinger tem): número de processos do PHP sob controle (PHP-FPM), banco com memória dedicada, cron e fila de verdade. O código atual roda igual: é PHP + MariaDB comum.
- **Arquivos** num armazenamento de objetos (S3, Cloudflare R2), com links temporários assinados.
- **Cache** (Redis) para sessões e listas mais lidas, se o banco virar gargalo.
- **Backups** automáticos do banco e da pasta de arquivos, com teste de restauração.

## Como saber que chegou a hora

- O hPanel mostra o uso de **processos simultâneos** e de CPU. Se encostar no limite nos horários de pico, é hora da etapa seguinte.
- Antes de abrir para um cliente grande, simule a carga com uma ferramenta como o `k6`, num ambiente de teste e não na produção. Dispare o padrão do app (login + listas a cada 30 s) com o número de celulares esperado.
