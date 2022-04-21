/*
 * Copyright (c) 2022 Faiz & Siegeln Software GmbH
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
 *
 */

package com.im.njams.sdk.njams.util.mock;

import com.im.njams.sdk.NjamsFactory;
import com.im.njams.sdk.common.Path;
import com.im.njams.sdk.settings.Settings;

public class MockingNjamsFactory extends NjamsFactory {

    private final NjamsJobsMock njamsJobsMock;
    private final NjamsReceiverMock njamsReceiverMock;
    private final NjamsConfigurationMock njamsConfigurationMock;
    private final NjamsProjectMessageMock njamsProjectMessageMock;
    private final NjamsArgosMock njamsArgosMock;

    public MockingNjamsFactory() {
        super(new Path(), "SDK", new Settings());
        njamsJobsMock = new NjamsJobsMock();
        njamsReceiverMock = new NjamsReceiverMock();
        njamsConfigurationMock = new NjamsConfigurationMock();
        njamsProjectMessageMock = new NjamsProjectMessageMock();
        njamsArgosMock = new NjamsArgosMock();
    }

    @Override
    public NjamsJobsMock getNjamsJobs() {
        return njamsJobsMock;
    }

    @Override
    public NjamsReceiverMock getNjamsReceiver(){
        return njamsReceiverMock;
    }

    @Override
    public NjamsConfigurationMock getNjamsConfiguration(){
        return njamsConfigurationMock;
    }

    @Override
    public NjamsProjectMessageMock getNjamsProjectMessage(){
        return njamsProjectMessageMock;
    }

    @Override
    public NjamsArgosMock getNjamsArgos(){
        return njamsArgosMock;
    }

}
