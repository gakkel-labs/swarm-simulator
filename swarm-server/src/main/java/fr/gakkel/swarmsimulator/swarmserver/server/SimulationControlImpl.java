package fr.gakkel.swarmsimulator.swarmserver.server;

import fr.gakkel.swarmsimulator.swarmserver.domain.TriggerSource;
import fr.gakkel.swarmsimulator.swarmserver.domain.Vector3D;
import fr.gakkel.swarmsimulator.swarmserver.simulation.SimulationService;
import io.gakkel.swarm.contracts.v1.PlaceTargetRequest;
import io.gakkel.swarm.contracts.v1.PlaceTargetResponse;
import io.gakkel.swarm.contracts.v1.SimulationControlGrpc;
import io.gakkel.swarm.contracts.v1.Vec3;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

public class SimulationControlImpl extends SimulationControlGrpc.SimulationControlImplBase {

    private static final Logger LOG = LoggerFactory.getLogger(SimulationControlImpl.class);

    private final SimulationService simulationService;

    public SimulationControlImpl(SimulationService simulationService) {
        this.simulationService = Objects.requireNonNull(simulationService, "simulationService");
    }

    @Override
    public void placeTarget(PlaceTargetRequest request, StreamObserver<PlaceTargetResponse> responseObserver) {
        if (!request.hasPosition()) {
            responseObserver.onError(Status.INVALID_ARGUMENT
                    .withDescription("position is required")
                    .asRuntimeException());
            return;
        }

        Vec3 nedPosition = request.getPosition();
        // NED → server coordinates: server.x = NED.y (East), server.y = -NED.z (Down→Up), server.z = NED.x (North)
        Vector3D serverPosition = new Vector3D(nedPosition.getY(), -nedPosition.getZ(), nedPosition.getX());

        simulationService.placeTarget(serverPosition, TriggerSource.OPERATOR_CLICK);
        LOG.info("PlaceTarget RPC — NED({}, {}, {})", nedPosition.getX(), nedPosition.getY(), nedPosition.getZ());

        responseObserver.onNext(PlaceTargetResponse.getDefaultInstance());
        responseObserver.onCompleted();
    }
}
