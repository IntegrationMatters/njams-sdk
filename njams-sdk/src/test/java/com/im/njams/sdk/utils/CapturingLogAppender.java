package com.im.njams.sdk.utils;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import org.apache.log4j.AppenderSkeleton;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.spi.LoggingEvent;

/**
 * Captures the log events of one logger so that a test can assert on their level and message. Use
 * {@link #attach(Class)} and always {@link #detach()} in a finally block.
 */
public final class CapturingLogAppender extends AppenderSkeleton {
    private final List<LoggingEvent> events = new CopyOnWriteArrayList<>();
    private Logger logger;
    private Level originalLevel;

    public static CapturingLogAppender attach(Class<?> loggerClass) {
        CapturingLogAppender appender = new CapturingLogAppender();
        appender.logger = Logger.getLogger(loggerClass);
        appender.originalLevel = appender.logger.getLevel();
        appender.logger.addAppender(appender);
        appender.logger.setLevel(Level.DEBUG);
        return appender;
    }

    public void detach() {
        logger.removeAppender(this);
        logger.setLevel(originalLevel);
    }

    public List<LoggingEvent> events(Level level) {
        return events.stream().filter(e -> e.getLevel().equals(level)).collect(Collectors.toList());
    }

    @Override
    protected void append(LoggingEvent event) {
        events.add(event);
    }

    @Override
    public void close() {
        // nothing to release
    }

    @Override
    public boolean requiresLayout() {
        return false;
    }
}
