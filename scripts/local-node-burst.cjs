// Independent transport cross-check; synthetic loopback targets only, no automatic application retries.
const fs = require('node:fs');
const { Agent, request } = require(process.env.UNDICI_MODULE || 'undici');
const input = JSON.parse(fs.readFileSync(process.argv[2], 'utf8'));
if (!/^http:\/\/127\.0\.0\.1:\d+(?:\/api\/proxy)?$/.test(input.base)) throw new Error('Loopback test target required');
const agent = new Agent({ connections: 500, pipelining: 1, connect: { timeout: 5000 }, headersTimeout: 30000, bodyTimeout: 30000 });
const outcomes = {}, times = [];
const started = performance.now();
Promise.all(input.tokens.map(async token => {
  for (let step = 0; step < 5; step++) {
    const before = performance.now();
    try {
      const path = step === 4 ? `/api/v1/feed/${input.reel}/like` : '/api/v1/feed?size=10';
      const result = await request(input.base + path, { dispatcher: agent, method: step === 4 ? 'POST' : 'GET',
        headers: { authorization: `Bearer ${token}`, cookie: `sf-access-token=${token}`, 'content-type': 'application/json' },
        body: step === 4 ? '' : undefined });
      await result.body.text();
      const key = `HTTP_${result.statusCode}`;
      outcomes[key] = (outcomes[key] || 0) + 1;
    } catch (error) {
      const key = `${error.code || error.name}:${error.message}`;
      outcomes[key] = (outcomes[key] || 0) + 1;
    }
    times.push(performance.now() - before);
  }
})).then(async () => {
  times.sort((a,b) => a-b);
  console.log(JSON.stringify({ requests: times.length, p95_ms: Math.round(times[Math.floor((times.length-1)*.95)]),
    elapsed_ms: Math.round(performance.now()-started), outcomes }));
  await agent.close();
}).catch(error => { console.error(error); process.exitCode = 1; });
