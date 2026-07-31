package com.leafuke.jea.incident;

import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public record IncidentBatch(List<IncidentSignal> signals) {
    public IncidentBatch {
        signals = List.copyOf(signals);
    }

    public Set<IncidentType> types() {
        var types = EnumSet.noneOf(IncidentType.class);
        signals.forEach(signal -> types.add(signal.type()));
        return types;
    }

    public Set<UUID> playerIds() {
        var ids = new LinkedHashSet<UUID>();
        signals.stream()
                .map(IncidentSignal::playerId)
                .filter(java.util.Objects::nonNull)
                .forEach(ids::add);
        return ids;
    }

    public String comment() {
        var players = new LinkedHashSet<String>();
        signals.stream()
                .map(IncidentSignal::playerName)
                .filter(name -> !name.isBlank())
                .forEach(players::add);

        var reasons = types().stream()
                .map(IncidentType::id)
                .toList();
        String actor = players.isEmpty() ? "global" : String.join(",", players);
        String comment = "[JEA] " + actor + ": " + String.join(",", reasons);
        return comment.length() <= 240 ? comment : comment.substring(0, 240);
    }
}
