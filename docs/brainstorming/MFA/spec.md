# Feature Specification: Aprovação de Operações Sensíveis com Step-Up

**Feature Branch**: não definida; estudo sem implementação.

**Created**: 2026-10-22

**Status**: estudo arquivado; fora do escopo da PoC por decisão do usuário em 23/10/2026.

**Origem**: [Artigo sobre MFA e aprovação transacional](post-mfa-stepup-autorizacao.md).

## Clarifications

### Session 2026-10-22

- Q: Quais operações devem fazer parte da primeira versão do fluxo de aprovação? → A: Qualquer operação sensível, financeira ou não.
- Q: Se o titular não puder aprovar uma operação no momento em que ela for solicitada, como o fluxo deve continuar? → A: A aprovação deve ocorrer no mesmo fluxo; sem estado pendente.
- Q: O que deve acontecer quando alguém falha repetidamente ao autenticar ou confirmar uma operação sensível? → A: Limitar temporariamente novas tentativas conforme a política, sem bloquear a conta inteira.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Aprovar uma operação sensível (Priority: P1)

Como titular ou responsável por um recurso, quero confirmar os dados exatos de uma operação sensível e
comprovar minha identidade com o nível de segurança exigido, para que a operação só seja
executada com minha autorização e meu consentimento explícito.

**Why this priority**: Esta é a proteção central contra o uso indevido de uma sessão com MFA
válido para executar uma operação diferente daquela que o titular pretendia.

**Independent Test**: É possível apresentar uma operação sensível que exige confirmação,
verificar a identidade do titular, aprovar ou rejeitar os dados apresentados e observar se
a operação permanece sem execução ou é executada conforme as decisões tomadas.

**Acceptance Scenarios**:

1. **Dado que** uma operação exige autenticação reforçada e a sessão não atende aos
   requisitos de força ou atualidade, **Quando** o titular tenta prosseguir, **Então** o sistema
   exige autenticação adicional antes de permitir a avaliação da operação.
2. **Dado que** a sessão atende aos requisitos de autenticação, **Quando** a política de
   autorização nega a operação, **Então** a operação não é executada e a autenticação válida
   não altera essa decisão.
3. **Dado que** uma operação autorizável tem seus dados definidos, **Quando** o titular recebe
   a solicitação de confirmação, **Então** são apresentados claramente os dados materiais da
   operação, incluindo origem, destinatário, valor e moeda quando aplicáveis, antes de
   qualquer aprovação.
4. **Dado que** o titular aprovou os dados apresentados e a operação permanece inalterada,
   **Quando** a execução é solicitada, **Então** a operação pode ser executada uma única vez e a
   aprovação fica vinculada somente a essa operação.
5. **Dado que** o titular cancela a autenticação ou rejeita a confirmação, **Quando** o fluxo
   termina, **Então** a operação não é executada e o titular recebe um resultado que informa
   que ela foi cancelada ou rejeitada.
6. **Dado que** os dados materiais mudam depois da aprovação e antes da execução, **Quando**
   a execução é solicitada, **Então** a aprovação anterior é recusada e uma nova confirmação
   é exigida.
7. **Dado que** o limite definido pela política de falhas de autenticação ou confirmação foi
   atingido, **Quando** uma nova tentativa é feita, **Então** novas tentativas dessa operação
   são temporariamente limitadas sem bloquear automaticamente a conta inteira.

---

### User Story 2 - Confirmar uma operação iniciada por representante delegado (Priority: P2)

Como titular ou responsável por um recurso, quero saber quando uma operação sensível foi preparada por um
agente ou representante e revisar seus dados antes de aprová-la, para manter controle e
rastreabilidade sobre ações delegadas.

**Why this priority**: A delegação não pode confundir a identidade do titular com a do
representante nem substituir o consentimento humano exigido para operações sensíveis.

**Independent Test**: É possível submeter uma operação atribuída a um representante,
apresentar ao titular a identidade do representante e os dados da operação, e verificar que
ela só prossegue após a aprovação humana e autorização independente.

**Acceptance Scenarios**:

1. **Dado que** uma solicitação sensível foi iniciada por um representante identificado em
   nome do titular, **Quando** a aprovação é apresentada, **Então** o titular pode identificar quem
   iniciou a solicitação, em nome de quem ela foi feita e quais dados serão executados.
2. **Dado que** a identidade do representante ou do titular em nome de quem ele atua não pode
   ser verificada, **Quando** a solicitação é avaliada, **Então** o sistema a recusa sem executar
   a operação.
3. **Dado que** uma operação delegada exige aprovação humana, **Quando** o agente ou
   representante tenta aprová-la sem o titular, **Então** o sistema não considera essa ação
   como aprovação do titular.
4. **Dado que** o titular rejeita a solicitação delegada, **Quando** o fluxo termina, **Então** a
   operação não é executada e a rejeição permanece atribuível ao titular e à solicitação
   correspondente.
