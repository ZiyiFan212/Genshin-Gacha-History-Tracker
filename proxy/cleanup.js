const { execSync } = require('child_process');
const os = require('os');

const PROXY_PORT = process.argv[2] || 8080;

function runCommand(command) {
    try {
        return execSync(command, { stdio: 'pipe' }).toString().trim();
    } catch (error) {
        return "";
    }
}

function getPidsByPort(port) {
    const platform = os.platform();
    const pids = new Set();

    try {
        if (platform === 'win32') {
            const output = runCommand(`netstat -ano | findstr :${port}`);
            if (output) {
                const lines = output.split('\n');
                for (const line of lines) {
                    if (line.includes(`:${port}`) && line.includes('LISTENING')) {
                        const parts = line.trim().split(/\s+/);
                        const potentialPid = parts[parts.length - 1];
                        if (!isNaN(potentialPid)) {
                            pids.add(potentialPid);
                        }
                    }
                }
            }
        } else {
            const output = runCommand(`lsof -ti :${port}`);
            if (output) {
                output.split('\n').forEach(pid => {
                    if (pid.trim()) {
                        pids.add(pid.trim());
                    }
                });
            }
        }
    } catch (e) {
        // Ignore errors if no process is found
    }

    return Array.from(pids);
}

function killProcess(pid) {
    if (!pid) return;

    const platform = os.platform();

    try {
        if (platform === 'win32') {
            runCommand(`taskkill /F /PID ${pid}`);
        } else {
            runCommand(`kill -9 ${pid}`);
        }
    } catch (error) {
        // Ignore errors if process is already dead or permission denied
    }
}

function disableSystemProxy() {
    const platform = os.platform();

    if (platform === 'win32') {
        try {
            runCommand(`reg add "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Internet Settings" /v ProxyEnable /t REG_DWORD /d 0 /f`);
        } catch (e) {
            // Ignore registry errors
        }
    }
    else if (platform === 'darwin') {
        let allServices = [];
        try {
            const listOutput = runCommand(`networksetup -listallnetworkservices`);
            allServices = listOutput.split('\n')
                .map(s => s.replace(/^\*\s*/, '').trim())
                .filter(s => s.length > 0 && s !== 'An asterisk (*) denotes that a network service is disabled.');
        } catch (e) {
            allServices = ['Wi-Fi', 'Ethernet', 'Thunderbolt Ethernet', 'USB LAN'];
        }

        allServices.forEach(service => {
            try {
                runCommand(`networksetup -setwebproxystate "${service}" off`);
                runCommand(`networksetup -setsecurewebproxystate "${service}" off`);
                runCommand(`networksetup -setsocksfirewallproxystate "${service}" off`);
            } catch (e) {
                // Ignore errors for non-existent services
            }
        });
    }
    else if (platform === 'linux') {
        try {
            runCommand(`gsettings set org.gnome.system.proxy mode 'none'`);
        } catch (e) {
            // Ignore if not GNOME or gsettings unavailable
        }
    }
}

async function main() {
    disableSystemProxy();

    await new Promise(resolve => setTimeout(resolve, 500));

    const pids = getPidsByPort(PROXY_PORT);
    if (pids.length > 0) {
        console.log(`[cleanup] Found ${pids.length} process(es) using port ${PROXY_PORT}: ${pids.join(', ')}`);
        pids.forEach(pid => {
            killProcess(pid);
            console.log(`[cleanup] Killed process ${pid}`);
        });
    }

    process.exit(0);
}

main().catch(err => {
    console.error('[cleanup] Error:', err.message);
    process.exit(1);
});