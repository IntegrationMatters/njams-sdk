/*
 * Copyright (c) 2019 Faiz & Siegeln Software GmbH
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and
 * to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 * the Software.
 *
 * The Software shall be used for Good, not Evil.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO
 * THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF
 * CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS
 * IN THE SOFTWARE.
 */

package com.im.njams.sdk.subagent.jvm;

import com.im.njams.sdk.subagent.ArgosComponent;
import com.im.njams.sdk.subagent.ArgosCollector;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;

public class JVMCollector extends ArgosCollector<JVMStatistics> {

    public static final String MEASUREMENT = "jvm";

    private int pid = 0;

    public JVMCollector(String id, String name, String type){
        this(id, name, type, getPid());
    }

    private static int getPid(){
        if (ManagementFactory.getRuntimeMXBean().getName().contains("@")) {
            try {
                //Try to get the JVM id
                return Integer.valueOf(ManagementFactory.getRuntimeMXBean().getName().split("@")[0]);
            } catch (Exception e) {
                // ignore
            }
        }
        return 0;
    }

    public JVMCollector(String id, String name, String type, int pid){
        this(createDefaultJVMComponent(id, name, type), pid);
    }

    private static ArgosComponent createDefaultJVMComponent(String id, String name, String type){
        return new ArgosComponent(id, name, getHost(), MEASUREMENT, type);
    }

    public JVMCollector(ArgosComponent argosComponent, int pid) {
        super(argosComponent);
        this.pid = pid;
    }

    private static String getHost(){
        try {
            InetAddress localMachine = InetAddress.getLocalHost();
            return localMachine.getCanonicalHostName();
        } catch (Exception ex) {
            return "localhost";
        }
    }

    @Override
    protected JVMStatistics create() {
        JVMStatistics jvmStats = new JVMStatistics();

        MemoryMXBean memoryMxBean = ManagementFactory.getMemoryMXBean();
        MemoryUsage mu = memoryMxBean.getHeapMemoryUsage();

        jvmStats.setThreadCount(ManagementFactory.getThreadMXBean().getThreadCount());
        jvmStats.setHeapCommitted(mu.getCommitted());
        jvmStats.setHeapInit(mu.getInit());
        jvmStats.setHeapMax(mu.getMax());
        jvmStats.setHeapUsed(mu.getUsed());
        jvmStats.setHeapFree(jvmStats.getHeapCommitted() - jvmStats.getHeapUsed());

        mu = memoryMxBean.getNonHeapMemoryUsage();
        jvmStats.setOffCommitted(mu.getCommitted());
        jvmStats.setOffInit(mu.getInit());
        jvmStats.setOffMax(mu.getMax());
        jvmStats.setOffUsed(mu.getUsed());
        jvmStats.setOffFree(jvmStats.getOffCommitted() - jvmStats.getOffUsed());

        Map<String, GCStats> gc = new HashMap<>();
        for (GarbageCollectorMXBean bean : ManagementFactory.getGarbageCollectorMXBeans()) {
            GCStats gcStats = new GCStats();
            gc.put(bean.getName().replace(" ", "_"), gcStats.collectStatistics(bean));
        }
        jvmStats.setGc(gc);
        OsProcessStats processStats = null;
        if (pid > 0) {
            try {
                processStats = new OsProcessStats(pid).collectProcessStats();
            } catch (Throwable e) {
                //Do nothing
            }
        }
        jvmStats.setProcessStats(processStats);
        return jvmStats;
    }
}
