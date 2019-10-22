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
package com.im.njams.sdk.model.svg;

import java.io.StringWriter;
import java.util.List;
import java.util.stream.Collectors;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.DOMImplementation;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.im.njams.sdk.common.NjamsSdkRuntimeException;
import com.im.njams.sdk.model.ActivityModel;
import com.im.njams.sdk.model.GroupModel;
import com.im.njams.sdk.model.ProcessModel;
import com.im.njams.sdk.model.TransitionModel;

/**
 * This is the default SDK ProcessDiagramFactory. It converts a ProcessModel
 * into an SVG and give it the default SDK look and feel.
 *
 * @author pnientiedt
 */
public class NjamsProcessDiagramFactory implements ProcessDiagramFactory {

    /**
     * default margin
     */
    protected static final int DEFAULT_MARGIN = 10;

    /**
     * default half activity size
     */
    protected static final int DEFAULT_HALF_ACTIVITY_SIZE = 25;

    /**
     * default activity size
     */
    protected static final int DEFAULT_ACTIVITY_SIZE = DEFAULT_HALF_ACTIVITY_SIZE * 2;

    /**
     * default activity radius
     */
    protected static final int DEFAULT_ACTIVITY_RADIUS = 30;

    /**
     * default text size
     */
    protected static final int DEFAULT_TEXT_SIZE = 15;

    /**
     * default marker size
     */
    protected static final int DEFAULT_MARKER_SIZE = 10;

    private static final Logger LOG = LoggerFactory.getLogger(NjamsProcessDiagramFactory.class);

    /**
     * This function converts a ProcessModel to a SVG.
     *
     * @param processModel The ProcessModel to be converted
     * @return The created SVG as as String.
     */
    @Override
    public String getProcessDiagram(ProcessModel processModel) {
        try {

            // create a new DocumentBuilderFactory
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();

            // use the factory to create a documentbuilder
            DocumentBuilder builder = factory.newDocumentBuilder();

            DOMImplementation impl = builder.getDOMImplementation();
            String svgNS = "http://www.w3.org/2000/svg";
            Document doc = impl.createDocument(svgNS, "svg", null);

            NjamsProcessDiagramContext context = new NjamsProcessDiagramContext();
            context.setSvgNS(svgNS);
            context.setDoc(doc);
            context.setCategory(processModel.getNjams().getCategory());

            createSvg(context, processModel);

            String svg = serializeDocument(context);
            LOG.trace("Created ProcessDiagram from ProcessModel: {}", svg);
            return svg;
        } catch (Exception e) {
            LOG.error("Error in NjamsProcessDiagramFactory", e);
            throw new NjamsSdkRuntimeException("Error in NjamsProcessDiagramFactory", e);
        }
    }

    /**
     * Create the SVG
     *
     * @param context of the NjamsProcessDiagramFactory
     * @param processModel to draw
     */
    public void createSvg(NjamsProcessDiagramContext context, ProcessModel processModel) {
        // get the root element (the svg element)
        Element svgRoot = context.getDoc().getDocumentElement();
        svgRoot.setAttributeNS(null, "id", "processdiagram_svg");

        Element outerG = context.getDoc().createElementNS(context.getSvgNS(), "g");
        outerG.setAttributeNS(null, "id", "viewport");
        svgRoot.appendChild(outerG);

        Element innerG = context.getDoc().createElementNS(context.getSvgNS(), "g");
        innerG.setAttributeNS(null, "id", "graph");
        outerG.appendChild(innerG);
        context.setContainerElement(innerG);

        getSvgSize(context, processModel);

        // set the width and height attribute on the root svg element
        svgRoot.setAttributeNS(null, "width", String.valueOf(context.getWidth()));
        svgRoot.setAttributeNS(null, "height", String.valueOf(context.getHeight()));

        // draw root activities
        List<ActivityModel> rootActivities
                = processModel.getActivityModels().stream().filter(a -> a.getParent() == null)
                        .filter(a -> !(a instanceof GroupModel)).collect(Collectors.toList());
        rootActivities.forEach(a -> drawActivity(context, a));

        // draw root transitions
        List<TransitionModel> rootTransition = processModel.getTransitionModels().stream()
                .filter(a -> a.getParent() == null).collect(Collectors.toList());
        rootTransition.forEach(t -> drawTransition(context, t));

        // draw root groups
        List<GroupModel> rootGroups = processModel.getActivityModels().stream().filter(a -> a.getParent() == null)
                .filter(a -> a instanceof GroupModel).map(GroupModel.class::cast).collect(Collectors.toList());
        rootGroups.forEach(a -> drawGroup(context, a));

        drawExtraElements(context);
    }

