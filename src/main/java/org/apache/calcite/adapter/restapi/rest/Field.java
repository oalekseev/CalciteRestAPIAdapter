package org.apache.calcite.adapter.restapi.rest;

import freemarker.template.TemplateModel;
import lombok.Getter;
import lombok.Setter;
import org.apache.calcite.rel.type.RelDataType;

@Getter
@Setter
public class Field {

    private String name;
    private RestFieldType restFieldType;
    private RelDataType relDataType;
    private boolean isRequestParameter;
    private boolean isResponseParameter;
    private String jsonpath;
    private TemplateModel requestValue;

    public Field(String name, RestFieldType restFieldType, RelDataType relDataType) {
        this.name = name;
        this.restFieldType = restFieldType;
        this.relDataType = relDataType;
    }

}
