# R1/L2 — timeout com commit tardio

Data: 18/10/2026. Continuidade autorizada pelo usuário: “seguimos então”, após a descrição do L2.

## Hipótese e desenho

Uma proposta nova de R$ 1.000 e um replay HTTP. O mock recebe atraso de 4.000 ms, erro zero e TTL de 2.000 ms. A primeira chamada captura o atraso antes da expiração e continua dormindo; novas chamadas depois do TTL usam baseline neutra. O timeout do worker permanece 3 s; retry base 5 s e redrive 3 recebimentos, conforme configuração existente, sem alterações.

O mesmo helper `test-legacy-transient.ps1` recebe `-Scenario L2`, mantendo L1 como padrão. Compartilha preflight, cópia isolada do JMX, token, expiração, restore e coleta; muda somente coorte, falha e critérios. Não duplicar o harness nem alterar o JMX principal. Registrar observações intermediárias de estado/counters durante a recuperação quando possível.

## Critérios e evidências

- Duas respostas 202, uma RECEIVED e uma DUPLICATE_ACCEPTED com mesma identidade; uma confirmação final na sessão original.
- Mock: um efeito único adicional, um replay adicional no ledger, zero falhas 503 simuladas. O replay do ledger recupera o efeito existente; não representa segunda averbação.
- Jaeger: erro/timeout do primeiro cliente após aproximadamente 3 s; servidor ainda executa por cerca de 4 s; tentativa posterior retorna 200. Verificar que o fim do primeiro servidor antecede o retry e que ambos pertencem à mesma jornada.
- Captura conciliada: uma proposta recebida/confirmada, R$ 1.000, nenhuma aberta; metas e maturação da política original preservadas.
- Filas e DLQ vazias; controle neutro antes do restore explícito. Sem reinício, purga ou mudança de identidade.

O resultado automatizado do helper é evidência parcial: contadores globais em ambiente isolado mais estado por identidade. A conclusão sobre timeout/commit tardio depende também da cronologia dos spans e do ledger; não atribuir timeout somente a uma duração configurada. Se a primeira chamada começar após o TTL, a hipótese não foi exercitada: registrar FAIL/INCOMPLETE, sem ampliar silenciosamente a falha.

## Segurança e limites

Preflight recusa serviço doente, controle não neutro, snapshot stale ou fila com trabalho. Uma ativação por execução, teto de 2 s, atraso máximo desta rodada 4 s; prazo de recuperação de até 60 s. Expiração independe do cliente e restore ocorre em finally. Erros de captura não são convertidos em sucesso. Não habilitar circuito, instrumentação nova de payload ou serviços adicionais.

Apenas uma coorte nesta rodada; o aquecimento do L1 permanece fora. Snapshots e traces são locais/na EC2 já autorizada, sem consulta à SQS cloud ou fixture de alerta. L3 e a lacuna do detector continuam fora deste ensaio.

## Ajuste do gerador após a primeira tentativa

A tentativa `legacy-l2-20261018-01` confirmou uma proposta, mas não exercitou commit tardio: a primeira chamada do worker começou depois da janela de falha. Houve erro de cliente e recuperação, porém apenas um atendimento no mock e zero replay no ledger. Não classificar como sucesso do L2.

Para uma repetição isolada, o harness passou a iniciar a mesma distribuição JMeter/Java 21 em processo próprio, com heap 128–512 MB e teto de 180 segundos. O timeout encerra somente o processo filho criado pelo ensaio; a expiração do mock continua independente e o restore permanece em finally. Não alterar o atraso de 4 s, timeout de 3 s, janela de 2 s, retry ou redrive para esconder o resultado anterior. Uma nova tentativa usa diretório e identidade novos, preservando o ledger e todos os artefatos da anterior.