    /**
     * Calculate the total SVG size, based on every elements min and max X/Y
     *
     * @param context of the NjamsProcessDiagramFactory
     * @param processModel to draw
     */
    private void getSvgSize(NjamsProcessDiagramContext context, ProcessModel processModel) {
        int minX = 0;
        int maxX = 0;
        int minY = 0;
        int maxY = 0;
        // calculate mins
        for (ActivityModel am : processModel.getActivityModels()) {
            if (am.getX() < minX) {
                minX = am.getX();
            }
            if (am.getX() > maxX) {
                maxX = am.getX();
            }
            if (am.getY() < minY) {
                minY = am.getY();
            }
            if (am.getY() > maxY) {
                maxY = am.getY();
            }
        }
        // add margin to left
        minX -= DEFAULT_MARGIN;
        // add activitySize and margin to right
        maxX += DEFAULT_ACTIVITY_SIZE + DEFAULT_MARGIN;
        // add margin to top;
        minY -= DEFAULT_MARGIN;
        // add activitySize and margin and two times the text size to the bottom
        maxY += DEFAULT_ACTIVITY_SIZE + DEFAULT_MARGIN + (DEFAULT_TEXT_SIZE * 2);
        // calculate sizsed
        int width = maxX - minX;
        int height = maxY - minY;
        int startX = Math.abs(minX);
        int startY = Math.abs(minY);
        LOG.debug("SVG sizes: height: {}, width: {}, startX: {}, startY: {}", height, width, startX, startY);
        // save in context
        context.setHeight(height);
        context.setWidth(width);
        context.setStartX(startX);
        context.setStartY(startY);
    }

    /**
     * This function draws a activity. A activity is a composition of the
     * activity image, and two texts below that image. See the example below
     *
     * @param context of the NjamsProcessDiagramFactory
     * @param activityModel to draw
     */
    protected void drawActivity(NjamsProcessDiagramContext context, ActivityModel activityModel) {

        /**
         * Calculate absolute coordinates.
         */
        double activityX = context.getStartX() + activityModel.getX();
        double activityY = context.getStartY() + activityModel.getY();
        double labelX = activityX + DEFAULT_HALF_ACTIVITY_SIZE;
        double labelY = activityY + DEFAULT_ACTIVITY_SIZE + DEFAULT_TEXT_SIZE;
        double statsX = labelX;
        double statxY = labelY + DEFAULT_TEXT_SIZE;

        // create image
        Element image = context.getDoc().createElementNS(context.getSvgNS(), "image");
        image.setAttributeNS(null, "activity", "true");
        image.setAttributeNS(null, "modelId", activityModel.getId());
        image.setAttributeNS(null, "x", String.valueOf(activityX));
        image.setAttributeNS(null, "y", String.valueOf(activityY));
        image.setAttributeNS(null, "width", String.valueOf(DEFAULT_ACTIVITY_SIZE));
        image.setAttributeNS(null, "height", String.valueOf(DEFAULT_ACTIVITY_SIZE));
        image.setAttributeNS(null, "activity-type", context.getCategory() + "." + activityModel.getType());
        context.getContainerElement().appendChild(image);

        // create text for activity name
        Element activityText = context.getDoc().createElementNS(context.getSvgNS(), "text");
        activityText.setAttributeNS(null, "id", activityModel.getId() + "_label");
        activityText.setAttributeNS(null, "x", String.valueOf(labelX));
        activityText.setAttributeNS(null, "y", String.valueOf(labelY));
        activityText.setAttributeNS(null, "text-anchor", "middle");
        activityText.setTextContent(activityModel.getName());
        context.getContainerElement().appendChild(activityText);

        // create text for activity stats
        Element activityStats = context.getDoc().createElementNS(context.getSvgNS(), "text");
        activityStats.setAttributeNS(null, "id", activityModel.getId() + "_stats_text");
        activityStats.setAttributeNS(null, "x", String.valueOf(statsX));
        activityStats.setAttributeNS(null, "y", String.valueOf(statxY));
        activityStats.setAttributeNS(null, "text-anchor", "middle");
        context.getContainerElement().appendChild(activityStats);
    }

