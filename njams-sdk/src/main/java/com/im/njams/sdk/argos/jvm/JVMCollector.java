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

package com.im.njams.sdk.argos.jvm;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.im.njams.sdk.argos.ArgosCollector;
import com.im.njams.sdk.argos.ArgosComponent;

/**
 * This is a concrete implementation for collecting JMV statistic metrics.
 * <p>
 * It uses also @see {@link GCStats} and @see {@link OsProcessStats} classes.
 */
public class JVMCollector extends ArgosCollector<JVMMetric> {
    private static final Logger LOG = LoggerFactory.getLogger(JVMCollector.class);

    /**
     * The name of this measurement. It is used in @see {@link ArgosComponent}
     */
    public static final String MEASUREMENT = "jvm";

    private OsProcessStats processStats = null;

    private static int getPid() {
        if (ManagementFactory.getRuntimeMXBean().getName().contains("@")) {
            try {
                //Try to get the JVM id
                return Integer.valueOf(ManagementFactory.getRuntimeMXBean().getName().split("@")[0]);
            } catch (Exception e) {
                LOG.warn("Could not get the JVM process id (PID).", e);
            }
        }
        return 0;
    }

    private static String getLocalHost() {
        try {
            InetAddress localMachine = InetAddress.getLocalHost();
            return localMachine.getCanonicalHostName();
        } catch (Exception e) {
            LOG.warn("Could not get local host. Using localhost as default.", e);
            return "localhost";
        }
    }

    private static ArgosComponent createDefaultJVMComponent(String id, String name, String type) {
        return new ArgosComponent(id, name, getLocalHost(), MEASUREMENT, type);
    }

    public JVMCollector(String id, String name, String type) {
        this(createDefaultJVMComponent(id, name, type), getPid());
    }

    public JVMCollector(ArgosComponent argosComponent, int pid) {
        super(argosComponent);
        if (pid > 0) {
            try {
                processStats = new OsProcessStats(pid).collectProcessStats();
            } catch (Throwable e) {
                //Do nothing
            }
        }
    }

    @Override
    protected JVMMetric create(ArgosComponent argosComponent) {
        JVMMetric jvmStats = new JVMMetric(argosComponent);

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
        if (processStats != null) {
            processStats.collectProcessStats();
        }
        jvmStats.setProcessStats(processStats);

        return jvmStats;
    }
}
