package com.pokerlab.solver;

/** Separately identified objective conditioning; the v1 solves and saved reports are unchanged. */
public final class FiniteTwoPlayerConditionedDecisionLoss {
    public static final String REPRESENTATION =
            "UPPER_OBJECTIVE_MINUS_MAX_ABSOLUTE_TERMINAL_UTILITY_PLUS_ONE/v1";

    public record Audit(
            String representation,
            double upperObjectiveShift,
            FiniteTwoPlayerConditionalDecisionLoss.Audit joint) {
        public Audit {
            if (!REPRESENTATION.equals(representation)
                    || !Double.isFinite(upperObjectiveShift)
                    || upperObjectiveShift > -1
                    || joint == null
                    || !FiniteTwoPlayerConditionalDecisionLoss.CONDITIONED_ALGORITHM.equals(
                            joint.algorithm())
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
        var owned =
                FiniteTwoPlayerConditionalDecisionLoss.solve(
                        game,
                        question,
                        securitySlack,
                        minimumReach,
                        compilerLimit,
                        pivotLimit,
                        arithmeticLimit,
                        true);
        return new Result(new Audit(REPRESENTATION, owned.upperObjectiveShift(), owned.audit()));
    }
}
