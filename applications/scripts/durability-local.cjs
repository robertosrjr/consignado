// Executar na mesma sessao de local-up.ps1: o novo worker herda os segredos transitorios.
const {states,ledger,sleep} = require('./capacity-local.cjs');
const {randomUUID} = require('node:crypto');
const {readFileSync,writeFileSync,openSync,closeSync} = require('node:fs');
const {join} = require('node:path');
const {spawn} = require('node:child_process');
const root = join(__dirname,'..');
async function main() {
  if (process.env.WORKER_PROPOSALS_TYPE !== 'dynamodb' || process.env.AWS_ENDPOINT_URL_DYNAMODB !== 'http://127.0.0.1:8000') throw Error('Exige sessao local duravel herdada de local-up.ps1');
  const tenant = 'banco-001';
  const token = (await (await fetch(`http://127.0.0.1:8082/token?tenant=${tenant}`)).json()).access_token;
  const keys = Array.from({length:6},()=>randomUUID());
  async function submit(key) {
    const response = await fetch('http://127.0.0.1:8080/v1/proposals',{method:'POST',
      headers:{'content-type':'application/json',authorization:`Bearer ${token}`,'X-Tenant-ID':tenant,'Idempotency-Key':key},
      body:JSON.stringify({customerCpf:'52998224725',convenioCode:'INSS',isPortability:true,amount:1000,installments:84}),
      signal:AbortSignal.timeout(15000)});
    await response.arrayBuffer();
    if (response.status !== 202) throw Error(`Aceite local: ${response.status}`);
  }
  async function waitFor(cohort) {
    const deadline = Date.now()+120000;
    do {
      const snapshot = await states();
      if (cohort.every(key=>snapshot.get(key)==='CONFIRMED')) return;
      await sleep(1000);
    } while(Date.now()<deadline);
    throw Error('Coorte nao confirmou no prazo do teste');
  }
  const before = await ledger();
  await Promise.all(keys.slice(0,3).map(submit));
  await waitFor(keys.slice(0,3));
  const pidFile = join(root,'.local/pids.json');
  const pids = JSON.parse(readFileSync(pidFile,'utf8').replace(/^\uFEFF/,''));
  process.kill(Number(pids.worker));
  await sleep(1000);
  // Reentrega explicita de terminais apos queda; mais tres propostas novas ficam na fila.
  for (const key of keys.slice(0,3)) {
    const response = await fetch('http://127.0.0.1:9324/000000000000/sqs-fast-lane-queue',{
      method:'POST',headers:{'content-type':'application/x-www-form-urlencoded'},
      body:new URLSearchParams({Action:'SendMessage',Version:'2012-11-05',
        MessageBody:JSON.stringify({schemaVersion:1,key,tenant,lane:'FAST',priorityScore:60})}),
      signal:AbortSignal.timeout(15000)});
    if (!response.ok) throw Error(`Reentrega SQS local: ${response.status}`);
    await response.arrayBuffer();
  }
  await Promise.all(keys.slice(3).map(submit));
  const out = openSync(join(root,'.local/logs/worker-recovery.out.log'),'a');
  const err = openSync(join(root,'.local/logs/worker-recovery.err.log'),'a');
  const child = spawn('java',['-Dmanagement.metrics.distribution.percentiles-histogram.http.server.requests=true','-jar',join(root,'worker/target/app.jar')],{
    cwd:root,env:process.env,windowsHide:true,detached:true,stdio:['ignore',out,err]});
  child.on('error', error=>{console.error(error.message);process.exitCode=1;});
  child.unref(); closeSync(out); closeSync(err);
  if (!child.pid) throw Error('Falha ao reiniciar worker');
  pids.worker=child.pid; writeFileSync(pidFile,JSON.stringify(pids,null,2));
  await waitFor(keys);
  // Permitir que as reentregas terminais sejam consumidas antes da conciliacao.
  await sleep(5000);
  const uniqueDelta = await ledger()-before;
  const report = {observedAt:new Date().toISOString(),scenario:'worker_crash_terminal_replay_and_pending_queue',
    terminalBeforeCrash:3,newWhileStopped:3,terminalAfterRestart:6,uniqueFinancialDelta:uniqueDelta,
    status:uniqueDelta===6?'PASS':'FAIL'};
  writeFileSync(join(root,'.local/durability-worker-restart.json'),JSON.stringify(report,null,2));
  console.log(JSON.stringify(report));
  if (report.status !== 'PASS') process.exitCode=1;
}
main().catch(error=>{console.error(error.message);process.exitCode=1;});
