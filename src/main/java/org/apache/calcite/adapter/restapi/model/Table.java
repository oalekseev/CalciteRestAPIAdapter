package org.apache.calcite.adapter.restapi.model;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import lombok.Data;

import java.util.List;

@Data
public class Table {

    @JacksonXmlProperty
    private String name;

    @JacksonXmlProperty
    private String rootJsonpath;

    @JacksonXmlProperty
    private List<Parameter> parameters;

}
