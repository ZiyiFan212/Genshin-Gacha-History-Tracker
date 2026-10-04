/** Configure or restore the Windows proxy for the current capture session. */
async function setSystemProxy(enable, port = 8080) {
    if (process.platform !== 'win32') {
        throw new Error('Platform unsupported for proxy service!')
    }
    const session = require('./windowsProxySession').windowsSession()
    const sessionId = process.env.GENSHIN_PROXY_SESSION
    if (enable) await session.enable(sessionId, port)
    else await session.restore(sessionId)
}

module.exports = {
    setSystemProxy
}
