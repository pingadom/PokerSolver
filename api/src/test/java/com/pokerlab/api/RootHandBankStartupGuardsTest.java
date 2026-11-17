package com.pokerlab.api;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Modifier;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class RootHandBankStartupGuardsTest {
    @Test
    void explicitSourceNamesAreStrictAndNeverFallBack() {
        for (String v :
                Arrays.asList(
                        null, "", "original", "PHYSICAL", " ORIGINAL", "PHYSICAL_PARENT_REUSE "))
            assertThrows(
                    IllegalStateException.class,
                    () -> RootHandBankStartupSource.load("absent.json", "pin", v));
        assertEquals(
                RootHandBankStartupSource.Mode.ORIGINAL,
                RootHandBankStartupSource.mode("ORIGINAL"));
        assertEquals(
                RootHandBankStartupSource.Mode.PHYSICAL_PARENT_REUSE,
                RootHandBankStartupSource.mode("PHYSICAL_PARENT_REUSE"));
    }

    @Test
    void bothModesRequireExactPublicPinBeforeOpeningFiles() {
        for (String mode : List.of("ORIGINAL", "PHYSICAL_PARENT_REUSE")) {
            assertThrows(
                    IllegalStateException.class,
                    () -> RootHandBankStartupSource.load("", "pin", mode));
            assertThrows(
                    IllegalStateException.class,
                    () -> RootHandBankStartupSource.load("file", "", mode));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> RootHandBankStartupSource.load("absent.json", "0".repeat(64), mode));
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            RootHandBankStartupSource.load(
                                    "absent.json",
                                    com.pokerlab.solver.SixMaxPhysicalRootBankDrillLoader
                                            .PHYSICAL_STUDY_HASH,
                                    mode));
        }
    }

    @Test
    void sourceCapabilityCannotBeConstructedFromPublishedMetadata() {
        assertTrue(
                Arrays.stream(RootHandBankStartupSource.class.getDeclaredConstructors())
                        .allMatch(c -> Modifier.isPrivate(c.getModifiers())));
        assertTrue(
                Arrays.stream(RootHandBankStartupSource.class.getDeclaredMethods())
                        .filter(m -> m.getName().equals("load"))
                        .allMatch(
                                m ->
                                        Arrays.equals(
                                                m.getParameterTypes(),
                                                new Class<?>[] {
                                                    String.class, String.class, String.class
                                                })));
    }

    @Test
    void mismatchedManifestCannotSelectASecondLoader() {
        String pin = com.pokerlab.solver.SixMaxRootHandBankDrillLoader.STUDY_HASH;
        assertThrows(
                Exception.class,
                () ->
                        RootHandBankStartupSource.load(
                                "../docs/data/supplied-physical-root-bank-drill-manifest.json",
                                pin,
                                "ORIGINAL"));
        assertThrows(
                Exception.class,
                () ->
                        RootHandBankStartupSource.load(
                                "../docs/data/supplied-root-hand-bank-drill-manifest.json",
                                pin,
                                "PHYSICAL_PARENT_REUSE"));
    }
}
