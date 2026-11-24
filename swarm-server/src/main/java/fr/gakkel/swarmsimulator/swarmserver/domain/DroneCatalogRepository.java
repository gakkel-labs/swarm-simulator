package fr.gakkel.swarmsimulator.swarmserver.domain;

/**
 * Where the drone catalog is kept. A port (DDD): the domain says what it needs, and an adapter
 * says how. Today the adapter reads a JSON file; at Step 3, when the operator edits the catalog
 * from the control station, a database adapter can take its place without changing the domain.
 */
public interface DroneCatalogRepository {

    /**
     * Loads the whole catalog.
     *
     * @throws IllegalStateException when the catalog cannot be read or holds a wrong value
     */
    DroneCatalog load();
}
