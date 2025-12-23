package com.lancontrol.client.monitor;

import com.lancontrol.client.model.HeartbeatModel;
import com.lancontrol.client.model.ProcessInfo;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.GlobalMemory;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.hardware.NetworkIF;
import oshi.hardware.HWDiskStore;
import oshi.software.os.OSProcess;
import oshi.software.os.OperatingSystem;

import java.util.ArrayList;
import java.util.List;

public class SystemMonitor {
    private final SystemInfo si = new SystemInfo();
    private final HardwareAbstractionLayer hal = si.getHardware();
    private final OperatingSystem os = si.getOperatingSystem();
    private long prevDiskReadBytes = 0;
    private long prevDiskWriteBytes = 0;
    private long prevNetRecvBytes = 0;
    private long prevNetSentBytes = 0;
    private long prevTimeStamp = 0;
    private long[] prevCpuTicks;
    public SystemMonitor() {
        prevCpuTicks = hal.getProcessor().getSystemCpuLoadTicks();
        updateIoCounters();
        prevTimeStamp = System.currentTimeMillis();
    }

    public HeartbeatModel collect() {
        HeartbeatModel hb = new HeartbeatModel();

        double cpuLoad = hal.getProcessor().getSystemCpuLoadBetweenTicks(prevCpuTicks) * 100;
        prevCpuTicks = hal.getProcessor().getSystemCpuLoadTicks(); // Reset tick
        hb.setCpuUsage((short) cpuLoad);

        GlobalMemory memory = hal.getMemory();
        long totalRam = memory.getTotal();
        long availableRam = memory.getAvailable();
        double ramPercent = 100d * (totalRam - availableRam) / totalRam;
        hb.setRamUsage((short) ramPercent);

        var fileStores = os.getFileSystem().getFileStores();
        long totalSpace = 0;
        long freeSpace = 0;
        for (var store : fileStores) {
            totalSpace += store.getTotalSpace();
            freeSpace += store.getUsableSpace();
        }
        if (totalSpace > 0) {
            hb.setDiskFreePercent((short) (100d * freeSpace / totalSpace));
        }

        long currentTime = System.currentTimeMillis();
        long timeDelta = currentTime - prevTimeStamp;

        if (timeDelta < 1000) timeDelta = 1000;

        long currentDiskRead = 0;
        long currentDiskWrite = 0;
        for (HWDiskStore disk : hal.getDiskStores()) {
            currentDiskRead += disk.getReadBytes();
            currentDiskWrite += disk.getWriteBytes();
        }

        long currentNetRecv = 0;
        long currentNetSent = 0;
        for (NetworkIF net : hal.getNetworkIFs()) {
            net.updateAttributes(); // Cập nhật số liệu mới nhất
            currentNetRecv += net.getBytesRecv();
            currentNetSent += net.getBytesSent();
        }

        long seconds = timeDelta / 1000;

        hb.setDiskReadRateKb((int) ((currentDiskRead - prevDiskReadBytes) / 1024 / seconds));
        hb.setDiskWriteRateKb((int) ((currentDiskWrite - prevDiskWriteBytes) / 1024 / seconds));

        hb.setNetRecvRateKb((int) ((currentNetRecv - prevNetRecvBytes) / 1024 / seconds));
        hb.setNetSentRateKb((int) ((currentNetSent - prevNetSentBytes) / 1024 / seconds));

        prevDiskReadBytes = currentDiskRead;
        prevDiskWriteBytes = currentDiskWrite;
        prevNetRecvBytes = currentNetRecv;
        prevNetSentBytes = currentNetSent;
        prevTimeStamp = currentTime;

        return hb;
    }
    //cap nhat lai cac bo dem IO
    private void updateIoCounters() {
        for (HWDiskStore disk : hal.getDiskStores()) {
            prevDiskReadBytes += disk.getReadBytes();
            prevDiskWriteBytes += disk.getWriteBytes();
        }
        for (NetworkIF net : hal.getNetworkIFs()) {
            net.updateAttributes();
            prevNetRecvBytes += net.getBytesRecv();
            prevNetSentBytes += net.getBytesSent();
        }
    }
    // Lay top 20 tien trinh
    public List<ProcessInfo> getProcesses() {
        List<ProcessInfo> list = new ArrayList<>();
        List<OSProcess> procs = os.getProcesses(null, (p1, p2) -> Long.compare(p2.getResidentSetSize(), p1.getResidentSetSize()), 20); // Top 20 ăn RAM nhất

        for (OSProcess p : procs) {
            ProcessInfo pi = new ProcessInfo();
            pi.setPid(p.getProcessID());
            pi.setName(p.getName());
            pi.setMemoryUsageMb((double) p.getResidentSetSize() / (1024 * 1024));
            list.add(pi);
        }
        return list;
    }
}