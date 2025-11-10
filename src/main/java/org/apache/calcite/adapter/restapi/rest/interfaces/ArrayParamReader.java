package org.apache.calcite.adapter.restapi.rest.interfaces;

public interface ArrayParamReader {

    Object read(int index, String path);

}
