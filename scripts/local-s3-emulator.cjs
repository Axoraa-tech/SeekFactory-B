// Synthetic loopback-only S3 protocol fixture. Install s3rver outside the application dependency tree.
const fs = require('node:fs');
const S3rver = require(process.env.S3RVER_MODULE || 's3rver');
const directory = process.argv[2];
if (!directory) throw new Error('A disposable test directory is required');
fs.mkdirSync(directory, { recursive: true });
const cors = `<CORSConfiguration><CORSRule><AllowedOrigin>http://127.0.0.1:3000</AllowedOrigin>
<AllowedMethod>GET</AllowedMethod><AllowedMethod>HEAD</AllowedMethod><AllowedHeader>Range</AllowedHeader>
<ExposeHeader>Content-Range</ExposeHeader><ExposeHeader>Accept-Ranges</ExposeHeader><ExposeHeader>ETag</ExposeHeader>
<MaxAgeSeconds>60</MaxAgeSeconds></CORSRule></CORSConfiguration>`;
const server = new S3rver({ address: '127.0.0.1', port: 0, silent: true, directory,
  configureBuckets: [{ name: 'synthetic-media', configs: [cors] }] });
server.run().then(address => console.log(JSON.stringify(address))).catch(error => { console.error(error); process.exit(1); });
process.on('SIGTERM', () => server.close().finally(() => process.exit(0)));