    /**
     * Draw Group
     *
     * @param context of the NjamsProcessDiagramFactory
     * @param groupModel to draw
     */
    protected void drawGroup(NjamsProcessDiagramContext context, GroupModel groupModel) {
        Element g = context.getDoc().createElementNS(context.getSvgNS(), "g");
        g.setAttributeNS(null, "id", "group_" + groupModel.getId());
        g.setAttributeNS(null, "name", groupModel.getName());
        g.setAttributeNS(null, "modelId", groupModel.getId());
        context.getContainerElement().appendChild(g);

        // save actual parent, and set the own g element as parent for the childs
        Element parentContainer = context.getContainerElement();
        context.setContainerElement(g);

        // general group sizing
        int groupX = context.getStartX() + groupModel.getX();
        int groupY = context.getStartX() + groupModel.getY();
        int groupWidth = groupModel.getWidth();
        int groupHeight = groupModel.getHeight();

        // general header sizing
        int headerX = groupX;
        int headerY = groupY;
        int headerWidth = groupWidth;
        int headerHeight = 20;

        Element rectHeader = context.getDoc().createElementNS(context.getSvgNS(), "rect");
        rectHeader.setAttributeNS(null, "id", groupModel.getId() + "_group_header");
        rectHeader.setAttributeNS(null, "x", String.valueOf(headerX));
        rectHeader.setAttributeNS(null, "y", String.valueOf(headerY));
        rectHeader.setAttributeNS(null, "width", String.valueOf(headerWidth));
        rectHeader.setAttributeNS(null, "height", String.valueOf(headerHeight));
        rectHeader.setAttributeNS(null, "fill", "#98a6e7");
        rectHeader.setAttributeNS(null, "stroke", "black");
        context.getContainerElement().appendChild(rectHeader);

        Element groupIcon = context.getDoc().createElementNS(context.getSvgNS(), "image");
        groupIcon.setAttributeNS(null, "x", String.valueOf(headerX));
        groupIcon.setAttributeNS(null, "y", String.valueOf(headerY));
        groupIcon.setAttributeNS(null, "width", String.valueOf(16));
        groupIcon.setAttributeNS(null, "height", String.valueOf(16));
        // TODO: wtf, make this configurable or whatever
        groupIcon.setAttributeNS("http://www.w3.org/1999/xlink", "xlink:href", "assets/images/svg/groupType_group.png");
        context.getContainerElement().appendChild(groupIcon);

        // general container sizing
        int containerX = headerX;
        int containerY = headerY + headerHeight;
        int containerWidth = headerWidth;
        int containerHeight = groupHeight - headerHeight;

        Element rectGroupContainer = context.getDoc().createElementNS(context.getSvgNS(), "rect");
        rectGroupContainer.setAttributeNS(null, "id", groupModel.getId() + "_group_container");
        rectGroupContainer.setAttributeNS(null, "x", String.valueOf(containerX));
        rectGroupContainer.setAttributeNS(null, "y", String.valueOf(containerY));
        rectGroupContainer.setAttributeNS(null, "width", String.valueOf(containerWidth));
        rectGroupContainer.setAttributeNS(null, "height", String.valueOf(containerHeight));
        rectGroupContainer.setAttributeNS(null, "fill", "white");
        rectGroupContainer.setAttributeNS(null, "stroke", "black");
        context.getContainerElement().appendChild(rectGroupContainer);

        // general text sizing
        int groupTextX = groupX + groupWidth / 2;
        int groupTextY = groupY + groupHeight + DEFAULT_TEXT_SIZE;

        Element groupText = context.getDoc().createElementNS(context.getSvgNS(), "text");
        groupText.setAttributeNS(null, "id", groupModel.getId() + "_label");
        groupText.setAttributeNS(null, "x", String.valueOf(groupTextX));
        groupText.setAttributeNS(null, "y", String.valueOf(groupTextY));
        groupText.setAttributeNS(null, "text-anchor", "middle");
        groupText.setTextContent(groupModel.getName());
        context.getContainerElement().appendChild(groupText);

        // draw root activities
        List<ActivityModel> groupActivities = groupModel.getChildActivities().stream()
                .filter(a -> !(a instanceof GroupModel)).collect(Collectors.toList());
        groupActivities.forEach(a -> drawActivity(context, a));

        // draw root transitions
        List<TransitionModel> groupTransitions = groupModel.getChildTransitions().stream().collect(Collectors.toList());
        groupTransitions.forEach(t -> drawTransition(context, t));

        // draw root groups
        List<GroupModel> groupGroups = groupModel.getChildActivities().stream().filter(a -> a instanceof GroupModel)
                .map(GroupModel.class::cast).collect(Collectors.toList());
        groupGroups.forEach(a -> drawGroup(context, a));

        // after drawing my childs, set back to the previous parent
        context.setContainerElement(parentContainer);
    }

