package com.pokerlab.solver;

import static com.pokerlab.solver.FiniteTwoPlayerSequenceForm.*;
import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class NegativeReachBehaviorTest {
    @Test
    void missingActionsAndForeignRecallCannotCreateAPartialPolicy() {
        var source = chain(2);
        for (boolean foreignHistory : List.of(false, true)) {
            var infos = new ArrayList<>(source.infos());
            var info = infos.get(1);
            infos.set(
                    1,
                    new InformationSet(
                            info.key(),
                            info.actor(),
                            info.depth(),
                            foreignHistory ? info.actions() : List.of("go", "stop", "foreign"),
                            foreignHistory
                                    ? List.of(new OwnAction("missing", "go"))
                                    : info.ownHistory()));
            var flow =
                    new Flow(
                            source.actor(),
                            infos,
                            source.indices(),
                            source.sequences(),
                            source.conservation(),
                            source.matrix(),
                            source.rhs());
            var strategy = new LinkedHashMap<String, Map<String, Double>>();
            assertThrows(
                    IllegalStateException.class,
                    () ->
                            NegativeReachBehavior.reconstruct(
                                    flow,
                                    List.of(1., .5, .5, .5, 0.),
                                    strategy,
                                    new SequenceFormAffineProjection.Budget()));
            assertTrue(strategy.isEmpty());
        }
    }

    static Flow chain(int depth) {
        var infos = new ArrayList<InformationSet>();
        var sequences = new ArrayList<Sequence>();
        var conservation = new ArrayList<Conservation>();
        var indices = new HashMap<OwnAction, Integer>();
        var history = new ArrayList<OwnAction>();
        sequences.add(new Sequence(0, "", ""));
        int parent = 0;
        for (int i = 0; i < depth; i++) {
            String key = "1:step" + i;
            infos.add(new InformationSet(key, 1, i, List.of("go", "stop"), history));
            var children = new ArrayList<Integer>();
            for (String action : List.of("go", "stop")) {
                int index = sequences.size();
                sequences.add(new Sequence(index, key, action));
                indices.put(new OwnAction(key, action), index);
                children.add(index);
            }
            conservation.add(new Conservation(key, parent, children));
            parent = children.getFirst();
            history.add(new OwnAction(key, "go"));
        }
        return new Flow(1, infos, indices, sequences, conservation, new double[0][], new double[0]);
    }

    @Test
    void tinyMassUsesChildSumAndZeroMassHasADeterministicUnreachablePolicy() {
        var flow = chain(3);
        var point =
                List.of(
                        1.,
                        3.066424196519426e-10,
                        1. - 3.066424196519426e-10,
                        3.0664245615488384e-10,
                        0.,
                        0.,
                        3.0664245615488384e-10);
        assertThrows(
                IllegalStateException.class, () -> behavior(flow, point, new LinkedHashMap<>()));
        var policy = new LinkedHashMap<String, Map<String, Double>>();
        var budget = new SequenceFormAffineProjection.Budget();
        var audit = NegativeReachBehavior.reconstruct(flow, point, policy, budget);
        assertEquals(1., policy.get("1:step1").get("go"));
        assertEquals(0., policy.get("1:step2").get("go"));
        assertTrue(audit.maximumResidual() < 1e-15);
        assertTrue(budget.audit().chargedUnits() > 0);
        var zero = List.of(1., 0., 1., 0., 0., 0., 0.);
        NegativeReachBehavior.reconstruct(flow, zero, policy, budget);
        assertEquals(Map.of("go", .5, "stop", .5), policy.get("1:step1"));
        assertThrows(UnsupportedOperationException.class, () -> policy.get("1:step1").clear());
    }

    @Test
    void materialNegativeNonfiniteRootAndConservationErrorsRejectWithoutMutation() {
        var flow = chain(2);
        for (var point :
                List.of(
                        List.of(1., .5, .5, .5, .1),
                        List.of(.9, .5, .4, .5, 0.),
                        List.of(1., .5, .5, -.001, .501),
                        List.of(1., .5, .5, Double.NaN, 0.),
                        List.of(1., .5, .5, Double.POSITIVE_INFINITY, 0.),
                        List.of(1., .5, .5),
                        List.of(1., 1.1, -.1, 1.1, 0.))) {
            var strategy = new LinkedHashMap<String, Map<String, Double>>();
            strategy.put("keep", Map.of("action", 1.));
            assertThrows(
                    IllegalStateException.class,
                    () ->
                            NegativeReachBehavior.reconstruct(
                                    flow,
                                    point,
                                    strategy,
                                    new SequenceFormAffineProjection.Budget()));
            assertEquals(Map.of("keep", Map.of("action", 1.)), strategy);
        }
    }

    @Test
    void accumulatedPerfectRecallErrorCannotPassOnLocalConservationAlone() {
        var failure =
                assertThrows(
                        IllegalStateException.class,
                        () ->
                                NegativeReachBehavior.reconstruct(
                                        chain(3),
                                        List.of(1., 1., 0., 1. - 9e-9, 0., 1. - 18e-9, 0.),
                                        new LinkedHashMap<>(),
                                        new SequenceFormAffineProjection.Budget()));
        assertEquals("Rejected policy changes original realization", failure.getMessage());
        assertThrows(
                FiniteTwoPlayerAffineSequenceForm.Rejected.class,
                () ->
                        NegativeReachBehavior.reconstruct(
                                chain(1),
                                List.of(1., .5, .5),
                                new LinkedHashMap<>(),
                                new SequenceFormAffineProjection.Budget(0)));
    }

    @Test
    void originalCapturedSeventyNineByEightyTwoLpReproducesRatioBoundary() throws Exception {
        var mapper = SixMaxTexturePayoffTable.mapper();
        var saved =
                mapper.readTree(
                        Files.readAllBytes(
                                Path.of("../docs/data/captured-near-zero-minimum-reach-lp.json")));
        assertEquals(79, saved.path("cost").size());
        assertEquals(82, saved.path("rhs").size());
        var baseline =
                mapper.treeToValue(
                        saved.path("baseline"), FiniteTwoPlayerAffineSequenceForm.Audit.class);
        var originalFlow = baseline.secondFlow();
        var infos = baseline.informationSets().stream().filter(i -> i.actor() == 5).toList();
        var indices = new HashMap<OwnAction, Integer>();
        for (var sequence : originalFlow.sequences())
            if (sequence.index() > 0)
                indices.put(new OwnAction(sequence.key(), sequence.action()), sequence.index());
        var flow =
                new Flow(
                        5,
                        infos,
                        indices,
                        originalFlow.sequences(),
                        originalFlow.conservation(),
                        new double[0][],
                        new double[0]);
        var projection = baseline.secondProjection();
        var point = new ArrayList<Double>();
        for (int i = 0; i < projection.offset().size(); i++) {
            double value = projection.offset().get(i);
            for (int j = 0; j < projection.independentSequences().size(); j++)
                value +=
                        projection.transform().get(i).get(j)
                                * saved.path("solved").path("point").get(j).doubleValue();
            point.add(Math.max(0, value));
        }
        var failure =
                assertThrows(
                        IllegalStateException.class,
                        () -> behavior(flow, point, new LinkedHashMap<>()));
        assertEquals("Invalid behavioral ratio", failure.getMessage());
        var strategy = new LinkedHashMap<String, Map<String, Double>>();
        var audit =
                NegativeReachBehavior.reconstruct(
                        flow, point, strategy, new SequenceFormAffineProjection.Budget());
        assertEquals(3.650294126530209e-17, audit.maximumResidual());
        assertEquals(33, strategy.size());
    }
}
