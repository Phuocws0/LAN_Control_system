package com.lancontrol.client.handler;
public class CommandExecutor {
    public void shutdown() { exec("shutdown -s -t 5"); }
    public void restart() { exec("shutdown -r -t 5"); }
    private void exec(String cmd) {
        try { Runtime.getRuntime().exec(cmd); } catch(Exception e){}
    }
    public void killProcess(int pid) {
        System.out.println("!!! ĐANG THỰC HIỆN KILL PROCESS PID: " + pid + " !!!");
        String os = System.getProperty("os.name").toLowerCase();
        String cmd;

        if (os.contains("win")) {
            cmd = "taskkill /F /PID " + pid;
        } else {
            cmd = "kill -9 " + pid;
        }
        exec(cmd);
    }
}