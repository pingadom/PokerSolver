package com.pokerlab.solver;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;

/** Deterministic JSON transport for the bounded preflop pack contract. */
public final class PreflopPackJson {
    private static final JsonMapper MAPPER =
            JsonMapper.builder()
                    .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
                    .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                    .build();

    private PreflopPackJson() {}

    public static String write(PreflopSolutionPack pack) {
        pack.validate();
        try {
            return MAPPER.writeValueAsString(pack);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Could not serialize preflop pack", exception);
        }
    }

    public static PreflopSolutionPack read(String json) {
        try {
            PreflopSolutionPack pack = MAPPER.readValue(json, PreflopSolutionPack.class);
            pack.validate();
            return pack;
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Could not parse preflop pack", exception);
        }
    }
}