5. **Dado que** o titular não está disponível para aprovar a solicitação delegada durante o
   fluxo, **Quando** a decisão não pode ser obtida, **Então** a solicitação é cancelada sem
   execução e não fica aguardando aprovação posterior.

---

### User Story 3 - Recuperar o resultado de uma tentativa interrompida (Priority: P3)

Como titular ou responsável por um recurso, quero que uma interrupção ou repetição de uma
solicitação não aplique a mesma operação duas vezes e que o resultado seja esclarecido, para
evitar efeitos duplicados e incerteza sobre o estado da operação.

**Why this priority**: Falhas de comunicação podem ocorrer depois que uma operação já foi
aceita para execução; uma repetição segura é necessária para proteger o titular e permitir
uma recuperação consistente.

**Independent Test**: É possível interromper uma tentativa após o início da execução e
reenviar a mesma solicitação, verificando que não há execução duplicada e que o resultado
final ou o estado de execução não confirmado é informado.

**Acceptance Scenarios**:

1. **Dado que** uma solicitação de execução já foi processada, **Quando** a mesma solicitação é
   reenviada, **Então** o titular recebe o resultado associado à operação original e nenhum
   segundo efeito da operação ocorre.
2. **Dado que** não é possível confirmar se uma operação externa foi concluída, **Quando**
   ocorre uma nova tentativa, **Então** o sistema informa que o resultado não foi confirmado
   e verifica ou reconcilia o resultado anterior antes
   de iniciar outra execução.
3. **Dado que** duas tentativas simultâneas usam a mesma aprovação, **Quando** ambas são
   processadas, **Então** no máximo uma delas aplica o efeito da operação e as demais recebem o
   resultado correspondente ou um estado ainda não concluído.
4. **Dado que** uma operação é aprovada, rejeitada ou executada, **Quando** sua decisão é
   registrada para auditoria, **Então** o registro identifica o titular, o representante
   quando houver, a operação e o resultado, sem expor credenciais ou tokens.

### Edge Cases

- Se os dados materiais da operação mudarem depois da confirmação e antes da execução, a
  aprovação anterior deixa de ser válida e uma nova confirmação é exigida.
- Se a autenticação adicional for cancelada, falhar ou não satisfizer o nível exigido, a
  operação permanece sem execução e o sistema não inicia um ciclo infinito de desafios.
- Se a autorização de política expirar ou mudar antes da execução, o sistema reavalia a
  permissão e não executa uma operação que deixou de ser autorizada.
- Se uma aprovação expirar, já tiver sido consumida ou pertencer a outra operação, o
  sistema a rejeita.
- Se a execução externa terminar, mas sua resposta não chegar, o sistema mantém um estado
  não confirmado e reconcilia o resultado antes de tentar executar novamente.
- Se dados essenciais de atribuição estiverem ausentes em uma solicitação delegada, o
  sistema recusa a solicitação sem tratá-la como uma ação direta do titular.
- Se o titular não puder responder durante o fluxo interativo, o sistema cancela a
  solicitação sem execução; não há fila de operações aguardando aprovação posterior.
- Se o limite de falhas de autenticação ou confirmação definido pela política for atingido,
  novas tentativas da operação são temporariamente limitadas sem bloquear automaticamente
  a conta inteira.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: O sistema DEVE determinar, conforme a política aplicável à operação, se são
  exigidas autenticação reforçada e confirmação transacional.
- **FR-002**: O sistema DEVE avaliar separadamente a força e a atualidade da autenticação,
  a autorização do titular para a ação e a aprovação dos dados da operação.
- **FR-003**: O sistema DEVE recusar a execução quando a autenticação exigida não for
  satisfeita, quando a autorização de política for negada ou quando uma aprovação
  transacional obrigatória estiver ausente, inválida ou expirada.
- **FR-004**: Antes da aprovação, o sistema DEVE apresentar ao titular os dados materiais
  da operação, incluindo origem, destinatário, valor e moeda quando aplicáveis, além de
  quaisquer outros dados que alterem o efeito da operação.
- **FR-005**: O sistema DEVE vincular a aprovação do titular aos dados materiais exatos
  apresentados e à operação correspondente.
- **FR-006**: O sistema DEVE exigir nova aprovação se qualquer dado material da operação
  mudar antes da execução.
- **FR-007**: O sistema DEVE limitar cada aprovação à operação a que se refere, aplicar
  uma validade definida pela política e impedir seu consumo mais de uma vez.
- **FR-008**: O sistema DEVE permitir que o titular rejeite ou cancele a solicitação e DEVE
  impedir a execução após rejeição, cancelamento, falha de autenticação ou indisponibilidade
  do titular durante o fluxo. A solicitação DEVE ser cancelada, não mantida pendente para
  aprovação posterior.
