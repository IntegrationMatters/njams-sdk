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
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 * LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO
 * EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR
 * THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.im.njams.sdk.feature;

import com.im.njams.sdk.api.feature.Feature;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class NjamsFeaturesTest {

    private static final String FEATURE1 = "Test1";

    private static final String FEATURE2 = "Test2";

    private NjamsFeatures features;

    private Feature featureMock1 = mock(Feature.class);

    private Feature featureMock2 = mock(Feature.class);

    @Before
    public void initialize(){
        features = spy(new NjamsFeatures());
        when(featureMock1.getFeature()).thenReturn(FEATURE1);
        when(featureMock2.getFeature()).thenReturn(FEATURE2);
    }

    @Test
    public void getFeaturesAfterInitializeNotNullButEmpty() {
        List<String> featureList = features.getFeatures();
        assertNotNull(featureList);
        assertTrue(featureList.isEmpty());
    }

    @Test
    public void addNullFeature(){
        List<String> featureList = features.getFeatures();
        features.addFeature(featureMock1);
        features.addFeature(null);
        assertTrue(featureList.size() == 1);
        assertEquals(featureList.get(0), featureMock1.getFeature());
    }

    @Test
    public void addFeatureAddsAFeatureToTheList() {
        List<String> featureList = features.getFeatures();
        features.addFeature(featureMock1);
        assertTrue(featureList.size() == 1);
        assertEquals(featureList.get(0), featureMock1.getFeature());
    }

    @Test
    public void addingAFeatureMultipleTimesWontChangeTheList(){
        List<String> featureList = features.getFeatures();
        features.addFeature(featureMock1);
        features.addFeature(featureMock1);
        assertTrue(featureList.size() == 1);
    }

    @Test
    public void addingMultipleFeatures(){
        List<String> featureList = features.getFeatures();
        features.addFeature(featureMock1);
        features.addFeature(featureMock2);
        assertTrue(featureList.size() == 2);
        assertEquals(featureList.get(0), featureMock1.getFeature());
        assertEquals(featureList.get(1), featureMock2.getFeature());
    }

    @Test(expected = UnsupportedOperationException.class)
    public void getFeaturesIsUnmodifiable(){
        List<String> featureList = features.getFeatures();
        assertTrue(featureList.isEmpty());
        featureList.add("SHOULDN'T BE ADDED");
    }

    @Test
    public void removingExistingFeature() {
        List<String> featureList = features.getFeatures();
        features.addFeature(featureMock1);
        features.addFeature(featureMock2);
        features.removeFeature(featureMock1);
        assertTrue(featureList.size() == 1);
        assertEquals(featureList.get(0), featureMock2.getFeature());
    }

    @Test
    public void removingNullFeature() {
        List<String> featureList = features.getFeatures();
        features.addFeature(featureMock1);
        features.addFeature(featureMock2);
        features.removeFeature(null);
        assertTrue(featureList.size() == 2);
        assertEquals(featureList.get(0), featureMock1.getFeature());
        assertEquals(featureList.get(1), featureMock2.getFeature());
    }

    @Test
    public void removingNotExistentFeature() {
        List<String> featureList = features.getFeatures();
        features.addFeature(featureMock1);
        features.removeFeature(featureMock2);
        assertTrue(featureList.size() == 1);
        assertEquals(featureList.get(0), featureMock1.getFeature());
    }
}