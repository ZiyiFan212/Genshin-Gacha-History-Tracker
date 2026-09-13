const { exec } = require('child_process')
const { promisify } = require('util')

const execAsync = promisify(exec)
const platform = process.platform

/** @type {Record<string, string> | null} */
let savedGnomeSettings = null
let gnomeProxyWasEnabled = false

const IGNORE_HOSTS = "['localhost', '127.0.0.0/8', '10.0.0.0/8', '172.16.0.0/12', '192.168.0.0/16']"

async function commandExists(cmd) {
    try {
        await execAsync(`command -v ${cmd}`)
        return true
    } catch {
        return false
    }
}

async function isGnomeProxyAvailable() {
    if (!(await commandExists('gsettings'))) return false
    try {
        const { stdout } = await execAsync('gsettings list-schemas')
        return stdout.includes('org.gnome.system.proxy')
    } catch {
        return false
    }
}

async function gsettingsGet(schema, key) {
    const { stdout } = await execAsync(`gsettings get ${schema} ${key}`)
    return stdout.trim()
}

async function gsettingsSet(schema, key, value) {
    const escaped = value.replace(/'/g, "'\\''")
    await execAsync(`gsettings set ${schema} ${key} '${escaped}'`)
}

async function setLinuxGnomeProxy(enable, port) {
    if (enable) {
        savedGnomeSettings = {
            mode: await gsettingsGet('org.gnome.system.proxy', 'mode'),
            httpHost: await gsettingsGet('org.gnome.system.proxy.http', 'host'),
            httpPort: await gsettingsGet('org.gnome.system.proxy.http', 'port'),
            httpsHost: await gsettingsGet('org.gnome.system.proxy.https', 'host'),
            httpsPort: await gsettingsGet('org.gnome.system.proxy.https', 'port'),
            ignoreHosts: await gsettingsGet('org.gnome.system.proxy', 'ignore-hosts'),
        }
        gnomeProxyWasEnabled = savedGnomeSettings.mode === "'manual'"

        await gsettingsSet('org.gnome.system.proxy', 'mode', "'manual'")
        await gsettingsSet('org.gnome.system.proxy.http', 'host', "'127.0.0.1'")
        await gsettingsSet('org.gnome.system.proxy.http', 'port', String(port))
        await gsettingsSet('org.gnome.system.proxy.https', 'host', "'127.0.0.1'")
        await gsettingsSet('org.gnome.system.proxy.https', 'port', String(port))
        await gsettingsSet('org.gnome.system.proxy', 'ignore-hosts', IGNORE_HOSTS)
    } else if (savedGnomeSettings) {
        await gsettingsSet('org.gnome.system.proxy', 'mode', savedGnomeSettings.mode)
        await gsettingsSet('org.gnome.system.proxy.http', 'host', savedGnomeSettings.httpHost)
        await gsettingsSet('org.gnome.system.proxy.http', 'port', savedGnomeSettings.httpPort)
        await gsettingsSet('org.gnome.system.proxy.https', 'host', savedGnomeSettings.httpsHost)
        await gsettingsSet('org.gnome.system.proxy.https', 'port', savedGnomeSettings.httpsPort)
        await gsettingsSet('org.gnome.system.proxy', 'ignore-hosts', savedGnomeSettings.ignoreHosts)
        savedGnomeSettings = null
    } else {
        await gsettingsSet('org.gnome.system.proxy', 'mode', "'none'")
    }
}

/** @type {Record<string, any> | null} */
let savedWindowsSettings = null

async function setWindowsProxy(enable, port) {
    let Registry
    try {
        Registry = require('winreg')
    } catch (e) {
        if (enable) {
            throw new Error('MANUAL_PROXY_REQUIRED')
        } else {
            console.warn('[proxy] winreg not available, cannot disable proxy via registry')
            return
        }
    }
    
    const regKey = new Registry({
        hive: Registry.HKCU,
        key: '\\Software\\Microsoft\\Windows\\CurrentVersion\\Internet Settings',
    })

    const regGet = (key) =>
        new Promise((resolve) => {
            regKey.get(key, (err, result) => {
                resolve(err ? null : result?.value)
            })
        })

    const regSet = (key, type, value) =>
        new Promise((resolve, reject) => {
            regKey.set(key, type, value, (err) => (err ? reject(err) : resolve()))
        })

    if (enable) {
        savedWindowsSettings = {
            proxyEnable: await regGet('ProxyEnable'),
            proxyServer: await regGet('ProxyServer'),
            proxyOverride: await regGet('ProxyOverride'),
        }

        const proxyIp = `127.0.0.1:${port}`
        const ignoreIp =
            'localhost;127.*;10.*;172.16.*;172.17.*;172.18.*;172.19.*;172.20.*;172.21.*;172.22.*;172.23.*;172.24.*;172.25.*;172.26.*;172.27.*;172.28.*;172.29.*;172.30.*;172.31.*;192.168.*;<local>'

        await regSet('ProxyEnable', Registry.REG_DWORD, 1)
        await regSet('ProxyServer', Registry.REG_SZ, proxyIp)
        await regSet('ProxyOverride', Registry.REG_SZ, ignoreIp)
    } else {
        await regSet('ProxyEnable', Registry.REG_DWORD, 0)
        await regSet('ProxyServer', Registry.REG_SZ, '')
        await regSet('ProxyOverride', Registry.REG_SZ, '')
        savedWindowsSettings = null
    }
}

async function detectMacNetworkService() {
    const { stdout } = await execAsync('networksetup -listallnetworkservices')
    const services = stdout
        .split('\n')
        .map((s) => s.trim())
        .filter((s) => s && !s.startsWith('*'))
    return services.find((s) => /wi-?fi|ethernet/i.test(s)) || services[0]
}

/** @type {string | null} */
let macNetworkService = null

/** @type {Record<string, { host: string; port: string; enabled: boolean }> | null} */
let savedMacSettings = null

async function getMacProxyConfig(service, protocol) {
    try {
        const { stdout: host } = await execAsync(`networksetup -get${protocol}proxy "${service}"`)
        const hostMatch = host.match(/Server: (.+)/)
        const portMatch = host.match(/Port: (.+)/)
        const { stdout: enabled } = await execAsync(`networksetup -get${protocol}proxystate "${service}"`)
        return {
            host: hostMatch ? hostMatch[1].trim() : '',
            port: portMatch ? portMatch[1].trim() : '',
            enabled: enabled.trim() === 'On',
        }
    } catch {
        return { host: '', port: '', enabled: false }
    }
}

async function setMacProxy(enable, port) {
    if (!macNetworkService) {
        macNetworkService = await detectMacNetworkService()
    }
    if (!macNetworkService) {
        throw new Error('MANUAL_PROXY_REQUIRED')
    }

    if (enable) {
        savedMacSettings = {
            web: await getMacProxyConfig(macNetworkService, 'web'),
            secureweb: await getMacProxyConfig(macNetworkService, 'secureweb'),
        }

        await execAsync(`networksetup -setwebproxy "${macNetworkService}" 127.0.0.1 ${port}`)
        await execAsync(`networksetup -setsecurewebproxy "${macNetworkService}" 127.0.0.1 ${port}`)
        await execAsync(`networksetup -setwebproxystate "${macNetworkService}" on`)
        await execAsync(`networksetup -setsecurewebproxystate "${macNetworkService}" on`)
    } else if (savedMacSettings) {
        const { web, secureweb } = savedMacSettings
        await execAsync(`networksetup -setwebproxy "${macNetworkService}" ${web.host || 'off'} ${web.port || ''}`)
        await execAsync(`networksetup -setsecurewebproxy "${macNetworkService}" ${secureweb.host || 'off'} ${secureweb.port || ''}`)
        await execAsync(`networksetup -setwebproxystate "${macNetworkService}" ${web.enabled ? 'on' : 'off'}`)
        await execAsync(`networksetup -setsecurewebproxystate "${macNetworkService}" ${secureweb.enabled ? 'on' : 'off'}`)
        savedMacSettings = null
    } else {
        await execAsync(`networksetup -setwebproxystate "${macNetworkService}" off`)
        await execAsync(`networksetup -setsecurewebproxystate "${macNetworkService}" off`)
    }
}

/**
 * Configure system HTTP/HTTPS proxy.
 * Throws Error('MANUAL_PROXY_REQUIRED') when automatic setup is unavailable.
 */
async function setSystemProxy(enable, port = 8080) {
    if (platform === 'win32') {
        return setWindowsProxy(enable, port)
    }
    if (platform === 'darwin') {
        return setMacProxy(enable, port)
    }
    if (platform === 'linux') {
        if (await isGnomeProxyAvailable()) {
            return setLinuxGnomeProxy(enable, port)
        }
        throw new Error('MANUAL_PROXY_REQUIRED')
    }
    throw new Error(`Unsupported platform: ${platform}`)
}

function debugConsolePrint(port) {
    console.log('\n[proxy] Unable to automatically setup the proxy server, try manually：')
    console.log(`[proxy] HTTP/HTTPS proxy address: 127.0.0.1. Port ${port}`)
}

module.exports = {
    setSystemProxy,
    debugConsolePrint: debugConsolePrint,
}
