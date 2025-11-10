package org.apache.calcite.adapter.restapi.freemarker;

import freemarker.core.*;
import freemarker.template.TemplateDateModel;

import java.text.ParseException;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class XmlTemplateDateFormatFactory extends TemplateDateFormatFactory {

    public static final XmlTemplateDateFormatFactory INSTANCE = new XmlTemplateDateFormatFactory();

    private XmlTemplateDateFormatFactory() {
    }

    @Override
    public TemplateDateFormat get(String params, int dateType,
                                  Locale locale, TimeZone timeZone, boolean zoneLessInput,
                                  Environment env)
            throws InvalidFormatParametersException {
        TemplateFormatUtil.checkHasNoParameters(params);
        return XmlTemplateDateFormat.INSTANCE;
    }

    private static class XmlTemplateDateFormat extends TemplateDateFormat {

        private static final XmlTemplateDateFormat INSTANCE = new XmlTemplateDateFormat();

        private XmlTemplateDateFormat() {
        }

        @Override
        public String formatToPlainText(TemplateDateModel dateModel) {
            return dateModel.toString();
        }

        @Override
        public boolean isLocaleBound() {
            return false;
        }

        @Override
        public boolean isTimeZoneBound() {
            return false;
        }

        @Override
        public Date parse(String s, int dateType) throws UnparsableValueException {
            try {
                return CalendarDate.parse(s, dateType);
            } catch (ParseException ex) {
                throw new UnparsableValueException("", ex);
            }
        }

        @Override
        public String getDescription() {
            return "xml format";
        }

    }

}