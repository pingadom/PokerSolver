package com.pokerlab.solver;

import static com.pokerlab.solver.FiniteTwoPlayerRootSecurityEnvelope.Question;

import java.util.*;

/** A conservative full-menu root batch sharing one owned parent and cumulative endpoint limits. */
public final class FiniteTwoPlayerRootSecurityBatch {
    public static final String ALGORITHM = "ROOT_SECURITY_ENVELOPE_SHARED_OWNED_PARENT_BATCH/v1";
    public static final String SCOPE =
            "CONSERVATIVE_FULL_MENU_OUTER_BOUNDS_NOT_ATTAINED_ENDPOINTS_OR_TRAINER_ADMISSION/v1";
    public static final int MAX_QUESTIONS = 8;

    public record Work(
            int parentSolveCalls,
            int parentLpSolves,
            long parentCompilerUnits,
            long parentLpArithmeticWork,
            long preflightCompilerUnits,
            long compilerUnits,
            long compilerLimit,
            int endpointLpSolves,
            int endpointPivots,
            int endpointPivotLimit,
            long endpointArithmeticWork,
            long endpointArithmeticLimit,
            int enumeratedContinuationPlans) {}

    public record Audit(
            String algorithm,
            String scope,
            boolean trainerAdmission,
            String snapshotHash,
            FiniteTwoPlayerAffineSequenceForm.Audit parent,
            FiniteTwoPlayerAffineSequenceForm.LpWitness firstParentWitness,
            FiniteTwoPlayerAffineSequenceForm.LpWitness secondParentWitness,
            List<FiniteTwoPlayerRootSecurityEnvelope.Audit> envelopes,
            Work work) {
        public Audit {
            envelopes = List.copyOf(envelopes);
        }
    }

    /** Only a complete successful owned batch can construct this handle. */
    public static final class Result {
        private final Audit audit;

        private Result(Audit audit) {
            this.audit = audit;
        }

        public Audit audit() {
            return audit;
        }
    }

    private FiniteTwoPlayerRootSecurityBatch() {}

    public static <S> Result solve(MultiPlayerCfrGame<S> game, List<Question> questions)
            throws Exception {
        return solve(
                game,
                questions,
                FiniteTwoPlayerAffineSequenceForm.MAX_REDUCTION_WORK,
                BoundedLinearProgram.MAX_PIVOTS,
                BoundedLinearProgram.MAX_ARITHMETIC_WORK);
    }

    // Controls may reduce the original cumulative endpoint limits, never increase them.
    static <S> Result solve(
            MultiPlayerCfrGame<S> game,
            List<Question> questions,
            long compilerLimit,
            int pivotLimit,
            long arithmeticLimit)
            throws Exception {
        Objects.requireNonNull(questions, "questions");
        if (questions.isEmpty() || questions.size() > MAX_QUESTIONS)
            throw new IllegalArgumentException("One to eight distinct root questions required");
        var captured = List.copyOf(questions);
        if (captured.isEmpty()
                || captured.size() > MAX_QUESTIONS
                || new HashSet<>(captured).size() != captured.size())
            throw new IllegalArgumentException("One to eight distinct root questions required");
        if (pivotLimit < 0
                || pivotLimit > BoundedLinearProgram.MAX_PIVOTS
                || arithmeticLimit < 0
                || arithmeticLimit > BoundedLinearProgram.MAX_ARITHMETIC_WORK)
            throw new IllegalArgumentException("Original endpoint limits required");
        var budget = new SequenceFormAffineProjection.Budget(compilerLimit);
        var checked = FiniteTwoPlayerSequenceForm.checked(game);
        for (var q : captured) FiniteTwoPlayerRootSecurityEnvelope.preflight(checked, q, budget);
        long preflight = budget.audit().chargedUnits();
        var parent =
                FiniteTwoPlayerAffineSequenceForm.solveAscendingRefinedDirections(
                        checked.snapshot());
        var envelopes = new ArrayList<FiniteTwoPlayerRootSecurityEnvelope.Audit>();
        int pivots = 0, solves = 0;
        long arithmetic = 0;
        for (var q : captured) {
            var a =
                    FiniteTwoPlayerRootSecurityEnvelope.solveCapturedParent(
                                    checked.snapshot(),
                                    q,
                                    parent,
                                    compilerLimit - budget.audit().chargedUnits(),
                                    pivotLimit - pivots,
                                    arithmeticLimit - arithmetic)
                            .audit();
            budget.charge(a.work().compilerUnits());
            pivots = Math.addExact(pivots, a.work().lpPivots());
            solves = Math.addExact(solves, a.work().lpSolves());
            arithmetic = Math.addExact(arithmetic, a.work().lpArithmeticWork());
            envelopes.add(a);
        }
        var p = parent.audit();
        var work =
                new Work(
                        1,
                        2,
                        p.reductionWork().chargedUnits(),
                        Math.addExact(
                                p.firstLp().work().arithmeticWork(),
                                p.secondLp().work().arithmeticWork()),
                        preflight,
                        budget.audit().chargedUnits(),
                        compilerLimit,
                        solves,
                        pivots,
                        pivotLimit,
                        arithmetic,
                        arithmeticLimit,
                        0);
        return new Result(
                new Audit(
                        ALGORITHM,
                        SCOPE,
                        false,
                        p.snapshotHash(),
                        p,
                        parent.firstLpWitness(),
                        parent.secondLpWitness(),
                        envelopes,
                        work));
    }
}
