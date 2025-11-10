package org.apache.calcite.adapter.restapi.freemarker.exception;

public class FreeMarkerException extends RuntimeException {
    private String message;

    public FreeMarkerException(String message, Throwable cause) {
        super(message, cause);
        this.message = message;
    }
    public FreeMarkerException(String message) {
        super(message);
        this.message = message;
    }

    public FreeMarkerException(Throwable cause) {
        super(cause);
    }

    @Override
    public String toString() {
        return message;
    }
}
