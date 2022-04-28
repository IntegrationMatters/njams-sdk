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
 */
package com.im.njams.sdk.logmessage;

import com.faizsiegeln.njams.messageformat.v4.projectmessage.AttributeType;
import com.faizsiegeln.njams.messageformat.v4.projectmessage.Extract;
import com.faizsiegeln.njams.messageformat.v4.projectmessage.ExtractRule;
import com.faizsiegeln.njams.messageformat.v4.projectmessage.RuleType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.im.njams.sdk.njams.NjamsSerializers;
import com.im.njams.sdk.configuration.ActivityConfiguration;
import com.im.njams.sdk.model.ActivityModel;
import com.im.njams.sdk.utils.StringUtils;
import io.burt.jmespath.Expression;
import io.burt.jmespath.JmesPath;
import io.burt.jmespath.jackson.JacksonRuntime;
import net.sf.saxon.xpath.XPathEvaluator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.transform.sax.SAXSource;
import javax.xml.xpath.XPathConstants;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ExtractHandler is used for handling the extracts that can be set from the server.
 */
public class ExtractHandler {

    private static final Logger LOG = LoggerFactory.getLogger(ExtractHandler.class);
    private static final Map<String, Matcher> MATCHER = new ConcurrentHashMap<>();

    /**
     * Enumeration for specifying on what data an extract is to be applied.
     */
    public enum ExtractSource {
        /**
         * The extract applies to the activity's input data.
         */
        INPUT("in"),
        /**
         * The extract applies to the activity's output data.
         */
        OUTPUT("out");

        /**
         * The direction indicator used by the extract configuration.
         */
        public final String direction;

        private ExtractSource(String key) {
            direction = key;
        }

        public static ExtractSource byDirection(String direction) {
            if ("in".equalsIgnoreCase(direction)) {
                return INPUT;
            }
            if ("out".equalsIgnoreCase(direction)) {
                return OUTPUT;
            }
            throw new IllegalArgumentException("No enum constant for direction: " + direction);
        }
    }

    private ExtractHandler() {
        //utility
    }

    /**
     * Handle extract. Input is the sourceData object and the string data, which
     * is only filled if a tracepoint was already evaluated. If not, the
     * sourceData has to be serialized via Njams to get extractData.
     *
     * @param job             The job in which the extract is to be handled
     * @param activity        The activity instance on that the extract is to be applied.
     * @param sourceDirection Whether the extract applies to the activity's input or output data.
     * @param sourceData      The data object on that the extract is being evaluated.
     * @param data            The serialized data object on that the extract is being evaluated.
     * @param serializers     Serializers to serialize the message with
     */
    public static void handleExtract(JobImpl job, ActivityImpl activity, ExtractSource sourceDirection,
                                     Object sourceData, String data, NjamsSerializers serializers) {
        ActivityConfiguration config = null;
        ActivityModel model = activity.getActivityModel();
        config = job.getActivityConfiguration(model);

        if (config == null) {
            return;
        }
        handleExtract(job, config.getExtract(), activity, sourceDirection, sourceData, data, serializers);
    }

    /**
     * Handle extract. Input is the sourceData object and the string data, which
     * is only filled if a tracepoint was already evaluated. If not, the
     * sourceData has to be serialized via Njams to get extractData.
     *
     * @param job             The job in which the extract is to be handled
     * @param extract         The extract that is to be applied.
     * @param activity        The activity instance on that the extract is to be applied.
     * @param sourceDirection Whether the extract applies to the activity's input or output data.
     * @param sourceData      The data object on that the extract is being evaluated.
     * @param data            The serialized data object on that the extract is being evaluated.
     * @param serializers     Serializers to serialize the message with
     */
    public static void handleExtract(JobImpl job, Extract extract, ActivityImpl activity,
                                     ExtractSource sourceDirection, Object sourceData, String data, NjamsSerializers serializers) {
        if (extract == null) {
            return;
        }
        Iterator<ExtractRule> erl = extract.getExtractRules().iterator();

        while (erl.hasNext()) {
            ExtractRule er = erl.next();

            if (er.getRuleType() == RuleType.DISABLED || !er.getInout().equalsIgnoreCase(sourceDirection.direction)) {
                continue;
            }
            if (LOG.isDebugEnabled()) {
                LOG.debug("nJAMS: after execution - rule: " + er.getRule());
            }

            switch (er.getRuleType()) {
                case REGEXP:
                    doRegexp(job, activity, er, sourceData, data, serializers);
                    break;
                case EVENT:
                    doEvent(job, activity, er);
                    break;
                case VALUE:
                    doValue(job, activity, er);
                    break;
                case XPATH:
                    doXpath(job, activity, er, sourceData, data, serializers);
                    break;
                case JMESPATH:
                    doJmespath(job, activity, er, sourceData, data, serializers);
                    break;
                default:
                    break;
            }
        }
    }

