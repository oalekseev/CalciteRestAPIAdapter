package org.apache.calcite.adapter.restapi.rest;

import org.apache.calcite.adapter.restapi.freemarker.CalendarDate;
import org.apache.calcite.adapter.restapi.freemarker.FreeMarkerEngine;
import org.apache.calcite.adapter.restapi.freemarker.exception.ConvertException;
import org.apache.calcite.adapter.restapi.model.Header;
import org.apache.calcite.adapter.restapi.model.Parameter;
import org.apache.calcite.adapter.restapi.model.RequestData;
import org.apache.calcite.adapter.restapi.model.Table;
import org.apache.calcite.adapter.restapi.rest.exception.ConvertFiltersException;
import org.apache.calcite.adapter.restapi.rest.interfaces.ArrayParamReader;
import org.apache.calcite.adapter.restapi.rest.interfaces.ArrayReader;
import com.google.common.base.Joiner;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import freemarker.template.*;
import net.minidev.json.JSONArray;
import org.apache.calcite.DataContext;
import org.apache.calcite.adapter.java.JavaTypeFactory;
import org.apache.calcite.config.CalciteConnectionProperty;
import org.apache.calcite.jdbc.CalciteConnection;
import org.apache.calcite.linq4j.AbstractEnumerable;
import org.apache.calcite.linq4j.Enumerable;
import org.apache.calcite.linq4j.Enumerator;
import org.apache.calcite.plan.RelOptUtil;
import org.apache.calcite.rel.type.RelDataType;
import org.apache.calcite.rel.type.RelDataTypeFactory;
import org.apache.calcite.rel.type.RelDataTypeField;
import org.apache.calcite.rex.*;
import org.apache.calcite.schema.ProjectableFilterableTable;
import org.apache.calcite.schema.impl.AbstractTable;
import org.apache.calcite.sql.SqlOperator;
import org.apache.calcite.util.Pair;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

public class RestTable extends AbstractTable implements ProjectableFilterableTable {
    private final Logger logger = LoggerFactory.getLogger(RestTable.class);

    private final String group;
    private final Map<String, TemplateModel> commonContext;

    private final Table table;
    private final RequestData connectionData;
    private Map<String, Field> fieldsMap;

    public RestTable(String group, RequestData connectionData, Table table, Map<String, TemplateModel> context) {
        this.group = group;
        this.connectionData = connectionData;
        this.table = table;
        this.commonContext = context;
        FreeMarkerEngine.init();
    }

    @Override
    public RelDataType getRowType(RelDataTypeFactory typeFactory) {
        if (fieldsMap == null) {
            fieldsMap = new LinkedHashMap<>();

            if (table.getParameters() != null) {
                for (Parameter parameter : table.getParameters()) {
                    if (parameter.getType() != null) {
                        Field field = null;
                        if (parameter.getType().isResponseParam()) {
                            RestFieldType restFieldType = RestFieldType.of(parameter.getDbType());
                            field = new Field(parameter.getName(), restFieldType, restFieldType.toType((JavaTypeFactory) typeFactory));
                            field.setJsonpath(parameter.getJsonpath());
                            field.setResponseParameter(true);
                        }

                        if (parameter.getType().isRequestParam()) {
                            if (field == null) {
                                RestFieldType restFieldType = RestFieldType.of(parameter.getDbType());
                                field = new Field(parameter.getName(), restFieldType, restFieldType.toType((JavaTypeFactory) typeFactory));
                            }
                            field.setRequestParameter(true);
                        }
                        fieldsMap.put(parameter.getName(), field);
                    }
                }
            }
        }

        return getRelDataType(typeFactory);
    }

