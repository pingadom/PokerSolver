package com.pokerlab.solver;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

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

    /** The whole saved artifact, including payoffs and strategy, identifies a drill version. */
    public static String contentHash(PreflopSolutionPack pack) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(write(pack).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by Java", exception);
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
