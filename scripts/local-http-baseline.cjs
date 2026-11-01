// Loopback-only transport baseline. No database, authentication, files or external requests.
const http = require('node:http');
let open = 0, peak = 0, requests = 0;
const server = http.createServer((req, res) => {
  requests++;
  req.resume();
  res.setHeader('Content-Type', 'application/json');
  res.end(JSON.stringify({ data: [], baseline: true }));
});
server.on('connection', socket => { open++; peak = Math.max(peak, open); socket.on('close', () => open--); });
server.listen({ port: 0, host: '127.0.0.1' }, () => console.log(JSON.stringify(server.address())));
setInterval(() => console.log(JSON.stringify({ requests, open, peak })), 5000).unref();
