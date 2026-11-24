package fr.gakkel.swarmsimulator.swarmserver.domain;

import java.util.List;

/** A fleet configuration names a drone model that is not in the drone catalog. */
public final class UnknownDroneModelException extends IllegalArgumentException {

    public UnknownDroneModelException(String model, List<String> knownModels) {
        super("Unknown drone model '" + model + "'. Known models: "
                + String.join(", ", knownModels));
    }
}
