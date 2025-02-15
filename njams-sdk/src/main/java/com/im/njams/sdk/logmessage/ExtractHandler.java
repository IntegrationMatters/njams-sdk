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
package com.im.njams.sdk.logmessage;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.transform.sax.SAXSource;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import com.faizsiegeln.njams.messageformat.v4.projectmessage.AttributeType;
import com.faizsiegeln.njams.messageformat.v4.projectmessage.Extract;
import com.faizsiegeln.njams.messageformat.v4.projectmessage.ExtractRule;
import com.faizsiegeln.njams.messageformat.v4.projectmessage.RuleType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.im.njams.sdk.configuration.ActivityConfiguration;
import com.im.njams.sdk.model.ActivityModel;
import com.im.njams.sdk.utils.StringUtils;

import io.burt.jmespath.Expression;
import io.burt.jmespath.JmesPath;
import io.burt.jmespath.jackson.JacksonRuntime;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.xpath.XPathEvaluator;

/**
 * @author pnientiedt
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
     * @param data            The serialized data object on that the extract is being evaluated.
     */
    public static void handleExtract(JobImpl job, ActivityImpl activity, ExtractSource sourceDirection, String data) {
        ActivityConfiguration config = null;
        ActivityModel model = activity.getActivityModel();
        config = job.getActivityConfiguration(model);

        if (config == null) {
            return;
        }
        handleExtract(job, config.getExtract(), activity, sourceDirection, data);
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
     * @param data            The serialized data object on that the extract is being evaluated.
     */
    public static void handleExtract(JobImpl job, Extract extract, ActivityImpl activity, ExtractSource sourceDirection,
            String data) {
        if (extract == null) {
            return;
        }
        for (ExtractRule er : extract.getExtractRules()) {
            if (er.getRuleType() == RuleType.DISABLED || !er.getInout().equalsIgnoreCase(sourceDirection.direction)) {
                continue;
            }
            if (LOG.isDebugEnabled()) {
                LOG.debug("nJAMS: after execution - rule: {}", er.getRule());
            }

            String maskedData = DataMasking.maskString(data);
            switch (er.getRuleType()) {
            case REGEXP:
                doRegexp(job, activity, er, maskedData);
                break;
            case EVENT:
                // TODO: does this make sense? does this case really exist => MSG-27 ?
                doEvent(job, activity, er);
                break;
            case VALUE:
                doValue(job, activity, er);
                break;
            case XPATH:
                doXpath(job, activity, er, maskedData);
                break;
            case JMESPATH:
                doJmespath(job, activity, er, maskedData);
                break;
            default:
                break;
            }
        }
    }

    private static void
            doRegexp(JobImpl job, ActivityImpl activity, ExtractRule er, String data) {
        if (StringUtils.isBlank(data)) {
            return;
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

    private static String applyJmespath(String expression, String data) throws IOException {
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
                strResult = result.toString();
                if (strResult.charAt(0) == '"') {
                    strResult = strResult.substring(1);
                }
                if (strResult.charAt(strResult.length() - 1) == '"') {
                    strResult = strResult.substring(0, strResult.length() - 1);
                }

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

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static String applyXpath(String xpath, String data) throws XPathException, XPathExpressionException {
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
            LOG.error("Unknown class {} returned from XPath evaluator", result.getClass());
        }
        if (nodes == null || nodes.isEmpty()) {
            return "";
        }
        final StringBuilder strResult = new StringBuilder();
        for (Object node : nodes) {
            if (node instanceof net.sf.saxon.tinytree.TinyNodeImpl
                    && !(node instanceof net.sf.saxon.tinytree.WhitespaceTextImpl)) {
                strResult.append(((net.sf.saxon.tinytree.TinyNodeImpl) node).getStringValue());
            } else {
                strResult.append(node);
            }
        }
        return strResult.toString();
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

    private static void doJmespath(JobImpl job, ActivityImpl activity, ExtractRule er, String data) {
        if (StringUtils.isBlank(data)) {
            return;
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

    private static void doXpath(JobImpl job, ActivityImpl activity, ExtractRule er,
            String data) {
        if (StringUtils.isBlank(data)) {
            return;
        }
        try {
            String strResult = applyXpath(er.getRule(), data);
            if (er.getAttributeType() == AttributeType.EVENT) {
                LOG.debug("nJAMS: xpath extract for setting: {}", er.getAttribute());
                LOG.debug("nJAMS: xpath result: {}", strResult);
                activity.setEventStatus(getEventStatus(strResult));
            } else {
                LOG.debug("nJAMS: xpath extract for setting: {}", er.getAttribute());
                LOG.debug("nJAMS: xpath result: {}", strResult);
                setAttributes(job, activity, er.getAttribute(), strResult);
            }
            job.setInstrumented();
        } catch (Exception e) {
            LOG.error("Failed to evaluate xpath for setting: {} and rule: {}.", er.getAttribute(), er.getRule(), e);
        }
    }

    private static Matcher getExtractMatcher(String rule) {
        try {
            return MATCHER.computeIfAbsent(rule, k -> Pattern.compile(k).matcher(""));
        } catch (Exception e) {
            LOG.warn("Could not compile pattern: {} ({})", rule, e.getMessage());
            LOG.debug("Error in getExtractMatcher", e);
        }
        return null;
    }

    private static void setAttributes(Job job, ActivityImpl activity, String setting, String uncheckedvalue) {
        String value = DataMasking.maskString(uncheckedvalue);
        LOG.debug("nJAMS: setAttributes: {}={}", setting, value);

        switch (setting.toLowerCase()) {
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
        if ("success".equalsIgnoreCase(status) || "1".equals(status)) {
            return EventStatus.SUCCESS.getValue();
        }
        if ("warning".equalsIgnoreCase(status) || "2".equals(status)) {
            return EventStatus.WARNING.getValue();
        }
        if ("error".equalsIgnoreCase(status) || "3".equals(status)) {
            return EventStatus.ERROR.getValue();
        }
        return EventStatus.INFO.getValue();
    }

}