    /**
     * This function draws a transition. A Transitions is a composition of a
     * line, a marker which is the arrow head, and a text for that transition.
     * See the example below
     *
     * @param context of the NjamsProcessDiagramFactory
     * @param transitionModel to draw
     */
    protected void drawTransition(NjamsProcessDiagramContext context, TransitionModel transitionModel) {

        /**
         * Calculate absoule coordiantes
         */
        Point[] points = getTransitionCoordinates(context, transitionModel);
        Point fromPoint = points[0];
        Point toPoint = points[1];

        // create marker which is the arrow head.
        Element marker = context.getDoc().createElementNS(context.getSvgNS(), "marker");
        String markerId = transitionModel.getId() + "_marker";
        marker.setAttributeNS(null, "id", markerId);
        marker.setAttributeNS(null, "name", markerId);
        marker.setAttributeNS(null, "viewbox", "0 0 10 10");
        marker.setAttributeNS(null, "refX", "1");
        marker.setAttributeNS(null, "refY", "5");
        marker.setAttributeNS(null, "markerUnits", "userSpaceOnUse");
        marker.setAttributeNS(null, "orient", "auto");
        marker.setAttributeNS(null, "markerWidth", "0.7em");
        marker.setAttributeNS(null, "markerHeight", "0.7em");
        marker.setAttributeNS(null, "fill", "#000");
        marker.setAttributeNS(null, "stroke", "#000");
        context.getContainerElement().appendChild(marker);
        Element polyline = context.getDoc().createElementNS(context.getSvgNS(), "polyline");
        polyline.setAttributeNS(null, "points", "0,0 10,5 0,10 1,5");
        marker.appendChild(polyline);

        // create arrow line
        Element line = context.getDoc().createElementNS(context.getSvgNS(), "line");
        line.setAttributeNS(null, "markerId", markerId);
        line.setAttributeNS(null, "modelId", transitionModel.getId());
        line.setAttributeNS(null, "name", transitionModel.getName());
        line.setAttributeNS(null, "x1", String.valueOf(fromPoint.getX()));
        line.setAttributeNS(null, "y1", String.valueOf(fromPoint.getY()));
        line.setAttributeNS(null, "x2", String.valueOf(toPoint.getX()));
        line.setAttributeNS(null, "y2", String.valueOf(toPoint.getY()));
        line.setAttributeNS(null, "marker-end", "url(#" + markerId + ")");
        line.setAttributeNS(null, "style", "cursor: pointer; stroke:#000; fill:#000");
        context.getContainerElement().appendChild(line);

        // create text under transition on half way to next activity
        double x = fromPoint.getX() + ((fromPoint.getX() - fromPoint.getY()) / 2);
        double y = fromPoint.getX() + DEFAULT_TEXT_SIZE;
        Element bwTransitionText = context.getDoc().createElementNS(context.getSvgNS(), "text");
        bwTransitionText.setAttributeNS(null, "id", transitionModel.getId() + "_label");
        bwTransitionText.setAttributeNS(null, "x", String.valueOf(x));
        bwTransitionText.setAttributeNS(null, "y", String.valueOf(y));
        bwTransitionText.setAttributeNS(null, "text-anchor", "middle");
        context.getContainerElement().appendChild(bwTransitionText);
    }

    /**
     * This can be overwritten to draw any extra content after everything else
     *
     * @param context of the NjamsProcessDiagramFactory
     */
    protected void drawExtraElements(NjamsProcessDiagramContext context) {
        // no default implementation
    }

