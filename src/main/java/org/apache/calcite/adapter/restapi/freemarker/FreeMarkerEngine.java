package org.apache.calcite.adapter.restapi.freemarker;

import org.apache.calcite.adapter.restapi.freemarker.exception.ConvertException;
import org.apache.calcite.adapter.restapi.freemarker.exception.FreeMarkerException;
import org.apache.calcite.adapter.restapi.freemarker.exception.FreeMarkerFormatException;
import freemarker.core.TemplateDateFormatFactory;
import freemarker.core.TemplateNumberFormatFactory;
import freemarker.template.*;
import lombok.Getter;

import java.io.IOException;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;

public class FreeMarkerEngine {

    @Getter
    private static final FreeMarkerEngine instance = new FreeMarkerEngine();
    private static final Configuration cfg = new Configuration(new Version("2.3.28"));

    private String fmFunctions;

    public static void init() {
        cfg.setBooleanFormat("c");

//        cfg.setSharedVariable("tail", new ShowFilter());
        cfg.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
        cfg.setLogTemplateExceptions(false);
        cfg.setWrapUncheckedExceptions(true);

        Map<String, TemplateDateFormatFactory> templateDateFormatFactoryMap = new HashMap<>();
        templateDateFormatFactoryMap.put("xml", XmlTemplateDateFormatFactory.INSTANCE);
        cfg.setCustomDateFormats(templateDateFormatFactoryMap);
        cfg.setDateFormat("@xml");
        cfg.setDateTimeFormat("@xml");
        cfg.setTimeFormat("@xml");

        Map<String, TemplateNumberFormatFactory> templateNumberFormatFactoryMap = new HashMap<>();
        templateNumberFormatFactoryMap.put("java", JavaTemplateNumberFormatFactory.INSTANCE);
        cfg.setCustomNumberFormats(templateNumberFormatFactoryMap);
        cfg.setNumberFormat("@java");
    }

    public static void setSharedVariable(String name, TemplateModel tm) {
        cfg.setSharedVariable(name, tm);
    }

    public String process(String template, Map<String, TemplateModel> variables)
            throws FreeMarkerFormatException {
        StringWriter stringWriter = new StringWriter();
        try {
            getTemplate(template).process(variables, stringWriter);
        } catch (IOException | TemplateException ex) {
            throw new FreeMarkerFormatException(ex.getMessage());
        }
        return stringWriter.toString().trim();
    }

    public Template getTemplate(String templateText) {
        try {
            return new Template("freemarker", templateText, cfg);
        } catch (IOException e) {
            throw new FreeMarkerException(e.getMessage(), e);
        }
    }

    public static TemplateModel convert(Object value) throws ConvertException {
        try {
            return cfg.getObjectWrapper().wrap(value);
        } catch (TemplateModelException e) {
            throw ConvertException.buildConvertException(e);
        }
    }

}