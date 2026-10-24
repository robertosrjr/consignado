# S6 — Método do ensaio

Gerador Node.js usa HTTP local com agendamento por tempo; registra vazão efetiva para expor atraso do próprio gerador. Cada quinto envio reutiliza a última proposta (fator esperado 1,25). Vinte tenants evitam confundir a cota individual com a capacidade global. Prazo de requisição de 15 s e limite de 500 requisições simultâneas impedem crescimento ilimitado no gerador.

Após cada patamar, aguardar até 120 s e conciliar as chaves aceitas com STATE no DynamoDB Local e o delta de averbações únicas no legado. Qualquer divergência interrompe a progressão e preserva o relatório. Requisição com resposta perdida é erro, mesmo que possa ter sido aceita: ausência de certeza não é sucesso.

J2 é medido por createdAt/decidedAt da Inbox e lane. O ensaio de curta duração não estima percentis de 30 dias nem comprova capacidade sustentada de produção. Aging só será considerado se houver evidência de inanição; não faz parte do ensaio inicial.
