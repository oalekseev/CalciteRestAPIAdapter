package org.apache.calcite.adapter.restapi.model;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import lombok.Data;

import java.util.List;

@Data
public class RequestData {

    @JacksonXmlProperty
    private String addresses;

    @JacksonXmlProperty
    private int connectionTimeout;

    @JacksonXmlProperty
    private int responseTimeout;

    @JacksonXmlProperty
    private String method;

    @JacksonXmlProperty
    private String url;

    @JacksonXmlProperty
    private String body;

    @JacksonXmlProperty
    private int pageStart;

    @JacksonXmlProperty
    private int pageSize;

    @JacksonXmlProperty
    private List<Header> headers;

}
