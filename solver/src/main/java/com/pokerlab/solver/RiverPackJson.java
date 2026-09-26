package com.pokerlab.solver;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Deterministic JSON and full-content identity for the bounded river pack. */
public final class RiverPackJson {
    private static final JsonMapper MAPPER =
            JsonMapper.builder()
                    .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
                    .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                    .build();

    private RiverPackJson() {}

    public static String write(RiverSolutionPack pack) {
        pack.validate();
        try {
            return MAPPER.writeValueAsString(pack);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Could not serialize river pack", exception);
        }
    }

    public static RiverSolutionPack read(String json) {
        try {
            RiverSolutionPack pack = MAPPER.readValue(json, RiverSolutionPack.class);
            pack.validate();
            return pack;
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Could not parse river pack", exception);
        }
    }

    public static String contentHash(RiverSolutionPack pack) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(write(pack).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by Java", exception);
        }
    }
}
