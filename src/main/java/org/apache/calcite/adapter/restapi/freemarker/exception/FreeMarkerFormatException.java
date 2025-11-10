package org.apache.calcite.adapter.restapi.freemarker.exception;

public class FreeMarkerFormatException extends FreeMarkerException {
    private String message;

    public FreeMarkerFormatException(String message, Throwable cause) {
        super(message, cause);
        this.message = message;
    }

    public FreeMarkerFormatException(String message) {
        super(message);
        this.message = message;
    }

    public FreeMarkerFormatException(Exception ex) {
        super(ex);
    }


    @Override
    public String toString() {
        return message;
    }
}