    private static void
    doRegexp(JobImpl job, ActivityImpl activity, ExtractRule er, Object sourceData, String paramData, NjamsSerializers serializers) {
        String data = paramData;
        if (paramData == null || paramData.length() == 0) {
            data = serializers.serialize(sourceData);
            if (data == null || data.length() == 0) {
                return;
            }
        }
        String setting = er.getAttribute();

        LOG.debug("nJAMS: regex extract for setting: {}", setting);

        Matcher localMatcher = getExtractMatcher(er.getRule());
        if (localMatcher == null) {
            return;
        }

        if (localMatcher.reset(data).find()) {
            int group = localMatcher.groupCount() > 0 ? 1 : 0;
            String value = localMatcher.group(group);

            LOG.debug("nJAMS: regex result: {}", value);

            if (er.getAttributeType() == AttributeType.EVENT) {
                activity.setEventStatus(getEventStatus(value));
            } else {
                setAttributes(job, activity, setting, value);
            }
        }
        job.setInstrumented();
    }

    public static String testExpression(RuleType type, String expression, String testData) throws Exception {
        if (type == null) {
            throw new IllegalArgumentException("Missing rule type.");
        }
        switch (type) {
            case REGEXP:
                return testRegexp(expression, testData);
            case VALUE:
                return expression;
            case XPATH:
                return applyXpath(expression, testData);
            case JMESPATH:
                return applyJmespath(expression, testData);
            case DISABLED:
            case EVENT:
            default:
                return null;
        }
    }

    private static String applyJmespath(String expression, String data) throws Exception {
        if (StringUtils.isBlank(expression) || StringUtils.isBlank(data)) {
            return null;
        }
        String strResult = null;
        JsonNode result = null;
        try {
            final ObjectMapper mapper = com.im.njams.sdk.common.JsonSerializerFactory.getDefaultMapper();
            final JmesPath<JsonNode> jmespath = new JacksonRuntime();
            final Expression<JsonNode> jmesexpression = jmespath.compile(expression);
            final JsonNode input = mapper.readTree(data);
            result = jmesexpression.search(input);
            if (result != null) {
                // remove surrounding quotes
                strResult = result.toString().replaceAll("^\"|\"$", "");
            }
            if (LOG.isTraceEnabled()) {
                LOG.trace("Executed JMESPath query: {}\non:\n{}\nResult:\n{}", expression, data,
                    mapper.writeValueAsString(result));
            }
        } catch (final IOException e) {
            LOG.error("Unable to get jmespath " + expression + " from " + data, e);
        }

        return strResult;
    }

    private static String applyXpath(String xpath, String data) throws Exception {
        if (StringUtils.isBlank(xpath) || StringUtils.isBlank(data)) {
            return null;
        }
        XpathContext xpathContext = new XpathContext();
        if (xpathContext.getXpf() == null) {
            xpathContext.setXpf(new net.sf.saxon.xpath.XPathFactoryImpl());
            xpathContext.setXpath(xpathContext.getXpf().newXPath());
            xpathContext.getXpath().setNamespaceContext(new NamespaceResolver(data, false));
            xpathContext.setSs(new SAXSource(new InputSource(new StringReader(data))));
            xpathContext.setDoc(((XPathEvaluator) xpathContext.getXpath()).setSource(xpathContext.getSs()));
        }
        xpathContext.setExpr(xpathContext.getXpath().compile(xpath));
        Object result = xpathContext.getExpr().evaluate(xpathContext.getDoc(), XPathConstants.NODESET);
        List nodes = null;
        if (result instanceof List) {
            nodes = (List) result;
        } else if (result instanceof NodeList) {
            final int len = ((NodeList) result).getLength();
            nodes = new ArrayList<>(len);
            for (int i = 0; i < len; i++) {
                String val = ((NodeList) result).item(i).getNodeValue();
                nodes.add(val);
            }
        } else {
            LOG.error("Unknown class " + result.getClass() + " returned from XPath evaluator");
        }
        String strResult = "";
        if (nodes != null) {
            for (Object node : nodes) {
                Object o = node;
                if (o instanceof net.sf.saxon.tinytree.TinyNodeImpl) {
                    if (!(o instanceof net.sf.saxon.tinytree.WhitespaceTextImpl)) {
                        strResult += ((net.sf.saxon.tinytree.TinyNodeImpl) o).getStringValue();
                    }
                } else {
                    strResult += node;
                }
            }
        }
        return strResult;
    }

    private static String testRegexp(String regex, String data) {
        if (StringUtils.isBlank(regex) || StringUtils.isBlank(data)) {
            return null;
        }
        final Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(data);
        if (matcher.find()) {
            int group = matcher.groupCount() > 0 ? 1 : 0;
            String value = matcher.group(group);
            LOG.debug("nJAMS: regex result: {}", value);
            return value;
        }
        return null;
    }

