const fs = require('fs')
const path = require('path')
const os = require('os')
const net = require('net')
const { createHash } = require('crypto')

const leaseName = `\\\\.\\pipe\\genshin-capture-${createHash('sha256').update(os.homedir()).digest('hex').slice(0, 20)}`
let activeLease = null

// Windows releases the pipe when its owner exits, even when no shutdown handler runs.
function acquireSessionLease(name = leaseName) {
    return new Promise((resolve, reject) => {
        const server = net.createServer(socket => socket.destroy())
        server.once('error', error => reject(new Error('Another capture or proxy recovery is active; retry after it finishes.', { cause: error })))
        server.listen(name, () => resolve(() => new Promise(done => server.close(done))))
    })
}

const statePath = path.join(os.homedir(), 'GenshinAnalyzer', 'config', 'proxy-session.json')
const keys = ['ProxyServer', 'ProxyOverride', 'ProxyEnable']
const same = (a, b) => a === null || b === null ? a === b :
    a.type === b.type && (a.type === 'REG_DWORD' ? Number(a.value) === Number(b.value) : a.value === b.value)

// An exclusive, durable journal prevents overlapping captures from replacing the snapshot.
function createSession(registry, file = statePath, ownsLease = false) {
    async function restore(sessionId) {
        if (!sessionId || !fs.existsSync(file)) return false
        const state = JSON.parse(fs.readFileSync(file, 'utf8'))
        if (state.sessionId !== sessionId) return false
        const current = await registry.read()
        if (!same(current.ProxyServer, state.original.ProxyServer) && !same(current.ProxyServer, state.applied.ProxyServer)) {
            console.warn('[proxy] Settings changed outside this capture; preserving current proxy settings')
            fs.unlinkSync(file)
            return false
        }
        // Original values are accepted too, so interrupted restoration can be retried.
        for (const key of [...keys].reverse()) {
            if (same(current[key], state.applied[key]) && !same(current[key], state.original[key])) {
                await registry.write(key, state.original[key])
            }
        }
        fs.unlinkSync(file)
        return true
    }

    async function enable(sessionId, port) {
        if (!sessionId) throw new Error('A proxy capture session ID is required')
        if (!/^\d+$/.test(String(port)) || Number(port) < 1 || Number(port) > 65535) throw new Error('Invalid proxy port')
        if (fs.existsSync(file)) {
            const previous = JSON.parse(fs.readFileSync(file, 'utf8'))
            if (!ownsLease || previous.leaseVersion !== 1) {
                throw new Error('A legacy proxy snapshot needs explicit recovery before starting another capture. Do not delete the snapshot.')
            }
            console.log('[proxy] Recovering an interrupted capture session before starting a new one')
            await restore(previous.sessionId)
        }
        const original = await registry.read()
        const applied = {
            ProxyServer: { type: 'REG_SZ', value: `127.0.0.1:${port}` },
            ProxyOverride: { type: 'REG_SZ', value: 'localhost;127.*;<local>' },
            ProxyEnable: { type: 'REG_DWORD', value: '0x1' },
        }
        fs.mkdirSync(path.dirname(file), { recursive: true })
        const fd = fs.openSync(file, 'wx', 0o600)
        try {
            fs.writeFileSync(fd, JSON.stringify({ sessionId, original, applied, ...(ownsLease ? { leaseVersion: 1 } : {}) }))
            fs.fsyncSync(fd)
        } finally {
            fs.closeSync(fd)
        }
        try {
            for (const key of keys) await registry.write(key, applied[key])
        } catch (error) {
            await restore(sessionId)
            throw error
        }
    }
    return { enable, restore }
}

function windowsSession() {
    const Registry = require('winreg')
    const reg = new Registry({ hive: Registry.HKCU, key: '\\Software\\Microsoft\\Windows\\CurrentVersion\\Internet Settings' })
    const session = createSession({
        read: () => new Promise((resolve, reject) => reg.values((err, values) => {
            if (err) return reject(err)
            resolve(Object.fromEntries(keys.map(key => {
                const entry = values.find(value => value.name === key)
                return [key, entry ? { type: entry.type, value: entry.value } : null]
            })))
        })),
        write: require('./registryWriter').createRegistryWriter(),
    }, statePath, true)
    return {
        enable: async (sessionId, port) => {
            if (activeLease) throw new Error('A capture session is already active')
            const release = await acquireSessionLease()
            activeLease = { sessionId, release }
            try { await session.enable(sessionId, port) }
            catch (error) { activeLease = null; await release(); throw error }
        },
        restore: async sessionId => {
            const held = activeLease?.sessionId === sessionId ? activeLease : null
            const release = held ? held.release : await acquireSessionLease()
            try { return await session.restore(sessionId) }
            finally {
                if (held) activeLease = null
                await release()
            }
        },
    }
}

module.exports = { createSession, windowsSession, acquireSessionLease }

// Direct invocation only restores this session; process termination belongs to the parent.
if (require.main === module && process.platform === 'win32' && process.env.GENSHIN_PROXY_SESSION) {
    Promise.resolve().then(() => windowsSession().restore(process.env.GENSHIN_PROXY_SESSION)).catch(error => {
        console.error('[proxy] Session recovery failed:', error.message)
        process.exitCode = 1
    })
}
