package org.apache.calcite.adapter.restapi.model;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import lombok.Data;

@Data
public class Parameter {

    @JacksonXmlProperty
    private String name;

    @JacksonXmlProperty
    private String dbType;

    @JacksonXmlProperty
    private String jsonpath;

    @JacksonXmlProperty
    private Direction type;

}
