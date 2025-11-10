package org.apache.calcite.adapter.restapi.model;

public enum Direction {

    REQUEST,
    RESPONSE,
    BOTH;

    public boolean isRequestParam() {
        return this == REQUEST || this == BOTH;
    }

    public boolean isResponseParam() {
        return this == RESPONSE || this == BOTH;
    }

}
