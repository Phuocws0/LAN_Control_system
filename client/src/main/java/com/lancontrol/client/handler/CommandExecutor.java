package com.lancontrol.client.handler;
public class CommandExecutor {
    public void shutdown() {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            exec("shutdown -s -t 5");
        } else {
            exec("shutdown -h now");
        }
    }    public void restart() { exec("shutdown -r -t 5"); }
    private void exec(String cmd) {
        try { Runtime.getRuntime().exec(cmd); } catch(Exception e){}
    }
    public void sleep() {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            // Lệnh Sleep trên Windows (yêu cầu quyền hoặc PowerShell)
            exec("powershell.exe -Command \"Add-Type -AssemblyName System.Windows.Forms; [System.Windows.Forms.Application]::SetSuspendState([System.Windows.Forms.PowerState]::Suspend, $false, $false)\"");
        } else if (os.contains("mac")) {
            exec("pmset sleepnow");
        } else {
            exec("systemctl suspend"); // Lệnh Sleep cho Linux
        }
    }
    public boolean killProcess(int pid) {
        try {
            Process p;
            if (System.getProperty("os.name").toLowerCase().contains("win")) {
                // /F là ép buộc, /PID là theo ID
                p = Runtime.getRuntime().exec("taskkill /F /PID " + pid);
            } else {
                p = Runtime.getRuntime().exec("kill -9 " + pid);
            }

            // Đợi lệnh thực thi xong và lấy mã thoát
            int exitCode = p.waitFor();
            return exitCode == 0;
        } catch (Exception e) {
            return false;
        }
    }
}