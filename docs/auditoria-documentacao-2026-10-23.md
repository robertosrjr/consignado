# Revisão documental — 23/10/2026

## Resultado

O estado atual foi concentrado em [estado-atual.md](estado-atual.md). O índice, os guias de aplicação e os documentos SRE apontam para ele. S0–S5 estão validadas; S6 continua com homologação pendente. MFA, step-up e aprovação transacional ficam fora da PoC.

## Correções

- Guias que ainda tratavam persistência, medição contínua e burn rate como entregas futuras foram atualizados.
- A documentação diferencia o padrão `memory` do worker e o modo DynamoDB selecionado pelo script local. Reinício do worker não equivale a reinício dos emuladores.
- Pesos e limiar do dispatcher foram conferidos no código. A descrição de entrega foi corrigida para admitir republicação entre envio à fila e marcação na Inbox.
- Alertas locais e AWS passaram a ter rotas e evidências separadas. O consumidor JSONL local não comprova atendimento humano ou integração com a fila AWS.
- Os limites de J1/J2 e das regras dependentes de Pushgateway foram explicitados. Entrega da instrumentação não significa cobertura completa ou SLO mensal comprovado.
- Os guias SRE e de alertas foram reduzidos, substituindo cópias de catálogos e comandos por links aos documentos responsáveis.
- A narrativa de contexto foi simplificada. Alegações de mercado, custos e conformidade sem evidência foram retiradas desse guia; os pitches originais permanecem históricos.
- Links e caminhos afetados pelas mudanças para `brainstorming/Fase-01/` e `artigos/` foram corrigidos.
- O fechamento de 18/10, auditorias e planos antigos foram preservados com seu recorte temporal. Especificações target continuam como propostas, não inventário implantado.
- O estudo MFA recebeu a decisão de exclusão, correção de origem e aviso sobre diferenças entre artigo e spec.

## Verificação e limites

O verificador de links percorre todos os Markdown descobertos pelo script, incluindo pastas ocultas, e verifica destinos locais inline fora de blocos de código. O resultado final e o comando estão registrados na trilha [AI-DLC](../aidlc-docs/audit.md).

`git diff --check` verifica espaços e conflitos de formatação. Nenhum teste Java, carga, publicação ou recurso AWS foi executado nesta revisão. O resultado de 473 testes é a evidência anterior da S5.

URLs externas, âncoras, afirmações regulatórias e referências de mercado dos materiais históricos não foram revalidadas. Esta revisão documental não resolve lacunas do código nem conclui a S6.
