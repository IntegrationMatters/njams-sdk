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

import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.software.os.OSProcess;
import oshi.software.os.OperatingSystem;

/**
 * This class collects operating system process statistic metrics.
 * <p>
 * It is used in @see {@link JVMMetric}
 */
public class OsProcessStats {
    private OSProcess process;
    private long previousTimestamp = -1, previousCpuTime = -1, previousKernelTime = -1, previousUserTime = -1;

    private int pid;
    private SystemInfo systemInfo;
    private OperatingSystem operatingSystem;
    private CentralProcessor centralProcessor;
    private int cpuNumber = 0;

    private double cpuUsage = 0;
    private double kernelUsage = 0;
    private double userUsage = 0;
    private long openFiles = 0;
    private OSProcess.State state;

    public OsProcessStats(int pid) {
        this.pid = pid;
        this.systemInfo = new SystemInfo();
        this.operatingSystem = systemInfo.getOperatingSystem();
        this.centralProcessor = systemInfo.getHardware().getProcessor();
        this.cpuNumber = centralProcessor.getLogicalProcessorCount();
    }

    public double getCpuUsage() {
        return cpuUsage;
    }

    public void setCpuUsage(double cpuUsage) {
        this.cpuUsage = cpuUsage;
    }

    public long getOpenFiles() {
        return openFiles;
    }

    public void setOpenFiles(long openFiles) {
        this.openFiles = openFiles;
    }

    public OSProcess.State getState() {
        return state;
    }

    public void setState(OSProcess.State state) {
        this.state = state;
    }

    public double getKernelUsage() {
        return kernelUsage;
    }

    public void setKernelUsage(double kernelUsage) {
        this.kernelUsage = kernelUsage;
    }

    public double getUserUsage() {
        return userUsage;
    }

    public void setUserUsage(double userUsage) {
        this.userUsage = userUsage;
    }

    public OsProcessStats collectProcessStats() {
        if (systemInfo == null) {
            return null;
        }
        process = operatingSystem.getProcess(pid);
        if (process != null) {
            long ts = System.currentTimeMillis();
            long tsDiff = ts - previousTimestamp;
            this.previousTimestamp = ts;

            // CPU kernel
            long kernelTime = process.getKernelTime();
            if (previousKernelTime != -1) {
                long kernelTimeDiff = kernelTime - previousKernelTime;
                kernelUsage = (100d * (kernelTimeDiff / ((double) tsDiff))) / cpuNumber;
            }
            this.previousKernelTime = kernelTime;

            // CPU user
            long userTime = process.getUserTime();
            if (previousUserTime != -1) {
                long userTimeDiff = userTime - previousUserTime;
                userUsage = (100d * (userTimeDiff / ((double) tsDiff))) / cpuNumber;
            }
            this.previousUserTime = userTime;

            // CPU total
            long currentCpuTime = kernelTime + userTime;
            if (previousCpuTime != -1) {
                long timeDifference = currentCpuTime - previousCpuTime;
                cpuUsage = (100d * (timeDifference / ((double) tsDiff))) / cpuNumber;
            }
            this.previousCpuTime = currentCpuTime;

            this.state = process.getState();
            this.openFiles = process.getOpenFiles();
            return this;
        }
        return null;
    }
}
