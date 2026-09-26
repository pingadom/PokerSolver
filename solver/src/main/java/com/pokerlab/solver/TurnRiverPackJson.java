package com.pokerlab.solver;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Deterministic JSON and full-content identity for turn-to-river solution packs. */
public final class TurnRiverPackJson {
    private static final JsonMapper MAPPER =
            JsonMapper.builder()
                    .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
                    .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                    .build();

    private TurnRiverPackJson() {}

    public static String write(TurnRiverSolutionPack pack) {
        pack.validate();
        try {
            return MAPPER.writeValueAsString(pack);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Could not serialize turn-river pack", exception);
        }
    }

    public static TurnRiverSolutionPack read(String json) {
        try {
            TurnRiverSolutionPack pack = MAPPER.readValue(json, TurnRiverSolutionPack.class);
            pack.validate();
            return pack;
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Could not parse turn-river pack", exception);
        }
    }

    public static String contentHash(TurnRiverSolutionPack pack) {
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
