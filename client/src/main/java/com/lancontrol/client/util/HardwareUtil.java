package com.lancontrol.client.util;

import oshi.SystemInfo;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.hardware.NetworkIF;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;
import java.util.List;

public class HardwareUtil {
    private static final SystemInfo si = new SystemInfo();
    private static final HardwareAbstractionLayer hal = si.getHardware();

    public static String getLocalIpAddress() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                if (iface.isLoopback() || !iface.isUp()) continue;
                Enumeration<InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (addr.isSiteLocalAddress()) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "Unknown-IP";
    }

    public static String getMacAddress() {
        try {
            List<NetworkIF> networkIFs = hal.getNetworkIFs();
            for (NetworkIF net : networkIFs) {
                if (net.getIPv4addr().length > 0 && !net.getMacaddr().isEmpty()) {
                    return net.getMacaddr();
                }
            }
        } catch (Exception e) {}
        return "00-00-00-00-00-00";
    }

    public static long getTotalRam() {
        return hal.getMemory().getTotal();
    }

    public static int getTotalDiskGb() {
        long totalBytes = 0;
        for (var store : hal.getDiskStores()) {
            totalBytes += store.getSize();
        }
        return (int) (totalBytes / (1024 * 1024 * 1024)); // Convert sang GB
    }

    public static String getCpuName() {
        return hal.getProcessor().getProcessorIdentifier().getName();
    }
    public static String getHostName() {
        try {
            return java.net.InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            String host = System.getenv("COMPUTERNAME"); // Windows
            if (host == null) {
                host = System.getenv("HOSTNAME"); // Linux/Mac
            }
            return host != null ? host : "Client-Unknown";
        }
    }
}