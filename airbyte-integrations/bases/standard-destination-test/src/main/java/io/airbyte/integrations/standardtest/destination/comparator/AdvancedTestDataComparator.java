package io.airbyte.integrations.standardtest.destination.comparator;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AdvancedTestDataComparator extends BasicTestDataComparator {

    private static final Logger LOGGER = LoggerFactory.getLogger(AdvancedTestDataComparator.class);

    public static final String AIRBYTE_DATE_FORMAT = "yyyy-MM-dd";
    public static final String AIRBYTE_DATETIME_FORMAT = "yyyy-MM-dd'T'HH:mm:ss";
    public static final String AIRBYTE_DATETIME_WITH_TZ_FORMAT = "yyyy-MM-dd'T'HH:mm:ssXXX";

    @Override
    public void assertSameData(List<JsonNode> expected, List<JsonNode> actual) {
        LOGGER.info("Expected data {}", expected);
        LOGGER.info("Actual data   {}", actual);
        assertEquals(expected.size(), actual.size());
        final Iterator<JsonNode> expectedIterator = expected.iterator();
        final Iterator<JsonNode> actualIterator = actual.iterator();
        while (expectedIterator.hasNext() && actualIterator.hasNext()) {
            compareObjects(expectedIterator.next(), actualIterator.next());
        }
    }

    protected void compareObjects(final JsonNode expectedObject, final JsonNode actualObject) {
        LOGGER.info("Expected Object : {}", expectedObject);
        LOGGER.info("Actual Object   : {}", actualObject);
        if (areBothEmpty(expectedObject, actualObject)) {
            LOGGER.info("Both rows are empty.");
        } else {
            final Iterator<Map.Entry<String, JsonNode>> expectedDataIterator = expectedObject.fields();
            assertEquals(expectedObject.size(), actualObject.size(), "Unequal row size");
            while (expectedDataIterator.hasNext()) {
                final Map.Entry<String, JsonNode> expectedEntry = expectedDataIterator.next();
                final JsonNode expectedValue = expectedEntry.getValue();
                JsonNode actualValue = null;
                String key = expectedEntry.getKey();
                for (final String tmpKey : resolveIdentifier(expectedEntry.getKey())) {
                    actualValue = actualObject.get(tmpKey);
                    if (actualValue != null) {
                        key = tmpKey;
                        break;
                    }
                }
                LOGGER.info("For {} Expected {} vs Actual {}", key, expectedValue, actualValue);
                assertTrue(actualObject.has(key));
                assertSameValue(expectedValue, actualValue);
            }
        }
    }

    private boolean areBothEmpty(final JsonNode expectedData, final JsonNode actualData) {
        if (expectedData.size() == 0 && actualData.size() == 0) {
            return true;
        } else if (expectedData.size() <= 1 && actualData.size() <= 1) {
            boolean isExpectedEmpty = (expectedData.size() == 0 || expectedData.iterator().next().asText().isEmpty());
            return isExpectedEmpty && (actualData.size() == 0 || actualData.iterator().next().asText().isEmpty());
        } else {
            return false;
        }
    }

    // Allows subclasses to implement custom comparison asserts
    protected void assertSameValue(final JsonNode expectedValue, final JsonNode actualValue) {
        LOGGER.info("assertSameValue : {} vs {}", expectedValue, actualValue);

        assertTrue(compareJsonNodes(expectedValue, actualValue), "Expected value " + expectedValue + " vs Actual value " + actualValue);
    }

    protected boolean compareJsonNodes(final JsonNode expectedValue, final JsonNode actualValue) {
        if (expectedValue == null || actualValue == null)
            return expectedValue == null && actualValue == null;
        else if (expectedValue.isNumber() || expectedValue.isDouble() || expectedValue.isFloat())
            return compareNumericValues(expectedValue.asText(), actualValue.asText());
        else if (expectedValue.isBoolean())
            return compareBooleanValues(expectedValue.asText(), actualValue.asText());
        else if (isDateTimeWithTzValue(expectedValue.asText()))
            return compareDateTimeWithTzValues(expectedValue.asText(), actualValue.asText());
        else if (isDateTimeValue(expectedValue.asText()))
            return compareDateTimeValues(expectedValue.asText(), actualValue.asText());
        else if (isDateValue(expectedValue.asText()))
            return compareDateValues(expectedValue.asText(), actualValue.asText());
        else if (expectedValue.isArray() && actualValue.isArray())
            return compareArrays(expectedValue, actualValue);
        else if (expectedValue.isObject() && actualValue.isObject()) {
            compareObjects(expectedValue, actualValue);
            return true;
        } else
            return compareString(expectedValue, actualValue);
    }

    protected boolean compareString(final JsonNode expectedValue, final JsonNode actualValue) {
        return expectedValue.asText().equals(actualValue.asText());
    }

    private List<JsonNode> getArrayList(final JsonNode jsonArray) {
        List<JsonNode> result = new ArrayList<>();
        jsonArray.elements().forEachRemaining(result::add);
        return result;
    }

    protected boolean compareArrays(final JsonNode expectedArray, final JsonNode actualArray) {
        var expectedList = getArrayList(expectedArray);
        var actualList = getArrayList(actualArray);

        if (expectedList.size() != actualList.size()) {
            return false;
        } else {
            boolean result = false;
            for (JsonNode expectedNode : expectedList) {
                for (JsonNode actualNode : actualList) {
                    result = compareJsonNodes(expectedNode, actualNode) || result;
                }
            }
            return result;
        }
    }

    protected boolean compareBooleanValues(final String firstNumericValue, final String secondNumericValue) {
        return Boolean.valueOf(firstNumericValue) == Boolean.valueOf(secondNumericValue);
    }

    protected boolean compareNumericValues(final String firstNumericValue, final String secondNumericValue) {
        double firstValue = Double.parseDouble(firstNumericValue);
        double secondValue = Double.parseDouble(secondNumericValue);

        return firstValue == secondValue;
    }

    protected DateTimeFormatter getAirbyteDateTimeWithTzFormatter() {
        return DateTimeFormatter.ofPattern(AIRBYTE_DATETIME_WITH_TZ_FORMAT);
    }

    protected boolean isDateTimeWithTzValue(final String value) {
        return value.matches(".+[+-]\\d{2}:\\d{2}");
    }

    protected ZonedDateTime parseDestinationDateWithTz(final String destinationValue) {
        return ZonedDateTime.parse(destinationValue, DateTimeFormatter.ofPattern(AIRBYTE_DATETIME_WITH_TZ_FORMAT)).withZoneSameInstant(ZoneOffset.UTC);
    }

    protected boolean compareDateTimeWithTzValues(final String airbyteMessageValue, final String destinationValue) {
        try {
            ZonedDateTime airbyteDate = ZonedDateTime.parse(airbyteMessageValue, getAirbyteDateTimeWithTzFormatter()).withZoneSameInstant(ZoneOffset.UTC);
            ZonedDateTime destinationDate = parseDestinationDateWithTz(destinationValue);
            return airbyteDate.equals(destinationDate);
        } catch (DateTimeParseException e) {
            LOGGER.warn("Fail to convert values to ZonedDateTime. Try to compare as text. Airbyte value({}), Destination value ({}). Exception: {}",
                    airbyteMessageValue, destinationValue, e);
            return compareTextValues(airbyteMessageValue, destinationValue);
        }
    }

    protected boolean isDateTimeValue(final String value) {
        return value.matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}");
    }

    protected boolean compareDateTimeValues(final String airbyteMessageValue, final String destinationValue) {
        return compareTextValues(airbyteMessageValue, destinationValue);
    }

    protected boolean isDateValue(final String value) {
        return value.matches("\\d{4}-\\d{2}-\\d{2}");
    }

    protected boolean compareDateValues(final String airbyteMessageValue, final String destinationValue) {
        return compareTextValues(airbyteMessageValue, destinationValue);
    }

    protected boolean compareTextValues(final String firstValue, final String secondValue) {
        return firstValue.equals(secondValue);
    }
}
