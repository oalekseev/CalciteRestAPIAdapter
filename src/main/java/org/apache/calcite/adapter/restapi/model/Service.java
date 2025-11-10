package org.apache.calcite.adapter.restapi.model;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import lombok.Data;

import java.util.List;

@Data
public class Service {

    @JacksonXmlProperty
    private String dataSourceName;

    @JacksonXmlProperty
    private String schemaName;

    @JacksonXmlProperty
    private String description;

    @JacksonXmlProperty
    private RequestData requestData;

    @JacksonXmlProperty
    private List<Table> tables;

}
