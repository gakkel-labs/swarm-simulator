package fr.gakkel.swarmsimulator.swarmserver.domain;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The drone models available for a mission, each with its {@link DroneSpecification}
 * (ADR-0011). A fleet configuration picks its models here, by name.
 *
 * <p>The catalog does not know where it comes from: a {@link DroneCatalogRepository} loads it
 * (a JSON file today, the database later).
 */
public final class DroneCatalog {

    // A LinkedHashMap keeps the order of the catalog: the first model is the default one.
    private final Map<String, DroneSpecification> specificationsByModel;

    public DroneCatalog(List<DroneSpecification> specifications) {
        Objects.requireNonNull(specifications, "specifications");
        if (specifications.isEmpty()) {
            throw new IllegalArgumentException("a drone catalog needs at least one drone model");
        }
        Map<String, DroneSpecification> byModel = new LinkedHashMap<>();
        for (DroneSpecification specification : specifications) {
            Objects.requireNonNull(specification, "specification");
            if (byModel.putIfAbsent(specification.model(), specification) != null) {
                throw new IllegalArgumentException(
                        "drone model '" + specification.model() + "' is twice in the catalog");
            }
        }
        this.specificationsByModel = byModel;
    }

    /**
     * The specification of {@code model}.
     *
     * @throws UnknownDroneModelException when the catalog has no such model
     */
    public DroneSpecification specification(String model) {
        DroneSpecification specification = specificationsByModel.get(model);
        if (specification == null) {
            throw new UnknownDroneModelException(model, models());
        }
        return specification;
    }

    /** The model used when the configuration names none: the first one of the catalog. */
    public DroneSpecification defaultSpecification() {
        return specificationsByModel.values().iterator().next();
    }

    /** The names of every model, in the order of the catalog. */
    public List<String> models() {
        return List.copyOf(specificationsByModel.keySet());
    }
}
