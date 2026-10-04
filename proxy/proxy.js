const mitmproxy = require('node-mitmproxy')
const http = require('http')
const path = require('path')
const os = require('os')
const { randomUUID } = require('crypto')
process.env.GENSHIN_PROXY_SESSION ||= randomUUID()
const { setSystemProxy } = require('./systemProxy')

const KOTLIN_PORT = 3000
const PROXY_PORT = process.argv[2] || 8080
const CAPTURE_TIMEOUT_MS = 120000
const userPath = os.homedir()

const EXIT = {
    SUCCESS: 0,
    FAILED: 1,
    TIMEOUT: 2,
    DELIVERY_FAILED: 3,
}

function proxyCaptureError(code, message) {
    const err = new Error(message)
    err.exitCode = code
    return err
}

function exitCodeForError(err) {
    if (err && typeof err.exitCode === 'number') return err.exitCode
    const msg = err?.message || ''
    if (msg.includes('Timeout waiting for authkey')) return EXIT.TIMEOUT
    if (msg.includes('Failed to send authkey to Kotlin')) return EXIT.DELIVERY_FAILED
    return EXIT.FAILED
}

let proxyWasEnabled = false
let setupPromise = null
let stopPromise = null
let shuttingDown = false

const fixAuthkey = (url) => {
    const mr = url.match(/authkey=([^&]+)/)
    if (mr && mr[1] && mr[1].includes('=') && !mr[1].includes('%')) {
        return url.replace(/authkey=([^&]+)/, `authkey=${encodeURIComponent(mr[1])}`)
    }
    return url
}

const notifyKotlin = (url) => {
    const token = process.env.GENSHIN_CAPTURE_TOKEN
    if (!token) return Promise.reject(proxyCaptureError(EXIT.DELIVERY_FAILED, 'Missing capture receiver token'))
    const fixed = fixAuthkey(url)
    const body = JSON.stringify({ url: fixed })

    return new Promise((resolve, reject) => {
        const req = http.request({
            hostname: '127.0.0.1',
            port: KOTLIN_PORT,
            path: '/authkey',
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Content-Length': Buffer.byteLength(body),
                'X-Capture-Token': token,
            },
        }, (res) => {
            let data = ''
            res.on('data', (chunk) => { data += chunk })
            res.on('end', () => {
                if (res.statusCode === 200) {
                    console.log(`[proxy] Kotlin HTTP 200 acknowledged; URL received (${fixed.length} chars)`)
                    resolve(fixed)
                } else {
                    reject(proxyCaptureError(EXIT.DELIVERY_FAILED, `Failed to send authkey to Kotlin server. Status code: ${res.statusCode}`))
                }
            })
        })

        req.on('error', (err) => {
            const errorMsg = `Failed to send authkey to Kotlin server. Error message: ${err.message}`
            console.error('[proxy]', errorMsg)
            reject(proxyCaptureError(EXIT.DELIVERY_FAILED, errorMsg))
        })

        req.setTimeout(5000, () => {
            req.destroy()
            const errorMsg = 'Failed to send authkey to Kotlin server. Error: Request timeout'
            console.error('[proxy]', errorMsg)
            reject(proxyCaptureError(EXIT.DELIVERY_FAILED, errorMsg))
        })

        req.write(body)
        req.end()
    })
}

let proxyServerPromise = null
let proxyServer = null
let proxyStarted = false

const MIHOYO_HOST = /webstatic([^\.]{2,10})?\.(mihoyo|hoyoverse)\.com/

const createProxyServer = (port) => {
    return new Promise((resolve, reject) => {
        let localCaptured = false

        try {
            proxyServer = mitmproxy.createProxy({
                sslConnectInterceptor: (req) => MIHOYO_HOST.test(req.url),
                requestInterceptor: (rOptions, req, res, ssl, next) => {
                    next()

                    if (localCaptured) return

                    if (MIHOYO_HOST.test(rOptions.hostname) && /authkey=[^&]+/.test(rOptions.path)) {
                        localCaptured = true
                        const protocol = ssl ? 'https:' : 'http:'
                        const fullUrl = `${protocol}//${rOptions.hostname}${rOptions.path}`

                        console.log('[proxy] Notify kotlin in process')

                        notifyKotlin(fullUrl)
                            .then((url) => resolve(url))
                            .catch((err) => {
                                console.error('[proxy] notifyKotlin error:', err.message)
                                reject(err)
                            })
                    }
                },
                responseInterceptor: (req, res, proxyReq, proxyRes, ssl, next) => {
                    next()
                },
                getPath: () => path.join(userPath, 'node-mitmproxy'),
                port,
            })

            console.log(`[proxy] MITM proxy listening on port ${port}`)
        } catch (e) {
            console.error('[proxy] Failed to create MITM proxy. Error message: ', e.message)
            reject(e)
        }
    })
}

