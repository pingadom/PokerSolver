package com.pokerlab.solver;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Adds an explicit schedule without changing the canonical JSON of historical global rules. */
public final class SixMaxPreflopRulesJson {
    private SixMaxPreflopRulesJson() {}

    public static final class Serializer extends JsonSerializer<SixMaxPreflopBetting.Rules> {
        @Override
        public void serialize(
                SixMaxPreflopBetting.Rules rules, JsonGenerator output, SerializerProvider provider)
                throws IOException {
            output.writeStartObject();
            if (rules.raiseSchedule() != SixMaxPreflopBetting.RaiseSchedule.GLOBAL_TARGETS)
                output.writeStringField("raiseSchedule", rules.raiseSchedule().name());
            output.writeArrayFieldStart("raiseToBb");
            for (double target : rules.raiseToBb()) output.writeNumber(target);
            output.writeEndArray();
            output.writeNumberField("smallBlindBb", rules.smallBlindBb());
            output.writeNumberField("stackBb", rules.stackBb());
            output.writeEndObject();
        }
    }

    public static final class Deserializer extends JsonDeserializer<SixMaxPreflopBetting.Rules> {
        @Override
        public SixMaxPreflopBetting.Rules deserialize(
                JsonParser input, DeserializationContext context) throws IOException {
            if (!input.isExpectedStartObjectToken())
                throw invalid(input, "Rules must be an object");
            Set<String> seen = new HashSet<>();
            Double stack = null;
            Double smallBlind = null;
            List<Double> targets = null;
            var schedule = SixMaxPreflopBetting.RaiseSchedule.GLOBAL_TARGETS;
            while (input.nextToken() != JsonToken.END_OBJECT) {
                if (input.currentToken() != JsonToken.FIELD_NAME)
                    throw invalid(input, "Expected a rule field");
                String field = input.currentName();
                if (!seen.add(field)) throw invalid(input, "Duplicate rule field: " + field);
                input.nextToken();
                switch (field) {
                    case "stackBb" -> stack = number(input);
                    case "smallBlindBb" -> smallBlind = number(input);
                    case "raiseToBb" -> {
                        if (!input.isExpectedStartArrayToken())
                            throw invalid(input, "raiseToBb must be a numeric array");
                        targets = new ArrayList<>();
                        while (input.nextToken() != JsonToken.END_ARRAY) targets.add(number(input));
                    }
                    case "raiseSchedule" -> {
                        if (input.currentToken() != JsonToken.VALUE_STRING)
                            throw invalid(input, "raiseSchedule must be a supported name");
                        try {
                            schedule = SixMaxPreflopBetting.RaiseSchedule.valueOf(input.getText());
                        } catch (IllegalArgumentException exception) {
                            throw invalid(input, "Unknown raiseSchedule");
                        }
                    }
                    default -> throw invalid(input, "Unknown rule field: " + field);
                }
            }
            if (stack == null || smallBlind == null || targets == null)
                throw invalid(input, "stackBb, smallBlindBb and raiseToBb are required");
            var rules = new SixMaxPreflopBetting.Rules(stack, smallBlind, targets, schedule);
            try {
                new SixMaxPreflopBetting(rules);
            } catch (IllegalArgumentException exception) {
                throw invalid(input, exception.getMessage());
            }
            return rules;
        }

        @Override
        public SixMaxPreflopBetting.Rules getNullValue(DeserializationContext context)
                throws JsonMappingException {
            throw invalid(context.getParser(), "Rules are required");
        }

        private static double number(JsonParser input) throws IOException {
            if (input.currentToken() == null || !input.currentToken().isNumeric())
                throw invalid(input, "Chip amounts must be numbers");
            double amount = input.getDoubleValue();
            if (!Double.isFinite(amount)) throw invalid(input, "Chip amounts must be finite");
            return amount;
        }

        private static JsonMappingException invalid(JsonParser input, String message) {
            return JsonMappingException.from(input, message);
        }
    }
}