    @Override
    public Enumerable<Object[]> scan(DataContext root, List<RexNode> filters, int[] projects) {
        List<List<RexNode>> dnfFilters = convertToDnf(root, filters);
        Set<String> selectedProjectFields = getSelectedProjectFields(root.getTypeFactory(), projects);
        Properties properties = ((CalciteConnection) root.getQueryProvider()).getProperties();

        int pageSize = connectionData.getPageSize();
        AtomicInteger offset = new AtomicInteger(pageSize * connectionData.getPageStart());

        Map.Entry<String, List<ResponseRowReader>> restResult = getRestResult(null, dnfFilters, offset.get(), properties, selectedProjectFields);
        AtomicBoolean hasMore = new AtomicBoolean(pageSize > 0 && restResult.getValue().size() == pageSize);
        return new AbstractEnumerable<>() {
            public Enumerator<Object[]> enumerator() {
                return new RestDataEnumerator(restResult.getValue(), fieldsMap, projects, () -> {
                    if (hasMore.get()) {
                        Map.Entry<String, List<ResponseRowReader>> restResultMore = getRestResult(restResult.getKey(), dnfFilters, offset.addAndGet(pageSize), properties, selectedProjectFields);
                        hasMore.set(pageSize > 0 && restResultMore.getValue().size() == pageSize);
                        return restResultMore.getValue();
                    } else {
                        return Collections.emptyList();
                    }
                });
            }
        };
    }

    public String getTableName() {
        return this.table.getName();
    }

