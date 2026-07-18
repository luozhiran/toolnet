const assert = require('node:assert/strict');
const { after, before, describe, it } = require('node:test');

const app = require('../server/server');

describe('NetworkSceneActivity service scenarios', () => {
    let server;
    let baseUrl;

    before(async () => {
        server = app.listen(0, '127.0.0.1');
        await new Promise((resolve) => server.once('listening', resolve));
        const { port } = server.address();
        baseUrl = `http://127.0.0.1:${port}`;
    });

    after(async () => {
        await new Promise((resolve, reject) => {
            server.close((error) => error ? reject(error) : resolve());
        });
    });

    it('supports Net GET callback scene', async () => {
        const body = await getJson('/get?scene=net-get');

        assert.equal(body.args.scene, 'net-get');
        assert.match(body.url, /\/get\?scene=net-get$/);
    });

    it('supports Net POST JSON callback scene', async () => {
        const payload = { scene: 'net-post-json', time: 1234567890 };
        const body = await postJson('/post', payload);

        assert.deepEqual(body.json, payload);
        assert.equal(JSON.parse(body.data).scene, 'net-post-json');
    });

    it('supports Flow GET scene', async () => {
        const body = await getJson('/get?scene=net-flow-get');

        assert.equal(body.args.scene, 'net-flow-get');
    });

    it('supports Flow POST JSON scene', async () => {
        const payload = { scene: 'net-flow-post-json', time: 1234567890 };
        const body = await postJson('/post', payload);

        assert.deepEqual(body.json, payload);
    });

    it('supports callback binary download scene', async () => {
        const response = await fetch(`${baseUrl}/bytes/4096`);
        const body = Buffer.from(await response.arrayBuffer());

        assert.equal(response.status, 200);
        assert.equal(Number(response.headers.get('content-length')), 4096);
        assert.equal(body.length, 4096);
        assert.equal(body[0], 0);
        assert.equal(body[255], 255);
        assert.equal(body[256], 0);
    });

    it('supports Flow binary download scene with range progress', async () => {
        const response = await fetch(`${baseUrl}/bytes/4096`, {
            headers: { Range: 'bytes=1024-2047' }
        });
        const body = Buffer.from(await response.arrayBuffer());

        assert.equal(response.status, 206);
        assert.equal(response.headers.get('content-range'), 'bytes 1024-2047/4096');
        assert.equal(body.length, 1024);
    });

    it('supports Retrofit suspend GET scene', async () => {
        const body = await getJson('/get?scene=net-retrofit-suspend');

        assert.equal(body.args.scene, 'net-retrofit-suspend');
    });

    it('supports Retrofit Flow GET scene', async () => {
        const body = await getJson('/get?scene=net-retrofit-flow');

        assert.equal(body.args.scene, 'net-retrofit-flow');
    });

    async function getJson(path) {
        const response = await fetch(`${baseUrl}${path}`);
        assert.equal(response.status, 200);
        return response.json();
    }

    async function postJson(path, payload) {
        const response = await fetch(`${baseUrl}${path}`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        });
        assert.equal(response.status, 200);
        return response.json();
    }
});
