package external.audit.client;

import com.pokerlab.solver.FiniteTwoPlayerAffineSequenceForm;

/**
 * Compilation control: all public audit components must be accessible outside the solver package.
 */
public final class AffinePublicAuditClient {
    public static long inspect(FiniteTwoPlayerAffineSequenceForm.Result result) {
        var audit = result.audit();
        FiniteTwoPlayerAffineSequenceForm.ProjectionAudit projection = audit.firstProjection();
        FiniteTwoPlayerAffineSequenceForm.ProjectedPayoffAudit payoff = audit.projectedPayoff();
        return projection.transform().size()
                + payoff.matrix().size()
                + audit.reductionWork().chargedUnits();
    }
}
