package fr.gakkel.swarmsimulator.swarmserver.domain;

/**
 * Role of a drone in the fleet. Names follow the glossary (docs/produit/glossaire.md).
 *
 * <p>{@link #SPECIALIST} is a specialised drone (qualifies or intervenes on a contact). It is
 * reserved: no drone of this type is created yet.
 */
public enum AgentType {
    EXPLORER,
    SPECIALIST,
    CARRIER
}
