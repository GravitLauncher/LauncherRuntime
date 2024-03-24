package pro.gravit.launcher.gui.utils;

import com.sun.management.OperatingSystemMXBean;

public class SystemMemory {
    private static final OperatingSystemMXBean systemMXBean = (OperatingSystemMXBean) java.lang.management.ManagementFactory.getOperatingSystemMXBean();
    public static long getPhysicalMemorySize() {
        return systemMXBean.getTotalMemorySize();
    }
}
