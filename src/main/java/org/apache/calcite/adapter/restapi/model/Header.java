package org.apache.calcite.adapter.restapi.model;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import lombok.Data;

@Data
public class Header {

    @JacksonXmlProperty
    private String key;

    @JacksonXmlProperty
    private String value;

}
