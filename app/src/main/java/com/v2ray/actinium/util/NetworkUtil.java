package com.v2ray.actinium.util;

import java.lang.reflect.Method;
import java.util.LinkedList;
import java.util.List;

public class NetworkUtil {
    public static List<String> getDnsServers() {
        try {
            Class<?> SystemProperties = Class.forName("android.os.SystemProperties");
            Method method = SystemProperties.getMethod("get", String.class);
            List<String> servers = new LinkedList<>();
            for (String name : new String[]{"net.dns1", "net.dns2", "net.dns3", "net.dns4",}) {
                String value = (String) method.invoke(null, name);
                if (value != null && !"".equals(value) && !servers.contains(value))
                    servers.add(value);
            }
            return servers;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}
