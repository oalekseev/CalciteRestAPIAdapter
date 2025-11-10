package org.apache.calcite.adapter.restapi.rest;

import org.apache.calcite.schema.Schema;
import org.apache.calcite.schema.SchemaFactory;
import org.apache.calcite.schema.SchemaPlus;

import java.util.Map;

public class RestSchemaFactory implements SchemaFactory {

    public Schema create(SchemaPlus schemaPlus, String s, Map<String, Object> map) {
        return new RestSchema(map);
    }
}