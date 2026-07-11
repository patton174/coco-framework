package io.github.coco.logging.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/**
 * 基于 SLF4J 的异步日志丢弃监听器测试。
 *
 * @author patton174
 * @since 1.0.0
 */
class Slf4jCocoAsyncLogDropListenerTest {

    @Test
    void reportsOnlyFirstDropAndPowersOfTwo() {
        Logger logger = (Logger) LoggerFactory.getLogger(Slf4jCocoAsyncLogDropListener.class);
        Level previousLevel = logger.getLevel();
        boolean previousAdditive = logger.isAdditive();
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.setLevel(Level.WARN);
        logger.setAdditive(false);
        logger.addAppender(appender);

        try {
            Slf4jCocoAsyncLogDropListener listener = new Slf4jCocoAsyncLogDropListener();
            listener.onDropped(CocoLogLevel.TRACE, "handle-1", 1L);
            listener.onDropped(CocoLogLevel.DEBUG, "handle-2", 2L);
            listener.onDropped(CocoLogLevel.INFO, "handle-3", 3L);
            listener.onDropped(CocoLogLevel.INFO, "handle-4", 4L);
            listener.onDropped(CocoLogLevel.INFO, "handle-5", 5L);

            assertEquals(List.of(
                    "Coco async log queue overflow: totalDropped=1, level=TRACE, handleName=handle-1",
                    "Coco async log queue overflow: totalDropped=2, level=DEBUG, handleName=handle-2",
                    "Coco async log queue overflow: totalDropped=4, level=INFO, handleName=handle-4"),
                    appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList());
            assertTrue(appender.list.stream().allMatch(event -> event.getLevel() == Level.WARN));
            appender.list.forEach(event -> assertNull(event.getThrowableProxy()));
        }
        finally {
            logger.detachAppender(appender);
            logger.setLevel(previousLevel);
            logger.setAdditive(previousAdditive);
            appender.stop();
        }
    }
}
