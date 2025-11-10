package org.apache.calcite.adapter.restapi.freemarker;

public interface ConfigReaderLog {
    void logInfo(String message);
    void logDebug(String message);
    void logError(String message);
}
