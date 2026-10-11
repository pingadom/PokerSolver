package com.pokerlab.solver;

/** Separately identified objective conditioning; the v1 solves and saved reports are unchanged. */
public final class FiniteTwoPlayerConditionedDecisionLoss {
    public static final String REPRESENTATION =
            "UPPER_OBJECTIVE_MINUS_MAX_ABSOLUTE_TERMINAL_UTILITY_PLUS_ONE/v1";
    public static final String L1_REPRESENTATION =
            "UPPER_OBJECTIVE_SHIFT_STABLE_ASCENDING_L1_JOINT_ROWS_ORIGINAL_CERTIFICATES/v2";

    public record Audit(
            String representation,
            double upperObjectiveShift,
            FiniteTwoPlayerConditionalDecisionLoss.Audit joint) {
        public Audit {
            boolean l1 = L1_REPRESENTATION.equals(representation);
            if ((!REPRESENTATION.equals(representation) && !l1)
                    || !Double.isFinite(upperObjectiveShift)
                    || upperObjectiveShift > -1
                    || joint == null
                    || !(l1
                                    ? FiniteTwoPlayerConditionalDecisionLoss
                                            .L1_CONDITIONED_ALGORITHM
                                    : FiniteTwoPlayerConditionalDecisionLoss.CONDITIONED_ALGORITHM)
                            .equals(joint.algorithm())
                    || joint.actions().stream()
                            .anyMatch(
                                    a ->
                                            !FiniteTwoPlayerConditionalActionIntervals
                                                    .CONDITIONED_ALGORITHM
                                                    .equals(a.interval().algorithm())))
                throw new IllegalArgumentException("Invalid conditioned decision-loss identity");
        }
    }

    public static final class Result {
        private final Audit audit;

        private Result(Audit audit) {
            this.audit = audit;
        }

        public Audit audit() {
            return audit;
        }
    }

    private FiniteTwoPlayerConditionedDecisionLoss() {}

    public static <S> Result solve(
            MultiPlayerCfrGame<S> game,
            FiniteTwoPlayerConditionalDecisionLoss.Question question,
            double securitySlack,
            double minimumReach)
            throws Exception {
        return solve(
                game,
                question,
                securitySlack,
                minimumReach,
                FiniteTwoPlayerAffineSequenceForm.MAX_REDUCTION_WORK,
                BoundedLinearProgram.MAX_PIVOTS,
                BoundedLinearProgram.MAX_ARITHMETIC_WORK);
    }

    static <S> Result solve(
            MultiPlayerCfrGame<S> game,
            FiniteTwoPlayerConditionalDecisionLoss.Question question,
            double securitySlack,
            double minimumReach,
            long compilerLimit,
            int pivotLimit,
            long arithmeticLimit)
            throws Exception {
        return solve(
                game,
                question,
                securitySlack,
                minimumReach,
                compilerLimit,
                pivotLimit,
                arithmeticLimit,
                false);
    }

    public static <S> Result solveAscendingL1JointRows(
            MultiPlayerCfrGame<S> game,
            FiniteTwoPlayerConditionalDecisionLoss.Question question,
            double securitySlack,
            double minimumReach)
            throws Exception {
        return solve(
                game,
                question,
                securitySlack,
                minimumReach,
                FiniteTwoPlayerAffineSequenceForm.MAX_REDUCTION_WORK,
                BoundedLinearProgram.MAX_PIVOTS,
                BoundedLinearProgram.MAX_ARITHMETIC_WORK,
                true);
    }

    static <S> Result solve(
            MultiPlayerCfrGame<S> game,
            FiniteTwoPlayerConditionalDecisionLoss.Question question,
            double securitySlack,
            double minimumReach,
            long compilerLimit,
            int pivotLimit,
            long arithmeticLimit,
            boolean ascendingL1JointRows)
            throws Exception {
        var owned =
                FiniteTwoPlayerConditionalDecisionLoss.solve(
                        game,
                        question,
                        securitySlack,
                        minimumReach,
                        compilerLimit,
                        pivotLimit,
                        arithmeticLimit,
                        true,
                        ascendingL1JointRows);
        return new Result(
                new Audit(
                        ascendingL1JointRows ? L1_REPRESENTATION : REPRESENTATION,
                        owned.upperObjectiveShift(),
                        owned.audit()));
    }
}
