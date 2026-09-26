package com.pokerlab.solver;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/** Deterministic JSON, gzip transport and full-content identity for an exact flop pack. */
public final class FlopTurnRiverPackJson {
    private static final int MAX_DECOMPRESSED_BYTES = 64 * 1024 * 1024;
    private static final JsonMapper MAPPER =
            JsonMapper.builder()
                    .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
                    .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                    .build();

    private FlopTurnRiverPackJson() {}

    public static String write(FlopTurnRiverSolutionPack pack) {
        pack.validate();
        try {
            return MAPPER.writeValueAsString(pack);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Could not serialize flop pack", exception);
        }
    }

    public static FlopTurnRiverSolutionPack read(String json) {
        try {
            FlopTurnRiverSolutionPack pack =
                    MAPPER.readValue(json, FlopTurnRiverSolutionPack.class);
            pack.validate();
            return pack;
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Could not parse flop pack", exception);
        }
    }

    public static byte[] gzip(FlopTurnRiverSolutionPack pack) {
        byte[] json = write(pack).getBytes(StandardCharsets.UTF_8);
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            try (GZIPOutputStream gzip = new GZIPOutputStream(output)) {
                gzip.write(json);
            }
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalArgumentException("Could not compress flop pack", exception);
        }
    }

    public static FlopTurnRiverSolutionPack gunzip(byte[] compressed) {
        try (GZIPInputStream input = new GZIPInputStream(new ByteArrayInputStream(compressed))) {
            byte[] json = input.readNBytes(MAX_DECOMPRESSED_BYTES + 1);
            if (json.length > MAX_DECOMPRESSED_BYTES)
                throw new IllegalArgumentException("Flop pack exceeds decompressed size limit");
            return read(new String(json, StandardCharsets.UTF_8));
        } catch (IOException exception) {
            throw new IllegalArgumentException("Could not decompress flop pack", exception);
        }
    }

    public static String contentHash(FlopTurnRiverSolutionPack pack) {
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
