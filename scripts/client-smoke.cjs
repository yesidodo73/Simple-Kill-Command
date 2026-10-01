const fs = require('node:fs');
const path = require('node:path');
const net = require('node:net');
const { spawn } = require('node:child_process');
const { createRequire } = require('node:module');
const assert = require('node:assert/strict');
const requireClient = createRequire(path.resolve(__dirname, '../build/verification/client/package.json'));
const mineflayer = requireClient('mineflayer');
const minecraftData = requireClient('minecraft-data');
const [directory, java, minecraft, ...args] = process.argv.slice(2);
const bots = [];
const checks = [];
let output = '';
let child;

function waitEvent(target, name, timeout = 30000) {
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => finish(new Error(`Timed out waiting for ${name}`)), timeout);
    const success = (...values) => finish(null, values);
    const error = (failure) => finish(failure instanceof Error ? failure : new Error(String(failure)));
    function finish(failure, values) {
      clearTimeout(timer);
      target.removeListener(name, success);
      target.removeListener('error', error);
      target.removeListener('kicked', error);
      failure ? reject(failure) : resolve(values);
    }
    target.once(name, success);
    target.once('error', error);
    target.once('kicked', error);
  });
}

const delay = (ms, unref = false) => new Promise(resolve => {
  const timer = setTimeout(resolve, ms);
  if (unref) timer.unref();
});
async function connect(username, port) {
  const bot = mineflayer.createBot({ host: '127.0.0.1', port, username, auth: 'offline', version: false, respawn: true });
  bots.push(bot);
  bot.on('error', failure => console.error(`${username}: ${failure.message}`));
  await waitEvent(bot, 'spawn');
  await delay(500);
  return bot;
}

async function main() {
  const port = await new Promise((resolve, reject) => {
    const listener = net.createServer();
    listener.once('error', reject);
    listener.listen(0, '127.0.0.1', () => {
      const available = listener.address().port;
      listener.close(() => resolve(available));
    });
  });
  const properties = path.join(directory, 'server.properties');
  fs.writeFileSync(properties, fs.readFileSync(properties, 'utf8').replace(/^server-port=.*$/m, `server-port=${port}`));
  child = spawn(java, ['-Dsimplekillcommand.testKeepRunning=true', ...args], { cwd: directory, windowsHide: true });
  const exited = waitEvent(child, 'exit', 600000);
  exited.catch(() => {});
  let readyResolve;
  const ready = new Promise(resolve => { readyResolve = resolve; });
  function receive(data) {
    const text = data.toString();
    output += text;
    process.stdout.write(text);
    if (/Done \([^)]+\)!/.test(output)) readyResolve();
  }
  child.stdout.on('data', receive);
  child.stderr.on('data', receive);
  await Promise.race([ready, exited.then(() => { throw new Error('Server exited before startup'); }),
    delay(180000, true).then(() => { throw new Error('Server startup timed out'); })]);
  await delay(500);
  if (fs.existsSync(path.join(directory, 'mods/server-test.jar'))) {
    assert.match(output, /SKC_SMOKE_PASS/, 'in-server regression tests');
  }
  const protocol = minecraftData.versions.pc.find(version => version.minecraftVersion === minecraft)?.version;
  if (!minecraftData(protocol)?.protocol) {
    const consoleChecks = [];
    if (fs.existsSync(path.join(directory, 'plugins'))) {
      for (const command of ['kill', 'suicide', 'selfkill']) {
        const before = output.length;
        child.stdin.write(`${command}\n`);
        await delay(500);
        assert.match(output.slice(before), /This command can only be used by players\./);
        consoleChecks.push(`console-${command}-rejected`);
      }
    }
    const skipped = `minecraft-data has no protocol schema for ${minecraft}`;
    fs.writeFileSync(path.join(directory, 'client-result.json'), JSON.stringify({ passed: false, skipped, minecraft, checks: [], consoleChecks }, null, 2));
    console.log(`SKC_CLIENT_SKIPPED ${skipped}`);
    return;
  }
  const actor = await connect('SelfKillClient', port);
  const other = await connect('OtherClient', port);
  let otherDeaths = 0;
  other.on('death', () => otherDeaths++);
  actor.chat('/kill OtherClient');
  await delay(1000);
  assert.ok(actor.health > 0 && other.health > 0, 'target arguments must not kill either player');
  checks.push('target-argument-rejected');
  for (const command of ['kill', 'suicide', 'selfkill']) {
    const death = waitEvent(actor, 'death');
    const respawn = waitEvent(actor, 'spawn');
    actor.chat(`/${command}`);
    await death;
    assert.equal(otherDeaths, 0, 'another player must remain alive');
    await respawn;
    await delay(500);
    checks.push(`non-op-${command}-death-and-respawn`);
  }
  assert.equal(otherDeaths, 0);
  assert.ok(other.health > 0);
  checks.push('other-player-unaffected');
  fs.writeFileSync(path.join(directory, 'client-result.json'), JSON.stringify({ passed: true, minecraft, checks }, null, 2));
  console.log(`SKC_CLIENT_PASS checks=${checks.length}`);
}

main().catch(error => {
  console.error(error.stack);
  process.exitCode = 1;
}).finally(async () => {
  let forced = false;
  for (const bot of bots) {
    try {
      if (typeof bot.quit === 'function') bot.quit();
      else bot._client?.end();
    } catch (error) { console.error(`Client cleanup: ${error.message}`); }
  }
  if (child && child.exitCode === null) {
    child.stdin.write('stop\n');
    await Promise.race([waitEvent(child, 'exit', 30000), delay(30000, true)]).catch(() => {});
    if (child.exitCode === null) {
      forced = true;
      console.error('SKC_SERVER_STOP_FORCED after normal shutdown timed out');
      child.kill();
      await waitEvent(child, 'exit', 10000).catch(() => {});
    }
  }
  fs.writeFileSync(path.join(directory, 'server-stop-result.json'), JSON.stringify({ graceful: !forced, exitCode: child?.exitCode }, null, 2));
  fs.writeFileSync(path.join(directory, 'client-server.log'), output);
});
