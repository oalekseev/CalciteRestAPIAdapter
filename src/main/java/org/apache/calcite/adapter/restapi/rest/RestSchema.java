package org.apache.calcite.adapter.restapi.rest;

import org.apache.calcite.adapter.restapi.model.RequestData;
import org.apache.calcite.adapter.restapi.model.Service;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import freemarker.template.TemplateModel;
import org.apache.calcite.schema.Table;
import org.apache.calcite.schema.impl.AbstractSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class RestSchema extends AbstractSchema {
    Logger logger = LoggerFactory.getLogger(RestSchema.class);

    private final String group;
    private final Map<String, TemplateModel> context = new HashMap<>();

    private Map<String, Table> tableMap;
    private final XmlMapper xmlMapper = new XmlMapper();

    @SuppressWarnings("unchecked")
    public RestSchema(Map<String, Object> map) {
        if (map != null) {
            this.group = (String) map.get("group");
            Object contextObject = map.get("context");
            if (contextObject != null)
                this.context.putAll((Map<String, TemplateModel>) contextObject);
        } else {
            this.group = "";
        }
    }

    @Override
    protected Map<String, Table> getTableMap() {
        try {
            if (tableMap == null) {
                tableMap = new HashMap<>();
                String calciteRestDirectory = System.getProperty("calcite.rest");
                if (calciteRestDirectory == null) {
                    calciteRestDirectory = System.getProperty("catalina.base") + File.separator + "calcite" + File.separator + "rest";
                }

                try (Stream<Path> stream = Files.walk(Paths.get(calciteRestDirectory))) {
                    stream.filter(Files::isRegularFile)
                            .filter(p -> p.getFileName().toString().endsWith(".xml"))
                            .map(this::createTable)
                            .forEach(tableMap::putAll);
                }
            }
            return tableMap;
        } catch (IOException e) {
            logger.error("Xml files processing exception: {}", e.getMessage());
            throw new RuntimeException(e);
        }
    }

    private Map<String, Table> createTable(Path path) {
        try {
            Service service = xmlMapper.readValue(path.toFile(), Service.class);
            RequestData requestData = service.getRequestData();

            return service.getTables().stream()
                    .collect(Collectors.toMap(
                            org.apache.calcite.adapter.restapi.model.Table::getName,
                            table -> new RestTable(group, requestData, table, context)));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
