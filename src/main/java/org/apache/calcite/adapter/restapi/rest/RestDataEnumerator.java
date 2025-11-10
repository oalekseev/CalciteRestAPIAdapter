package org.apache.calcite.adapter.restapi.rest;

import org.apache.calcite.adapter.restapi.rest.interfaces.RestIterator;
import freemarker.template.TemplateBooleanModel;
import freemarker.template.TemplateModel;
import org.apache.calcite.avatica.util.DateTimeUtils;
import org.apache.calcite.linq4j.Enumerator;
import org.apache.commons.lang3.time.FastDateFormat;

import java.text.ParseException;
import java.util.*;

import static org.apache.calcite.adapter.restapi.rest.RestFieldType.STRING;

public class RestDataEnumerator implements Enumerator<Object[]> {

    private static final TimeZone gmt = TimeZone.getTimeZone("GMT");
    private static final FastDateFormat TIME_FORMAT_DATE = FastDateFormat.getInstance("yyyy-MM-dd", gmt);
    private static final FastDateFormat TIME_FORMAT_TIME = FastDateFormat.getInstance("HH:mm:ss", gmt);
    private static final FastDateFormat TIME_FORMAT_TIMESTAMP = FastDateFormat.getInstance("yyyy-MM-dd'T'HH:mm:ss", gmt);

    private List<ResponseRowReader> rows;
    private int index = -1;
    private ResponseRowReader current;
    private boolean isClosed = false;
    private final Map<String, Field> fieldsMap;

    RestIterator restIterator;

    public RestDataEnumerator(List<ResponseRowReader> results, Map<String, Field> fieldsMap, int[] projects, RestIterator restIterator) {
        this.rows = results;
        this.restIterator = restIterator;
        this.fieldsMap = getProjectFields(projects, fieldsMap);
    }

    private ResponseRowReader getNextRow() {
        return rows.get(++index);
    }

    @Override
    public Object[] current() {
        final Object[] objects = new Object[fieldsMap.size()];
        int i = 0;
        for (Field field : fieldsMap.values()) {
             if (field.isResponseParameter()) {
                objects[i++] = convert(field.getRestFieldType(), current.getArrayParamReader().read(index, field.getJsonpath()));
            } else if (field.isRequestParameter()) {
                objects[i++] = convert(field.getRestFieldType(), field.getRequestValue());
            }
        }

        return objects;
    }

    @Override
    public boolean moveNext() {
        if ((index + 1) == rows.size()) {
            rows = restIterator.getMore();
            if (rows.isEmpty()) {
                return false;
            }
            index = -1;
        }
        current = getNextRow();
        return true;
    }

    @Override
    public void reset() {
        current = null;
    }

    @Override
    public void close() {
        isClosed = true;
    }

    protected Object convert(RestFieldType fieldType, Object object) {
        if (object == null) {
            return null;
        }
        if (fieldType == null) {
            return object.toString();
        }
        if (!fieldType.equals(STRING) && object.toString().isEmpty()) {
            return null;
        }

        try {
            switch (fieldType) {
                case BOOLEAN:
                    if (object instanceof TemplateModel) {
                        return object.equals(TemplateBooleanModel.TRUE);
                    }
                    return Boolean.parseBoolean(object.toString());
                case BYTE:
                    return Byte.parseByte(object.toString());
                case SHORT:
                    return Short.parseShort(object.toString());
                case INT:
                    return Integer.parseInt(object.toString());
                case LONG:
                    return Long.parseLong(object.toString());
                case FLOAT:
                    return Float.parseFloat(object.toString());
                case DOUBLE:
                    return Double.parseDouble(object.toString());
                case DATE:
                    return (int)(TIME_FORMAT_DATE.parse(object.toString()).getTime() / DateTimeUtils.MILLIS_PER_DAY);
                case TIME:
                    return (int)TIME_FORMAT_TIME.parse(object.toString()).getTime();
                case TIMESTAMP:
                    return TIME_FORMAT_TIMESTAMP.parse(object.toString()).getTime();
                case UUID:
                    return UUID.fromString(object.toString());
                case STRING:
                default:
                    return object.toString();
            }
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }
    }

    private Map<String, Field> getProjectFields(int[] projects, Map<String, Field> fieldsMap) {
        if (projects == null) return fieldsMap;

        List<Field> arrayFields = new ArrayList<>(fieldsMap.values());
        Map<String, Field> projectFieldsMap = new LinkedHashMap<>(arrayFields.size());
        for (int index: projects) {
            Field field = arrayFields.get(index);
            projectFieldsMap.put(field.getName(), field);
        }
        return projectFieldsMap;
    }

}