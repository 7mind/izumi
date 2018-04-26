package org.slf4j.impl;

import com.github.pshirshov.izumi.logstage.adapter.slf4j.LogstageLoggerFactory;
import org.slf4j.ILoggerFactory;
import org.slf4j.spi.LoggerFactoryBinder;

@SuppressWarnings("ALL")
public final class StaticLoggerBinder implements LoggerFactoryBinder {

    // to avoid constant folding by the compiler, this field must *not* be final
    public static String REQUESTED_API_VERSION = "1.6"; // !final

    private static final String LOGGER_FACTORY_CLASS_STR = LogstageLoggerFactory.class.getName();

    private static final StaticLoggerBinder SINGLETON = new StaticLoggerBinder();

    private final ILoggerFactory loggerFactory;

    private StaticLoggerBinder() {
        loggerFactory = new LogstageLoggerFactory();
    }

    public static StaticLoggerBinder getSingleton() {
        return SINGLETON;
    }

    @Override
    public ILoggerFactory getLoggerFactory() {
        return loggerFactory;
    }

    @Override
    public String getLoggerFactoryClassStr() {
        return LOGGER_FACTORY_CLASS_STR;
    }
}
