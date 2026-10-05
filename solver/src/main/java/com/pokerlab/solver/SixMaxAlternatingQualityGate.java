package com.pokerlab.solver;

/** Whole-round acceptance in one declared game; never a general equilibrium certificate. */
public final class SixMaxAlternatingQualityGate {
    public static final double PARENT_TOLERANCE_BB = 1e-9;

    public record Decision(
            String status,
            boolean accepted,
            boolean everySelectedBranchReached,
            boolean conditionalGapWithinTarget,
            boolean parentNashConvDidNotIncrease,
            double parentImprovementBb,
            boolean materialParentImprovement) {}

    private SixMaxAlternatingQualityGate() {}

    public static Decision assess(
            double before,
            double after,
            double conditionalGap,
            boolean allReached,
            double conditionalTarget,
            double minimumImprovement) {
        for (double value : new double[] {before, after, conditionalGap})
            if (!Double.isFinite(value) || value < 0)
                throw new IllegalArgumentException("Quality scores must be finite and nonnegative");
        if (!Double.isFinite(conditionalTarget)
                || conditionalTarget <= 0
                || !Double.isFinite(minimumImprovement)
                || minimumImprovement < PARENT_TOLERANCE_BB)
            throw new IllegalArgumentException(
                    "Require a positive finite conditional target and improvement at least 1e-9bb");
        double improvement = before - after;
        boolean conditional = allReached && conditionalGap <= conditionalTarget;
        boolean parent = improvement >= -PARENT_TOLERANCE_BB;
        boolean material = improvement >= minimumImprovement;
        String status =
                !allReached
                        ? "SELECTED_BRANCH_UNREACHED"
                        : !conditional
                                ? "CONDITIONAL_TARGET_FAILED"
                                : !parent
                                        ? "PARENT_QUALITY_REGRESSION"
                                        : !material ? "NO_MATERIAL_IMPROVEMENT" : "ACCEPTED";
        return new Decision(
                status,
                conditional && parent && material,
                allReached,
                conditional,
                parent,
                improvement,
                material);
    }
}
