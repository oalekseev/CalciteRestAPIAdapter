package org.apache.calcite.adapter.restapi.freemarker;

import freemarker.core.*;
import freemarker.template.TemplateModelException;
import freemarker.template.TemplateNumberModel;

import java.util.Locale;

public class JavaTemplateNumberFormatFactory extends TemplateNumberFormatFactory {

    public static final JavaTemplateNumberFormatFactory INSTANCE
            = new JavaTemplateNumberFormatFactory();

    private JavaTemplateNumberFormatFactory() {
        // Defined to decrease visibility
    }

    @Override
    public TemplateNumberFormat get(String params, Locale locale, Environment env)
            throws InvalidFormatParametersException {
        TemplateFormatUtil.checkHasNoParameters(params);
        return HexTemplateNumberFormat.INSTANCE;
    }

    private static class HexTemplateNumberFormat extends TemplateNumberFormat {

        private static final HexTemplateNumberFormat INSTANCE = new HexTemplateNumberFormat();

        private HexTemplateNumberFormat() { }

        @Override
        public String formatToPlainText(TemplateNumberModel numberModel)
                throws UnformattableValueException, TemplateModelException {
            Number n = TemplateFormatUtil.getNonNullNumber(numberModel);
            try {
                return n.toString();
            } catch (ArithmeticException e) {
                throw new UnformattableValueException(n + " doesn't fit into an number");
            }
        }

        @Override
        public boolean isLocaleBound() {
            return false;
        }

        @Override
        public String getDescription() {
            return "java string representation of number";
        }

    }

}