- **FR-009**: Para solicitações delegadas, o sistema DEVE preservar a identidade do agente
  ou representante e do titular em nome de quem ele atua, apresentar essa atribuição ao
  titular e exigir a aprovação humana quando determinada pela política, durante o mesmo
  fluxo interativo da solicitação.
- **FR-010**: O sistema DEVE impedir que a autenticação ou aprovação do agente seja tratada
  como consentimento do titular quando a política exigir aprovação humana.
- **FR-011**: O sistema DEVE reconhecer uma repetição da mesma solicitação de execução e
  retornar ou manter o resultado da operação original sem produzir um segundo efeito.
- **FR-012**: Quando o resultado da execução não puder ser confirmado, o sistema DEVE
  reconciliar ou verificar o resultado anterior antes de iniciar uma nova execução.
- **FR-013**: O sistema DEVE registrar evidência auditável da identidade do titular, do
  representante delegado quando houver, da operação, das decisões e do resultado, sem
  registrar credenciais, tokens ou dados pessoais desnecessários.
- **FR-014**: As mensagens de falha DEVEM informar se a operação foi negada, cancelada,
  rejeitada ou se permanece com resultado não confirmado, sem expor segredos.
- **FR-015**: Após atingir o limite de falhas definido pela política, o sistema DEVE limitar
  temporariamente novas tentativas de autenticação ou confirmação da operação sem bloquear
  automaticamente a conta inteira.

### Key Entities *(include if feature involves data)*

- **Solicitação de operação**: A ação sensível pretendida e seus dados materiais, estado e
  vínculo com o titular e, quando aplicável, com quem a iniciou.
- **Contexto de autenticação**: Evidência da identidade, força e atualidade da autenticação
  usada para avaliar a operação.
- **Decisão de autorização**: Resultado da avaliação de permissão para o titular e para a
  ação solicitada.
- **Aprovação transacional**: Consentimento do titular ligado aos dados exatos e à
  solicitação correspondente, com validade e estado de consumo.
- **Atribuição de delegação**: Relação entre o representante que iniciou a ação e o titular
  em nome de quem ela foi solicitada.
- **Resultado de execução**: Estado confirmado, rejeitado ou ainda não confirmado da
  operação, incluindo sua relação com tentativas repetidas.

## Fora de Escopo

- Implementar o processamento central dos domínios de negócio ou definir regras para
  determinar quais operações exigem step-up e aprovação.
- Escolher ou construir um provedor específico de identidade, um método de autenticação
  específico ou um mecanismo específico de notificação.
- Selecionar ou implementar protocolos específicos de interoperabilidade entre agentes,
  provedores de identidade ou APIs.
- Substituir a política corporativa de autorização por MFA ou por aprovação transacional.
- Definir uma experiência completa de cadastro, recuperação de conta ou gestão de
  autenticadores.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Em 100% dos cenários de aceitação em que autenticação, autorização ou aprovação
  obrigatória falhe, a operação permanece sem execução.
- **SC-002**: Em 100% dos testes de alteração de dados materiais, a aprovação anterior é
  recusada e uma nova aprovação é exigida.
- **SC-003**: Em testes de repetição e concorrência, cada operação aprovada produz no máximo
  um efeito da operação.
- **SC-004**: Em 100% das solicitações delegadas de teste, o titular consegue identificar
  quem iniciou a operação, em nome de quem ela foi iniciada e quais dados serão executados
  antes de aprová-la.
- **SC-005**: Em 100% dos cenários de resultado externo não confirmado, o sistema verifica
  o resultado anterior antes de iniciar uma nova execução.
- **SC-006**: Os registros de auditoria dos cenários de aceitação identificam titular,
  representante quando presente, operação, decisões e resultado sem incluir credenciais ou
  tokens.
- **SC-007**: Em todos os testes que atingem o limite de falhas configurado, novas tentativas
  são temporariamente limitadas e a conta inteira não é bloqueada automaticamente.

## Assumptions

- O sistema já possui uma política de risco que determina quais operações exigem
  autenticação reforçada, qual força e atualidade são requeridas e quando a aprovação humana
  é obrigatória.
- O fluxo protege qualquer operação sensível, financeira ou não; a execução final é
  realizada pelo serviço de negócio correspondente, cujo resultado pode ser consultado ou
  reconciliado.
- O titular tem acesso a um canal confiável que apresenta os dados da operação e permite
  aprovar, rejeitar ou cancelar.
- O serviço de identidade existente fornece evidência suficiente para avaliar a força e a
  atualidade da autenticação, sem que um rótulo do provedor seja tomado isoladamente como
  garantia de um método específico.
- Os dados materiais são específicos ao tipo de operação e serão definidos antes do
  planejamento detalhado; para transferências, incluem no mínimo origem, destinatário,
  valor e moeda.
