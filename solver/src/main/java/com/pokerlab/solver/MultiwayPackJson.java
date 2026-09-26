package com.pokerlab.solver;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;

/** Strict deterministic JSON for offline multiway spots and solution artifacts. */
public final class MultiwayPackJson {
    private static final JsonMapper MAPPER =
            JsonMapper.builder()
                    .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
                    .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
                    .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
                    .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                    .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
                    .enable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
                    .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                    .build();

    private MultiwayPackJson() {}

    public static String write(MultiwaySolutionPack pack) {
        if (pack == null) throw new IllegalArgumentException("Pack is required");
        pack.validate();
        return serialize(pack);
    }

    public static MultiwaySolutionPack read(String json) {
        MultiwaySolutionPack pack = deserialize(json, MultiwaySolutionPack.class);
        pack.validate();
        return pack;
    }

    public static String contentHash(MultiwaySolutionPack pack) {
        return MultiwayCallSpot.sha256(write(pack));
    }

    public static String writeSpot(MultiwayCallSpot spot) {
        if (spot == null) throw new IllegalArgumentException("Spot is required");
        return serialize(spot);
    }

    public static MultiwayCallSpot readSpot(String json) {
        return deserialize(json, MultiwayCallSpot.class);
    }

    public static String writeSidePot(MultiwaySidePotPack pack) {
        if (pack == null) throw new IllegalArgumentException("Pack is required");
        pack.validate();
        return serialize(pack);
    }

    public static MultiwaySidePotPack readSidePot(String json) {
        MultiwaySidePotPack pack = deserialize(json, MultiwaySidePotPack.class);
        pack.validate();
        return pack;
    }

    public static String sidePotContentHash(MultiwaySidePotPack pack) {
        return MultiwayCallSpot.sha256(writeSidePot(pack));
    }

    public static String writeSidePotSpot(MultiwaySidePotSpot spot) {
        if (spot == null) throw new IllegalArgumentException("Spot is required");
        return serialize(spot);
    }

    public static MultiwaySidePotSpot readSidePotSpot(String json) {
        return deserialize(json, MultiwaySidePotSpot.class);
    }

    private static String serialize(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Could not serialize multiway artifact", exception);
        }
    }

    private static <T> T deserialize(String json, Class<T> type) {
        if (json == null) throw new IllegalArgumentException("JSON is required");
        try {
            T value = MAPPER.readValue(json, type);
            if (value == null) throw new IllegalArgumentException("Artifact is required");
            return value;
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Could not parse multiway artifact", exception);
        }
    }
}
