package org.apache.calcite.adapter.restapi.rest;

import org.apache.calcite.adapter.restapi.rest.interfaces.ArrayParamReader;
import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public class ResponseRowReader {

    private ArrayParamReader arrayParamReader;

}