const startProxy = async (port = PROXY_PORT) => {
    if (proxyStarted) {
        return
    }

    try {
        try {
            setupPromise = setSystemProxy(true, port)
            await setupPromise
            proxyWasEnabled = true
        } catch (e) {
            // debug print
            if (e.message === 'MANUAL_PROXY_REQUIRED') {
                console.log('[Proxy] Manual proxy setup required! Port: ' + port)
            } else {
                throw e
            }
        }

        if (shuttingDown) throw new Error('Capture shutdown requested')
        if (!proxyServerPromise) {
            proxyServerPromise = createProxyServer(port)
        }

        proxyStarted = true
        console.log('')
        console.log('[proxy] Proxy is ready. User procedure:')
        console.log('[proxy] 1. Log into the game client')
        console.log('[proxy] 2. Open wish UID')
        console.log('[proxy] 3. Click wish history')
        console.log(`[proxy] 4. wait for ${CAPTURE_TIMEOUT_MS / 1000} seconds`)
        console.log('')
    } catch (e) {
        console.error('[proxy] Failed to start proxy:', e.message)
        proxyStarted = false
        throw e
    }
}

const stopProxyOnce = async () => {
    // A shutdown during registry setup must wait until setup/rollback has completed.
    if (setupPromise) {
        try { await setupPromise; proxyWasEnabled = true } catch (_) { /* setup rolls back */ }
        setupPromise = null
    }
    try {
        if (proxyWasEnabled) {
            console.log('[proxy] Restoring this capture session proxy settings')
            await setSystemProxy(false)
            console.log('[proxy] Session proxy restoration completed')
            proxyWasEnabled = false
        }
        proxyStarted = false

        if (proxyServer) {
            console.log('[proxy] Closing MITM proxy server')
            proxyServer.close()
            console.log('[proxy] MITM proxy close requested')
            proxyServer = null
        }

        proxyServerPromise = null
    } catch (e) {
        console.error('[proxy] Failed to disable proxy. Error message: ', e.message)
        proxyStarted = false
        proxyWasEnabled = false
        proxyServer = null
        proxyServerPromise = null
        throw e
    }
}

const stopProxy = () => {
    if (!stopPromise) stopPromise = stopProxyOnce()
    return stopPromise
}

const getCapturedUrl = async (timeoutMs = CAPTURE_TIMEOUT_MS) => {
    if (!proxyServerPromise) {
        throw new Error('Proxy server not started')
    }

    const reminderInterval = setInterval(() => {
        console.log('[proxy] Still waiting, ensuring the user has opened the wish history.')
    }, 15000)

    try {
        const url = await Promise.race([
            proxyServerPromise,
            new Promise((_, reject) =>
                setTimeout(
                    () => reject(proxyCaptureError(
                        EXIT.TIMEOUT,
                        'Timeout waiting for authkey capture. Issue could be the wish history not opened.',
                    )),
                    timeoutMs,
                ),
            ),
        ])


        return url
    } catch (e) {
        console.error('[proxy] Error getting captured URL. Error message: ', e.message)
        proxyServerPromise = null
        throw e
    } finally {
        clearInterval(reminderInterval)
    }
}

const captureAuthkeyViaProxy = async (port = PROXY_PORT) => {
    try {
        await startProxy(port)
        const capturedUrl = await getCapturedUrl()
        console.log('[proxy] Capture resolved; starting cleanup')
        await stopProxy()
        console.log('[proxy] Cleanup completed; returning captured URL')
        return capturedUrl
    } catch (e) {
        console.error('[proxy] Exception occurred in capturing authkey via the proxy server. Error message: ', e.message)
        await stopProxy()
        throw e
    }
}

const shutdown = async () => {
    if (shuttingDown) return
    shuttingDown = true
    try {
        await stopProxy()
    } catch (e) {
        console.error('[proxy] Error during cleanup process. Error message: ', e.message)
    }
    process.exit(EXIT.SUCCESS)
}

for (const signal of ['SIGINT', 'SIGTERM', 'SIGHUP', 'SIGBREAK']) process.on(signal, shutdown)

if (require.main === module && process.env.GENSHIN_PROXY_PARENT_PIPE === '1') {
    process.stdin.on('end', shutdown)
    process.stdin.on('error', shutdown)
    process.stdin.resume()
}

process.on('uncaughtException', async (err) => {
    console.error('[proxy] Uncaught exception. Error message:', err)
    try {
        await stopProxy()
    } catch (e) {
        console.error('[proxy] Error during cleanup process. Error message:', e.message)
    }
    process.exit(EXIT.FAILED)
})

module.exports = {
    captureAuthkeyViaProxy,
    startProxy,
    stopProxy,
    getCapturedUrl,
    fixAuthkey,
    notifyKotlin,
    EXIT,
    exitCodeForError,
}

if (require.main === module) {
    (async () => {
        try {
            console.log(`[proxy] Starting authkey capture on port ${PROXY_PORT}...`)
            const authkeyUrl = await captureAuthkeyViaProxy(PROXY_PORT)
            if (authkeyUrl == null) {
                console.log('[proxy] No authkey is captured.')
                process.exit(EXIT.FAILED)
            }
            console.log(`[proxy] Success! Captured URL (${authkeyUrl.length} chars); exiting with code 0`)
            process.exit(EXIT.SUCCESS)
        } catch (e) {
            console.error(`[proxy] Failed. Error message: ${e.message}`)
            process.exit(exitCodeForError(e))
        }
    })()
}
