package com.lancontrol.client.util;

import oshi.SystemInfo;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.hardware.NetworkIF;

import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;
import java.util.List;

public class HardwareUtil {
    private static final SystemInfo si = new SystemInfo();
    private static final HardwareAbstractionLayer hal = si.getHardware();

    public static String getLocalIpAddress() {
        String bestIp = "127.0.0.1";
        int maxScore = 0;

        try {
            // ket noi den mot dia chi ngoai de lay ip dia phuong
            try (final DatagramSocket socket = new DatagramSocket()) {
                socket.connect(InetAddress.getByName("8.8.8.8"), 10002);
                String ip = socket.getLocalAddress().getHostAddress();
                if (ip != null && !ip.startsWith("127.")) return ip;
            } catch (Exception e) {}

            // Duyet tat ca cac NetworkInterface
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                if (!iface.isUp() || iface.isLoopback()) continue;
                if (iface.getDisplayName().toLowerCase().contains("docker") ||
                        iface.getDisplayName().toLowerCase().contains("virtual")) continue;

                Enumeration<InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    String ip = addr.getHostAddress();
                    if (ip.contains(":")) continue;

                    int score = 0;
                    if (ip.startsWith("192.168.")) score = 10;
                    else if (ip.startsWith("10.")) score = 9;
                    else if (!ip.startsWith("127.")) score = 5;

                    if (score > maxScore) {
                        maxScore = score;
                        bestIp = ip;
                    }
                }
            }
        } catch (Exception e) {}
        return bestIp;
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