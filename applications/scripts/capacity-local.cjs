// Ensaio exclusivamente local; node scripts/capacity-local.cjs [segundos por patamar].
// Nao registra tokens, payloads ou CPFs. Estado conciliado por chave no DynamoDB Local.
const {createHash, createHmac, randomUUID} = require('node:crypto');
const {mkdirSync, writeFileSync} = require('node:fs');
const {join} = require('node:path');
const sleep = ms => new Promise(resolve => setTimeout(resolve, ms));
const sha = value => createHash('sha256').update(value).digest('hex');
const hmac = (key, value) => createHmac('sha256', key).update(value).digest();
async function dynamo(target, data) {
  const body = JSON.stringify(data), date = new Date().toISOString().replace(/[:-]|\.\d{3}/g, '');
  const scope = `${date.slice(0,8)}/sa-east-1/dynamodb/aws4_request`;
  const headers = `content-type:application/x-amz-json-1.0\nhost:127.0.0.1:8000\nx-amz-date:${date}\nx-amz-target:DynamoDB_20120810.${target}\n`;
  const signed = 'content-type;host;x-amz-date;x-amz-target';
  const canonical = `POST\n/\n\n${headers}\n${signed}\n${sha(body)}`;
  let key = hmac('AWS4test', date.slice(0,8));
  for (const part of ['sa-east-1','dynamodb','aws4_request']) key = hmac(key, part);
  const signature = createHmac('sha256', key).update(`AWS4-HMAC-SHA256\n${date}\n${scope}\n${sha(canonical)}`).digest('hex');
  const response = await fetch('http://127.0.0.1:8000/', {method:'POST', body,
    headers:{'content-type':'application/x-amz-json-1.0','x-amz-date':date,
      'x-amz-target':`DynamoDB_20120810.${target}`,
      authorization:`AWS4-HMAC-SHA256 Credential=test/${scope}, SignedHeaders=${signed}, Signature=${signature}`},
    signal:AbortSignal.timeout(15000)});
  const result = await response.json();
  if (!response.ok) throw new Error(`DynamoDB Local ${response.status}: ${result.__type}`);
  return result;
}
async function states() {
  let start, result = new Map();
  do {
    const page = await dynamo('Scan', {TableName:'WorkerProposal', ConsistentRead:true,
      ProjectionExpression:'proposalKey, #s, sk', ExpressionAttributeNames:{'#s':'status'},
      ...(start ? {ExclusiveStartKey:start} : {})});
    for (const item of page.Items) if (item.sk?.S === 'STATE') result.set(item.proposalKey.S, item.status.S);
    start = page.LastEvaluatedKey;
  } while (start && Object.keys(start).length);
  return result;
}
async function ledger() {
  const response = await fetch('http://127.0.0.1:8081/actuator/prometheus', {signal:AbortSignal.timeout(15000)});
  if (!response.ok) throw Error('Metricas do legado indisponiveis');
  const text = await response.text();
  const line = text.split('\n').find(line => /^legacy_averbacoes_total\{/.test(line) && line.includes('kind="unique"'));
  if (!line) throw Error('Contador de averbacoes unicas ausente');
  return Number(line.trim().split(/\s+/).at(-1));
}
async function deadlines(accepted) {
  let start;
  const lanes = {fast:{total:0,onTime:0,missing:0,maxSeconds:0},standard:{total:0,onTime:0,missing:0,maxSeconds:0}};
  do {
    const page = await dynamo('Scan', {TableName:'DurableInbox', ConsistentRead:true,
      ProjectionExpression:'idempotencyKey, lane, createdAt, decidedAt',
      ...(start ? {ExclusiveStartKey:start} : {})});
    for (const item of page.Items) {
      if (!accepted.has(item.idempotencyKey?.S)) continue;
      const lane = lanes[item.lane?.S];
      if (!lane) continue;
      lane.total++;
      if (!item.decidedAt?.N) {lane.missing++; continue;}
      const elapsed = (Number(item.decidedAt.N)-Number(item.createdAt.N))/1000;
      lane.maxSeconds = Math.max(lane.maxSeconds,elapsed);
      if (elapsed <= (item.lane.S === 'fast' ? 120:2700)) lane.onTime++;
    }
    start = page.LastEvaluatedKey;
  } while (start && Object.keys(start).length);
  return lanes;
}
async function main() {
  const seconds = Number(process.argv[2] || 15);
  if (!Number.isInteger(seconds) || seconds < 5 || seconds > 60) throw Error('Duracao deve ser 5..60 segundos');
  const directory = join(__dirname, '../.local', `capacity-${new Date().toISOString().replace(/[:.]/g,'-')}`);
  mkdirSync(directory, {recursive:true});
  const report = {startedAt:new Date().toISOString(), seconds, stages:[], status:'INCOMPLETE'};
  report.environment = {debug:process.env.DEBUG || 'inherited-default',
    localDynamoTimeouts:process.env.CAPACITY_DYNAMO_TIMEOUTS || 'local-up defaults'};
  const save = () => writeFileSync(join(directory,'report.json'), JSON.stringify(report,null,2));
  try {
    await states(); // preflight exige a tabela duravel
    const tokens = await Promise.all(Array.from({length:20}, async (_,i) => {
      const tenant = `banco-${String(i+1).padStart(3,'0')}`;
      const response = await fetch(`http://127.0.0.1:8082/token?tenant=${tenant}`);
      if (!response.ok) throw Error('Emissor local indisponivel');
      return {tenant, token:(await response.json()).access_token};
    }));
    const rates = (process.argv[3] || '10,30,60,120,240').split(',').map(Number);
    if (!rates.length || rates.some(rate=>!Number.isInteger(rate) || rate<1 || rate>1000)) throw Error('Taxas devem ser inteiros entre 1 e 1000');
    for (const rate of rates) {
      const ledgerBefore = await ledger();
      const accepted = new Set(), generated = [], timings = [], codes = {}, tasks = [];
      let errors = 0, inflight = 0, peakInflight = 0;
      const start = performance.now();
      for (let i=0; i<rate*seconds; i++) {
        await sleep(Math.max(0,start+i*1000/rate-performance.now()));
        let proposal;
        if (i%5 === 4) proposal = generated.at(-1);
        else {
          proposal = {key:randomUUID(), partner:tokens[i%tokens.length], fast:i%2 === 0};
          generated.push(proposal);
        }
        inflight++; peakInflight = Math.max(peakInflight,inflight);
        tasks.push((async () => {
          const began = performance.now();
          try {
            const response = await fetch('http://127.0.0.1:8080/v1/proposals', {method:'POST',
              headers:{'content-type':'application/json', authorization:`Bearer ${proposal.partner.token}`,
                'X-Tenant-ID':proposal.partner.tenant,'Idempotency-Key':proposal.key},
              body:JSON.stringify({customerCpf:'52998224725',convenioCode:'INSS',isPortability:proposal.fast,amount:1000,installments:84}),
              signal:AbortSignal.timeout(15000)});
            await response.arrayBuffer();
            codes[response.status] = (codes[response.status]||0)+1;
            if (response.status === 202) accepted.add(proposal.key); else errors++;
          } catch { errors++; }
          finally {timings.push(performance.now()-began); inflight--;}
        })());
        if (inflight > 500) { errors++; break; }
      }
      await Promise.all(tasks);
      const requestSeconds = (performance.now()-start)/1000;
      const draining = performance.now();
      let found;
      do {
        found = await states();
        if ([...accepted].every(key => ['CONFIRMED','FAILED'].includes(found.get(key)))) break;
        await sleep(2000);
      } while (performance.now()-draining < 120000);
      timings.sort((a,b)=>a-b);
      const terminal = [...accepted].filter(key => ['CONFIRMED','FAILED'].includes(found.get(key))).length;
      const confirmed = [...accepted].filter(key=>found.get(key)==='CONFIRMED').length;
      const confirmedGenerated = generated.filter(p=>found.get(p.key)==='CONFIRMED').length;
      const unacknowledgedTerminal = generated.filter(p=>!accepted.has(p.key) && ['CONFIRMED','FAILED'].includes(found.get(p.key))).length;
      const uniqueFinancialDelta = await ledger() - ledgerBefore;
      const j2 = await deadlines(accepted);
      const stage = {rate, requests:timings.length, uniqueGenerated:generated.length, acceptedUnique:accepted.size,
        replayFactor:timings.length/generated.length, codes, errors, requestSeconds,
        actualRps:timings.length/requestSeconds, p95Ms:timings[Math.ceil(timings.length*.95)-1],
        p99Ms:timings[Math.ceil(timings.length*.99)-1], peakInflight,
        terminal, confirmed, confirmedGenerated, unacknowledgedTerminal, uniqueFinancialDelta, j2,
        drainSeconds:(performance.now()-draining)/1000,
        status:errors === 0 && accepted.size === generated.length && terminal === accepted.size && uniqueFinancialDelta === confirmed ? 'PASS':'FAIL'};
      stage.latencySloMet = stage.p95Ms <= 100 && stage.p99Ms <= 200;
      stage.targetRateMet = stage.actualRps >= rate*.95;
      stage.j2SloMet = j2.fast.total+j2.standard.total === accepted.size
        && j2.fast.missing+j2.standard.missing === 0
        && j2.fast.onTime >= j2.fast.total*.95 && j2.standard.onTime >= j2.standard.total*.90;
      report.stages.push(stage); save(); console.log(JSON.stringify(stage));
      if (stage.status !== 'PASS') break;
    }
    report.status = report.stages.length === rates.length && report.stages.every(s=>s.status==='PASS') ? 'PASS':'INCOMPLETE';
  } finally {report.finishedAt = new Date().toISOString(); save(); console.log(`Evidencia: ${directory}`);}
}
module.exports = {dynamo, states, ledger, sleep};
if (require.main === module) main().catch(error => {console.error(error.message); process.exitCode=1;});
