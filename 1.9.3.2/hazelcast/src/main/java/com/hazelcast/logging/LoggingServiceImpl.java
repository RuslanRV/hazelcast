/* 
 * Copyright (c) 2008-2010, Hazel Ltd. All Rights Reserved.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at 
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.hazelcast.logging;

import com.hazelcast.core.Member;

import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.LogRecord;

public class LoggingServiceImpl implements LoggingService {
    private final Member thisMember;
    private final String groupName;
    private final CopyOnWriteArrayList<LogListenerRegistration> lsListeners
            = new CopyOnWriteArrayList<LogListenerRegistration>();

    private final ConcurrentMap<String, ILogger> mapLoggers = new ConcurrentHashMap<String, ILogger>(100);
    private volatile Level minLevel = Level.OFF;

    public LoggingServiceImpl(String groupName, Member thisMember) {
        this.groupName = groupName;
        this.thisMember = thisMember;
    }

    public ILogger getLogger(String name) {
        ILogger logger = mapLoggers.get(name);
        if (logger == null) {
            ILogger newLogger = new DefaultLogger(name);
            logger = mapLoggers.putIfAbsent(name, newLogger);
            if (logger == null) {
                logger = newLogger;
            }
        }
        return logger;
    }

    public void addLogListener(Level level, LogListener logListener) {
        lsListeners.add(new LogListenerRegistration(level, logListener));
        if (level.intValue() < minLevel.intValue()) {
            minLevel = level;
        }
    }

    public void removeLogListener(LogListener logListener) {
        Iterator<LogListenerRegistration> it = lsListeners.iterator();
        while (it.hasNext()) {
            LogListenerRegistration logListenerRegistration = it.next();
            if (logListenerRegistration.getLogListener() == logListener) {
                it.remove();
            }
        }
    }

    void handleLogEvent(LogEvent logEvent) {
        for (LogListenerRegistration logListenerRegistration : lsListeners) {
            if (logEvent.getLogRecord().getLevel().intValue() >= logListenerRegistration.getLevel().intValue()) {
                logListenerRegistration.getLogListener().log(logEvent);
            }
        }
    }

    class LogListenerRegistration {
        Level level;
        LogListener logListener;

        LogListenerRegistration(Level level, LogListener logListener) {
            this.level = level;
            this.logListener = logListener;
        }

        public Level getLevel() {
            return level;
        }

        public LogListener getLogListener() {
            return logListener;
        }
    }

    class DefaultLogger implements ILogger {
        final String name;
        final ILogger logger;

        DefaultLogger(String name) {
            this.name = name;
            this.logger = Logger.getLogger(name);
        }

        public void log(Level level, String message) {
            log(level, message, null);
        }

        public void log(Level level, String message, Throwable thrown) {
            boolean loggable = logger.isLoggable(level);
            if (loggable || level.intValue() >= minLevel.intValue()) {
                message = "[" + groupName + "] " + message;
                LogRecord logRecord = new LogRecord(level, message);
                logRecord.setThrown(thrown);
                logRecord.setLoggerName(name);
                logRecord.setSourceClassName(name);
                LogEvent logEvent = new LogEvent(logRecord, groupName, thisMember);
                if (loggable) {
                    logger.log(logEvent);
                }
                if (lsListeners.size() > 0) {
                    handleLogEvent(logEvent);
                }
            }
        }

        public void log(LogEvent logEvent) {
            handleLogEvent(logEvent);
        }

        public Level getLevel() {
            return logger.getLevel();
        }

        public boolean isLoggable(Level level) {
            return logger.isLoggable(level);
        }
    }
}
