package com.pokerlab.solver;

import java.util.List;

/** Validated public observations and exact pair shares for the shared one-bet engine. */
interface SixMaxFlopPayoffView {
    String namespace();

    int dealCount();

    List<String> hands(int deal);

    List<Long> counts(int deal);

    /** Existing partitions are history independent; new partitions must declare their history. */
    default List<Long> counts(String publicHistory, int deal) {
        return counts(deal);
    }

    String key(int observation);

    double share(int deal, int mask, int player, int observation);

    default double share(String publicHistory, int deal, int mask, int player, int observation) {
        return share(deal, mask, player, observation);
    }

    static SixMaxFlopPayoffView rank(SixMaxRankTexturePayoffTable.Artifact table) {
        return new SixMaxFlopPayoffView() {
            public String namespace() {
                return "rank-texture";
            }

            public int dealCount() {
                return table.deals().size();
            }

            public List<String> hands(int deal) {
                return table.deals().get(deal).hands();
            }

            public List<Long> counts(int deal) {
                return table.deals().get(deal).flopCounts();
            }

            public String key(int observation) {
                return Integer.toString(table.signals().get(observation).key());
            }

            public double share(int deal, int mask, int player, int observation) {
                var row = table.deals().get(deal);
                return row.pair(mask)
                        .share(player, observation, row.flopCounts().get(observation) * 666);
            }
        };
    }
}