    public Map.Entry<String, List<ResponseRowReader>> getRestResult(String address, List<List<RexNode>> filters, int offset, Properties properties, Set<String> selectedProjectFields) {
        if (address == null) {
            List<String> errors = new ArrayList<>();
            for (String tryingAddress : connectionData.getAddresses().split(",")) {
                try {
                    return doRequest(tryingAddress.trim(), filters, offset, properties, selectedProjectFields);
                } catch (IOException e) {
                    errors.add(e.getMessage());
                    logger.warn(e.getMessage());
                }
            }
            throw new RuntimeException("All requests attempts are failed. \n" + Joiner.on(", \n").join(errors));
        } else {
            try {
                return doRequest(address, filters, offset, properties, selectedProjectFields);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private Map.Entry<String, List<ResponseRowReader>> doRequest(String address, List<List<RexNode>> filters, int offset, Properties properties, Set<String> selectedProjectFields) throws ConvertException, IOException {
        String httpResponse = executeRequest(buildRequest(address, filters, offset, properties, selectedProjectFields));

        List<ResponseRowReader> result = new ArrayList<>();
        if (table.getParameters() != null && !table.getParameters().isEmpty()) {
            JSONArray jsonArray = new ArrayReaderImpl(httpResponse).read(table.getRootJsonpath());
            if (jsonArray != null) {
                for (Object o : jsonArray) {
                    result.add(new ResponseRowReader(new ArrayParamReaderImpl(o)));
                }
            }
        }

        return new AbstractMap.SimpleImmutableEntry<>(address, result);
    }

    private String executeRequest(HttpUriRequestBase request) throws IOException {
        logger.debug("Trying: '{}'", request.getRequestUri());

        try (var httpClient = HttpClientBuilder.create().build()) {
            return httpClient.execute(request, response -> {
                int statusCode = response.getCode();
                if (!isSuccessfulResponse(statusCode)) {
                    throw new RuntimeException("Request Failed, status code (" + statusCode + ")");
                }

                HttpEntity entity = response.getEntity();
                if (entity == null) {
                    throw new IOException("Empty response entity");
                }

                try (InputStream is = entity.getContent()) {
                    return new String(is.readAllBytes(), StandardCharsets.UTF_8);
                }
            });
        }
    }

    private HttpUriRequestBase buildRequest(String address, List<List<RexNode>> filters, int offset, Properties properties, Set<String> selectedProjectFields) throws ConvertException {
        fillCommonContext(filters, offset, properties, selectedProjectFields);

        HttpUriRequestBase request;
        String URI = address + FreeMarkerEngine.getInstance().process(connectionData.getUrl(), commonContext);
        if (Objects.requireNonNull(Method.normalizedValueOf(connectionData.getMethod())) == Method.POST) {
            request = new HttpPost(URI);
        } else {
            request = new HttpGet(URI);
        }

        request.setHeader("Content-type", "application/json");
        if (connectionData.getBody() != null) {
            request.setEntity(new StringEntity(FreeMarkerEngine.getInstance().process(connectionData.getBody(), commonContext)));
        }
        request.setConfig(RequestConfig.custom()
                .setConnectionRequestTimeout(connectionData.getConnectionTimeout(), TimeUnit.SECONDS)
                .setResponseTimeout(connectionData.getResponseTimeout(), TimeUnit.SECONDS).build());

        if (connectionData.getHeaders() != null) {
            for (Header header : connectionData.getHeaders()) {
                request.setHeader(header.getKey(), FreeMarkerEngine.getInstance().process(header.getValue(), commonContext));
            }
        }

        return request;
    }

    private void fillCommonContext(List<List<RexNode>> filters, int offset, Properties properties, Set<String> selectedProjectFields) {
        commonContext.put("offset", new SimpleNumber(offset));
        commonContext.put("limit", new SimpleNumber(connectionData.getPageSize()));
        commonContext.put("name", new SimpleScalar(getTableName()));

        addPropertiesToCommonContext(properties);
        if (!selectedProjectFields.isEmpty()) {
            addSelectedFieldsToCommonContext(selectedProjectFields);
        }
        if (!filters.isEmpty()) {
            addFiltersToCommonContext(filters);
        }

    }

    private void addPropertiesToCommonContext(Properties properties) {
        for (Map.Entry<Object, Object> entry : properties.entrySet()) {
            if (!entry.getKey().toString().equals(CalciteConnectionProperty.MODEL.camelName()) &&
                    !entry.getKey().toString().equals(CalciteConnectionProperty.FUN.camelName()) &&
                    !entry.getKey().toString().equals(CalciteConnectionProperty.CASE_SENSITIVE.camelName()) &&
                    !entry.getKey().toString().equals(CalciteConnectionProperty.QUOTED_CASING.camelName()) &&
                    !entry.getKey().toString().equals(CalciteConnectionProperty.UNQUOTED_CASING.camelName())) {
                commonContext.put(entry.getKey().toString(), new SimpleScalar(entry.getValue().toString()));
            }
        }
    }

    private void addSelectedFieldsToCommonContext(Set<String> selectedProjectFields) throws ConvertException{
        commonContext.put("projects", FreeMarkerEngine.convert(selectedProjectFields
                .stream()
                .collect(Collectors.toMap(Function.identity(), Function.identity()))));
    }

    private void addFiltersToCommonContext(List<List<RexNode>> filters) throws ConvertException {
        List<List<Map<String, TemplateModel>>> list = filters
                .stream()
                .map(group -> group.stream()
                        .map(this::convertToMap)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList()))
                .filter(group -> !group.isEmpty())
                .collect(Collectors.toList());

        commonContext.put("filters", FreeMarkerEngine.convert(list));
    }

    private Map<String, TemplateModel> convertToMap(RexNode node) throws ConvertFiltersException {
        if (!(node instanceof RexCall)) {
            return null;
        }
        RexCall call = (RexCall) node;
        SqlOperator operator = call.getOperator();
        List<RexNode> operands = call.getOperands();

        if (operands.size() != 2) {
            return null;
        }
        RexNode left = operands.get(0);
        RexNode right = operands.get(1);

        if (!(left instanceof RexInputRef)) {
            return null;
        }
        Field field = getField(((RexInputRef) left).getIndex());
        if (!field.isRequestParameter()) {
            return null;
        }

        if (!(right instanceof RexLiteral)) {
            return null;
        }
        RexLiteral literal = (RexLiteral) right;

        try {
            TemplateModel value = getTemplateModel(literal);
            setRequestValueField(field, value);
            return Map.of(
                    "name", new SimpleScalar(field.getName()),
                    "operator", new SimpleScalar(operator.getName()),
                    "value", value
            );
        } catch (TemplateModelException e) {
            throw ConvertFiltersException.buildConvertFiltersException(e);
        }
    }

    private static TemplateModel getTemplateModel(RexLiteral rexLiteral) throws TemplateModelException {
        switch (rexLiteral.getTypeName()) {
            case BOOLEAN:
                return Boolean.TRUE.equals(rexLiteral.getValueAs(Boolean.class)) ? TemplateBooleanModel.TRUE : TemplateBooleanModel.FALSE;
            case CHAR:
            case VARCHAR:
                return new SimpleScalar(rexLiteral.getValueAs(String.class));
            case TINYINT:
            case SMALLINT:
            case INTEGER:
            case BIGINT:
            case DECIMAL:
            case FLOAT:
            case REAL:
            case DOUBLE:
                return new SimpleNumber(rexLiteral.getValueAs(Number.class));
            case DATE:
                return new CalendarDate(rexLiteral.getValueAs(Calendar.class), TemplateDateModel.DATE);
            case TIME:
            case TIME_WITH_LOCAL_TIME_ZONE:
                return new CalendarDate(rexLiteral.getValueAs(Calendar.class), TemplateDateModel.TIME);
            case TIMESTAMP:
            case TIMESTAMP_WITH_LOCAL_TIME_ZONE:
                return new CalendarDate(rexLiteral.getValueAs(Calendar.class), TemplateDateModel.DATETIME);
            default:
                throw new IllegalStateException("Unexpected type: " + rexLiteral.getTypeName());
        }
    }

    private RelDataType getRelDataType(RelDataTypeFactory typeFactory) {
        return typeFactory.createStructType(fieldsMap.values()
                .stream()
                .map(field -> new Pair<>(field.getName(), field.getRelDataType()))
                .collect(Collectors.toList()));
    }

    private Set<String> getSelectedProjectFields(RelDataTypeFactory typeFactory, int[] projects) {
        RelDataType rowType = getRelDataType(typeFactory);

        return projects != null ?
                Arrays.stream(projects)
                    .mapToObj(rowType.getFieldList()::get)
                    .map(RelDataTypeField::getName)
                    .collect(Collectors.toUnmodifiableSet()) :
                rowType.getFieldList().stream()  //If * is specified, then all columns are returned.
                        .map(RelDataTypeField::getName)
                        .collect(Collectors.toUnmodifiableSet());

    }

    private boolean isSuccessfulResponse (int statusCode) {
        return (statusCode - 200 >= 0) && (statusCode - 200 < 100);
    }

    private List<List<RexNode>> convertToDnf(DataContext root, List<RexNode> filters) {
        RexBuilder rexBuilder = new RexBuilder(root.getTypeFactory());
        RexNode combinedFilter = RexUtil.composeConjunction(rexBuilder, filters);
        RexNode dnfFilter = RexUtil.toDnf(rexBuilder, combinedFilter);

        return RelOptUtil.disjunctions(dnfFilter)
                .stream()
                .map(RelOptUtil::conjunctions)
                .collect(Collectors.toList());
    }

    private void setRequestValueField(Field field, TemplateModel value) {
        if (field == null || !field.isRequestParameter()) return;

        field.setRequestValue(value);
        commonContext.put(field.getName(), value);
    }

    private Field getField(int index) {
        return new ArrayList<>(fieldsMap.values()).get(index);
    }

    class ArrayReaderImpl implements ArrayReader {
        private final String json;

        public ArrayReaderImpl(String json) {
            this.json = json;
        }

        @Override
        public JSONArray read(String path) {
            if (json == null || json.isEmpty()) {
                return null;
            }

            try {
                Object responseObj = JsonPath.read(json, path);
                if (responseObj instanceof JSONArray) {
                    return (JSONArray)responseObj;
                } else {
                    throw new RuntimeException("Response Failed, at jsonpath = " + table.getRootJsonpath() + " should be a json array or absent");
                }
            } catch (PathNotFoundException e) {
                return null;
            }
        }
    }

    static class ArrayParamReaderImpl implements ArrayParamReader {
        private final Object object;

        public ArrayParamReaderImpl(Object object) {
            this.object = object;
        }

        @Override
        public Object read(int index, String path) {
            if (object == null) {
                return null;
            }

            try {
                return JsonPath.read(object, path);
            } catch (PathNotFoundException e) {
                return null;
            }
        }
    }

}