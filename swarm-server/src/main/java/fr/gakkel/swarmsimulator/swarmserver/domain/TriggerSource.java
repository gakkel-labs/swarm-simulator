package fr.gakkel.swarmsimulator.swarmserver.domain;

/**
 * Identifies who initiated a target placement.
 * New values (CLI, scenario file) can be added in v0.2 without touching the RPC contract — see ADR-0004.
 */
public enum TriggerSource {
    OPERATOR_CLICK
}
