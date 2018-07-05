/* 
 * Copyright (c) 2018 Faiz & Siegeln Software GmbH
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"),
 * to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 * 
 * The Software shall be used for Good, not Evil.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS
 * IN THE SOFTWARE.
 */
package com.im.njams.sdk.model;

import com.im.njams.sdk.common.Path;

/**
 * This activity represents a SubProcess call in a Process. It can handle
 * spawned or inline processes.
 *
 * @author pnientiedt
 */
public class SubProcessActivityModel extends ActivityModel {

    private ProcessModel subProcess;
    private String subProcessName;
    private Path subProcessPath;

    /**
     * Create a new SubProcessActivityModel
     *
     * @param processModel ProcessModel which should contain this
     * SubProcessActivityModel
     */
    public SubProcessActivityModel(ProcessModel processModel) {
        super(processModel);
    }

    /**
     *
     * @param processModel ProcessModel which should contain this
     * SubProcessActivityModel
     * @param subProcess ProcessModel of the called SubProcess
     */
    public SubProcessActivityModel(ProcessModel processModel, ProcessModel subProcess) {
        super(processModel);
        this.subProcess = subProcess;
    }

    /**
     *
     * @param processModel ProcessModel which should contain this
     * SubProcessActivityModel
     * @param id Id of this Activity
     * @param name Name of this Activity
     * @param type Type of this Activity
     */
    public SubProcessActivityModel(ProcessModel processModel, String id, String name, String type) {
        super(processModel, id, name, type);
    }

    /**
     * @return the subProcess
     */
    public ProcessModel getSubProcess() {
        return subProcess;
    }

    /**
     * @param subProcess the subProcess to set
     */
    public void setSubProcess(ProcessModel subProcess) {
        this.subProcess = subProcess;
    }

    /**
     * @param subProcessName name of subprocess
     * @param subProcessPath name of path
     */
    public void setSubProcess(String subProcessName, Path subProcessPath) {
        this.subProcessName = subProcessName;
        this.subProcessPath = subProcessPath;
    }

    /**
     *
     * @return name of the SubProcess
     */
    public String getSubProcessName() {
        if (subProcess != null) {
            return subProcess.getName();
        }
        return subProcessName;
    }

    /**
     *
     * @return path of the SubProcess
     */
    public Path getSubProcessPath() {
        if (subProcess != null) {
            return subProcess.getPath();
        }
        return subProcessPath;
    }

}
