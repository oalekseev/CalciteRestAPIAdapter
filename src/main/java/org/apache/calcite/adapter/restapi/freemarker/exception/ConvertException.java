package org.apache.calcite.adapter.restapi.freemarker.exception;

public class ConvertException extends RuntimeException {

    public ConvertException(String message) {
        super(message);
    }

    public ConvertException(Throwable cause) {
        super(cause);
    }

    public static ConvertException buildConvertException(Throwable cause) {
        return new ConvertException(cause);
    }

}