    /**
     * Calculate absoule coordiantes. First get the center point of both
     * activites. Then get the from and to points for a given radius to the
     * center points.
     */
    private Point[] getTransitionCoordinates(NjamsProcessDiagramContext context, TransitionModel t) {

        ActivityModel fromActivity = t.getFromActivity();
        ActivityModel toActivity = t.getToActivity();
        Point fromPoint;
        Point toPoint;
        if (fromActivity instanceof GroupModel) {
            GroupModel fromGroup = (GroupModel) fromActivity;
            int x = context.getStartX() + fromGroup.getX() + fromGroup.getWidth();
            int y = context.getStartY() + fromGroup.getY() + fromGroup.getHeight() / 2;
            fromPoint = new Point(x, y);
            LOG.debug("FromPoint for {} is {}:{}", fromGroup.getName(), fromPoint.getX(), fromPoint.getY());
        } else {
            int startCenterX = context.getStartX() + fromActivity.getX() + DEFAULT_HALF_ACTIVITY_SIZE;
            int startCenterY = context.getStartY() + fromActivity.getY() + DEFAULT_HALF_ACTIVITY_SIZE;
            fromPoint = new Point(startCenterX, startCenterY);
        }
        if (toActivity instanceof GroupModel) {
            GroupModel toGroup = (GroupModel) toActivity;
            int x = context.getStartX() + toGroup.getX();
            int y = context.getStartY() + toGroup.getY() + toGroup.getHeight() / 2;
            toPoint = new Point(x, y);
            LOG.debug("ToPoint for {} is {}:{}", toGroup.getName(), toPoint.getX(), toPoint.getY());
        } else {
            int endCenterX = context.getStartX() + toActivity.getX() + DEFAULT_HALF_ACTIVITY_SIZE;
            int endCenterY = context.getStartY() + toActivity.getY() + DEFAULT_HALF_ACTIVITY_SIZE;
            toPoint = new Point(endCenterX, endCenterY);
        }
        if (!(fromActivity instanceof GroupModel)) {
            // calculate radius points
            fromPoint = getRadiusPoint(toPoint, fromPoint, DEFAULT_ACTIVITY_RADIUS);
        }
        if (!(toActivity instanceof GroupModel)) {
            // add the marker size to the radius, because it will come on top of the line drawn.
            toPoint = getRadiusPoint(fromPoint, toPoint, DEFAULT_ACTIVITY_RADIUS + DEFAULT_MARKER_SIZE);
        } else {
            // calculate for marker radius
            toPoint = getRadiusPoint(fromPoint, toPoint, DEFAULT_MARKER_SIZE);
            LOG.debug("new toPoint for Group {} is {}:{}", toActivity.getName(), toPoint.getX(), toPoint.getY());
        }
        return new Point[]{fromPoint, toPoint};
    }

    /**
     * Serialize the the Document in the NjamsProcessDiagramContext to a string
     * representing the SVG.
     *
     * @param context of the NjamsProcessDiagramFactory
     * @return
     * @throws TransformerException
     */
    private String serializeDocument(NjamsProcessDiagramContext context) throws TransformerException {
        StringWriter sw = new StringWriter();
        TransformerFactory tf = TransformerFactory.newInstance();
        Transformer transformer = tf.newTransformer();
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
        transformer.setOutputProperty(OutputKeys.METHOD, "xml");
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");

        transformer.transform(new DOMSource(context.getDoc()), new StreamResult(sw));
        return sw.toString();
    }

    /**
     * Calculate the Point where a line drawn from fromPoint to centerPoint cuts
     * a circle with the radius radius. This will be used do get the best
     * looking starting and endpoint for transitions connecting two activities.
     *
     * @param fromPoint
     * @param centerPoint
     * @param radius
     * @return
     */
    private Point getRadiusPoint(Point fromPoint, Point centerPoint, double radius) {
        double vX = fromPoint.getX() - centerPoint.getX();
        double vY = fromPoint.getY() - centerPoint.getY();
        double magV = Math.sqrt(vX * vX + vY * vY);
        double aX = centerPoint.getX() + vX / magV * radius;
        double aY = centerPoint.getY() + vY / magV * radius;
        return new Point(aX, aY);
    }
}
