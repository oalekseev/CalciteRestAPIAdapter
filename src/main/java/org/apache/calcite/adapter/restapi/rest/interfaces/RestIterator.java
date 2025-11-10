package org.apache.calcite.adapter.restapi.rest.interfaces;

import org.apache.calcite.adapter.restapi.rest.ResponseRowReader;

import java.util.List;

public interface RestIterator {

    List<ResponseRowReader> getMore();

}
