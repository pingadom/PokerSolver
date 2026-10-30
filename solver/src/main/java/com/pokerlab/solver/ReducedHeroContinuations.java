package com.pokerlab.solver;

import com.pokerlab.solver.FiniteTwoPlayerSequenceForm.Node;
import java.math.BigInteger;
import java.util.*;

/** Exact reduced pure plans: omit only choices made unreachable by the hero's own actions. */
final class ReducedHeroContinuations {
    static final class PlanLimit extends IllegalArgumentException {
        private final BigInteger cartesianPlans, reducedPlans;

        PlanLimit(BigInteger cartesianPlans, BigInteger reducedPlans) {
            super("Reduced hero continuation plan budget exceeded: " + reducedPlans);
            this.cartesianPlans = cartesianPlans;
            this.reducedPlans = reducedPlans;
        }

        BigInteger cartesianPlans() {
            return cartesianPlans;
        }

        BigInteger reducedPlans() {
            return reducedPlans;
        }
    }

    record Compiled(
            BigInteger cartesianPlans, BigInteger reducedPlans, List<Map<String, String>> choices) {
        Compiled {
            choices = List.copyOf(choices);
        }
    }

    private record Link(String informationSet, String action) {}

    private static final Link ROOT = new Link(null, null);
    private final int hero;
    private final SequenceFormAffineProjection.Budget budget;
    private final Map<String, List<String>> actions = new TreeMap<>();
    private final Map<String, Link> parents = new HashMap<>();
    private final Map<Link, SortedSet<String>> children = new HashMap<>();
    private final Map<Link, BigInteger> counts = new HashMap<>();
    private final Set<Link> visiting = new HashSet<>();

    private ReducedHeroContinuations(int hero, SequenceFormAffineProjection.Budget budget) {
        this.hero = hero;
        this.budget = budget;
    }

    static Compiled compile(
            List<Node> roots, int hero, int treeNodes, SequenceFormAffineProjection.Budget budget) {
        if (roots.isEmpty() || hero < 0 || treeNodes < 1)
            throw new IllegalArgumentException("Owned continuation forest required");
        var compiler = new ReducedHeroContinuations(hero, budget);
        for (var root : roots) compiler.collect(root, ROOT);
        var cartesian = BigInteger.ONE;
        for (var row : compiler.actions.values()) {
            budget.charge(1);
            cartesian = cartesian.multiply(BigInteger.valueOf(row.size()));
        }
        var reduced = compiler.count(ROOT);
        if (reduced.compareTo(
                        BigInteger.valueOf(
                                FiniteTwoPlayerRootActionIntervals.MAX_CONTINUATION_PLANS))
                > 0) throw new PlanLimit(cartesian, reduced);
        budget.charge(reduced.longValueExact() * treeNodes);
        var choices = compiler.enumerate(ROOT);
        choices.sort(ReducedHeroContinuations::compare);
        if (choices.size() != reduced.intValueExact()
                || choices.stream().distinct().count() != choices.size())
            throw new IllegalStateException("Reduced plan inventory differs");
        return new Compiled(cartesian, reduced, choices);
    }

    private void collect(Node node, Link parent) {
        budget.charge(1);
        if (node.actor() == hero) {
            if (node.actions().isEmpty() || node.actions().size() != node.children().size())
                throw new IllegalArgumentException("Invalid hero continuation node");
            var oldActions = actions.putIfAbsent(node.key(), node.actions());
            var oldParent = parents.putIfAbsent(node.key(), parent);
            if ((oldActions != null && !oldActions.equals(node.actions()))
                    || (oldParent != null && !oldParent.equals(parent)))
                throw new IllegalArgumentException(
                        "Reduced plans require identical menus and perfect recall across worlds");
            children.computeIfAbsent(parent, p -> new TreeSet<>()).add(node.key());
            for (int i = 0; i < node.children().size(); i++)
                collect(node.children().get(i), new Link(node.key(), node.actions().get(i)));
        } else for (var child : node.children()) collect(child, parent);
    }

    private BigInteger count(Link parent) {
        budget.charge(1);
        var cached = counts.get(parent);
        if (cached != null) return cached;
        if (!visiting.add(parent))
            throw new IllegalArgumentException("Cyclic hero continuation recall");
        var product = BigInteger.ONE;
        for (String key : children.getOrDefault(parent, Collections.emptySortedSet())) {
            var sum = BigInteger.ZERO;
            for (String action : actions.get(key)) sum = sum.add(count(new Link(key, action)));
            budget.charge(actions.get(key).size());
            product = product.multiply(sum);
        }
        visiting.remove(parent);
        counts.put(parent, product);
        return product;
    }

    private List<Map<String, String>> enumerate(Link parent) {
        var product = new ArrayList<Map<String, String>>();
        product.add(Map.of());
        for (String key : children.getOrDefault(parent, Collections.emptySortedSet())) {
            var options = new ArrayList<Map<String, String>>();
            for (String action : actions.get(key))
                for (var suffix : enumerate(new Link(key, action))) {
                    budget.charge(1L + suffix.size());
                    var plan = new TreeMap<>(suffix);
                    plan.put(key, action);
                    options.add(Collections.unmodifiableMap(plan));
                }
            var next = new ArrayList<Map<String, String>>();
            for (var prefix : product)
                for (var suffix : options) {
                    budget.charge(1L + prefix.size() + suffix.size());
                    var plan = new TreeMap<>(prefix);
                    for (var e : suffix.entrySet())
                        if (plan.putIfAbsent(e.getKey(), e.getValue()) != null)
                            throw new IllegalArgumentException(
                                    "Duplicate independently activated hero information set");
                    next.add(Collections.unmodifiableMap(plan));
                }
            product = next;
        }
        return product;
    }

    private static int compare(Map<String, String> a, Map<String, String> b) {
        var first = a.entrySet().iterator();
        var second = b.entrySet().iterator();
        while (first.hasNext() && second.hasNext()) {
            var x = first.next();
            var y = second.next();
            int c = x.getKey().compareTo(y.getKey());
            if (c == 0) c = x.getValue().compareTo(y.getValue());
            if (c != 0) return c;
        }
        return Boolean.compare(first.hasNext(), second.hasNext());
    }
}
