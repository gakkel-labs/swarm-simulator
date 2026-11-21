package fr.gakkel.swarmsimulator.swarmserver.domain;

/**
 * Role of a drone in the fleet. Names follow the glossary (docs/produit/glossaire.md).
 *
 * <p>{@link #SPECIALIST} and {@link #CARRIER} are reserved: no drone of these types is created yet.
 */
public enum AgentType {
    EXPLORER,
    SPECIALIST,
    CARRIER
}
