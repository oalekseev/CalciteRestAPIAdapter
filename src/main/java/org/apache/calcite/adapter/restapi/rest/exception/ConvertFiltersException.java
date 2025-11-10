package org.apache.calcite.adapter.restapi.rest.exception;

import org.apache.calcite.adapter.restapi.freemarker.exception.ConvertException;

public class ConvertFiltersException extends ConvertException {

    ConvertFiltersException(Throwable cause) {
        super(cause);
    }

    ConvertFiltersException(String message) {
        super(message);
    }

    public static ConvertFiltersException buildConvertFiltersException(Throwable cause) {
        return new ConvertFiltersException(cause);
    }

}
