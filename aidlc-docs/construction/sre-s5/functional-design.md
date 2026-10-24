# S5 — Durabilidade e recuperação

Execução autorizada na retomada de 23/10/2026; somente local, sem Docker ou AWS.

Estado e eventos do worker são gravados na mesma transação DynamoDB. A chave da proposta é estável em todas as chamadas ao legado. Estados terminais são absorventes. O registro terminal funciona como pendência durável da decisão na Inbox: uma reentrega repara a decisão idempotentemente usando o instante persistido, sem nova chamada ao legado.

O dispatcher mantém índice esparso de propostas RECEIVED. O Sweeper republica após uma carência e remove a pendência somente após o envio. Publicação com resposta perdida pode duplicar a mensagem; a correção depende da mesma chave no worker e no legado.

Validação necessária: criação concorrente, estado e histórico após reconstruir o repositório, timeout após commit, terminal sem regressão, recuperação da decisão e falha de publicação seguida de reconciliação. Reiniciar o worker preserva os dados enquanto DynamoDB Local permanece ativo; o emulador atual em memória não preserva dados ao encerrar a infraestrutura local.

Contadores locais de estado não são inventário durável. Capacidade deve ser conciliada pela tabela e pelas respostas, sem assumir que métricas locais após reinício representam todo o histórico.