    private static void doEvent(JobImpl job, ActivityImpl activity, ExtractRule er) {
        String evt = er.getRule();
        if (evt != null && evt.length() > 0) {
            activity.setEventStatus(getEventStatus(evt));
            job.setInstrumented();
        }
    }

    private static void doValue(JobImpl job, ActivityImpl activity, ExtractRule er) {
        if (er.getAttributeType() == AttributeType.EVENT) {
            activity.setEventStatus(getEventStatus(er.getRule()));
        } else {
            setAttributes(job, activity, er.getAttribute(), er.getRule());
        }
        job.setInstrumented();
    }

    private static void doJmespath(JobImpl job, ActivityImpl activity, ExtractRule er, Object sourceData,
                                   String paramData, NjamsSerializers serializers) {
        String data = paramData;
        if (paramData == null || paramData.length() == 0) {
            data = serializers.serialize(sourceData);
            if (data == null || data.length() == 0) {
                return;
            }
        }
        try {
            String strResult = applyJmespath(er.getRule(), data);
            if (er.getAttributeType() == AttributeType.EVENT) {
                LOG.debug("nJAMS: jmespath extract for setting: {}", er.getAttribute());
                LOG.debug("nJAMS: jmespath result: {}", strResult);
                activity.setEventStatus(getEventStatus(strResult));
            } else {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("nJAMS: jmespath extract for setting: {}", er.getAttribute());
                    LOG.debug("nJAMS: jmespath result: {}", strResult);
                }
                setAttributes(job, activity, er.getAttribute(), strResult);
            }
            job.setInstrumented();
        } catch (Exception e) {
            LOG.error("Failed to evaluate jmespath for setting: {} and rule: {}.", er.getAttribute(), er.getRule(), e);
        }
    }

    private static void doXpath(JobImpl job, ActivityImpl activity, ExtractRule er, Object sourceData,
                                String paramData, NjamsSerializers serializers) {
        String data = paramData;
        if (paramData == null || paramData.length() == 0) {
            data = serializers.serialize(sourceData);
            if (data == null || data.length() == 0) {
                return;
            }
        }
        try {
            String strResult = applyXpath(er.getRule(), data);
            if (er.getAttributeType() == AttributeType.EVENT) {
                LOG.debug("nJAMS: xpath extract for setting: {}", er.getAttribute());
                LOG.debug("nJAMS: xpath result: {}", strResult);
                activity.setEventStatus(getEventStatus(strResult));
            } else {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("nJAMS: xpath extract for setting: {}", er.getAttribute());
                    LOG.debug("nJAMS: xpath result: {}", strResult);
                }
                setAttributes(job, activity, er.getAttribute(), strResult);
            }
            job.setInstrumented();
        } catch (Exception e) {
            LOG.error("Failed to evaluate xpath for setting: {} and rule: {}.", er.getAttribute(), er.getRule(), e);
        }
    }

    private static Matcher getExtractMatcher(String rule) {
        Matcher m = MATCHER.get(rule);
        if (m == null) {
            try {
                Pattern pattern = Pattern.compile(rule);
                m = pattern.matcher("");
                MATCHER.put(rule, m);
            } catch (Exception e) {
                LOG.warn("Could not compile pattern: {}", rule);
                LOG.debug("Error in getExtractMatcher", e);
            }
        }
        return m;
    }

    private static void setAttributes(Job job, ActivityImpl activity, String setting, String uncheckedvalue) {
        String value = DataMasking.getGlobalNjamsDataMasking().mask(uncheckedvalue);
        LOG.debug("nJAMS: setAttributes: {}/{}", setting, value);

        String settingLowerCase = setting.toLowerCase();
        switch (settingLowerCase) {
            case "correlationlogid":
                job.setCorrelationLogId(value);
                break;
            case "parentlogid":
                job.setParentLogId(value);
                break;
            case "externallogid":
                job.setExternalLogId(value);
                break;
            case "businessservice":
                job.setBusinessService(value);
                break;
            case "businessobject":
                job.setBusinessObject(value);
                break;
            case "eventmessage":
                activity.setEventMessage(value);
                break;
            case "eventcode":
                activity.setEventCode(value);
                break;
            case "payload":
                activity.setEventPayload(value);
                break;
            case "stacktrace":
                activity.setStackTrace(value);
                break;
            default:
                activity.addAttribute(setting, value);
        }
    }

    private static int getEventStatus(String status) {
        if ("success".equalsIgnoreCase(status)) {
            return EventStatus.SUCCESS.getValue();
        } else if ("warning".equalsIgnoreCase(status)) {
            return EventStatus.WARNING.getValue();
        } else if ("error".equalsIgnoreCase(status)) {
            return EventStatus.ERROR.getValue();
        } else {
            return EventStatus.INFO.getValue();
        }
    }

}
