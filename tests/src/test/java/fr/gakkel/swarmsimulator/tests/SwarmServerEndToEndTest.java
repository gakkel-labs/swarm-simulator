package fr.gakkel.swarmsimulator.tests;

import fr.gakkel.swarmsimulator.swarmserver.config.Settings;
import fr.gakkel.swarmsimulator.swarmserver.domain.BoidsConfig;
import fr.gakkel.swarmsimulator.swarmserver.domain.LossRiskLevel;
import fr.gakkel.swarmsimulator.swarmserver.domain.PredatorConfig;
import fr.gakkel.swarmsimulator.swarmserver.domain.SearchArea;
import fr.gakkel.swarmsimulator.swarmserver.server.SwarmServerBootstrap;
import fr.gakkel.swarmsimulator.swarmserver.simulation.DiagnosticsConfig;
import io.gakkel.swarm.contracts.v1.PlaceTargetRequest;
import io.gakkel.swarm.contracts.v1.SimulationControlGrpc;
import io.gakkel.swarm.contracts.v1.SubscribeRequest;
import io.gakkel.swarm.contracts.v1.SwarmObserverGrpc;
import io.gakkel.swarm.contracts.v1.Vec3;
import io.gakkel.swarm.contracts.v1.WorldState;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Starts the real server (simulation + gRPC) on a free port and talks to it like the Unity client
 * does: subscribe to the WorldState stream, then place a target.
 */
class SwarmServerEndToEndTest {

    private static final int DRONE_COUNT = 5;
    private static final long CALL_DEADLINE_SECONDS = 10;

    private SwarmServerBootstrap server;
    private ManagedChannel channel;

    @BeforeEach
    void startServer() throws Exception {
        // Port 0: the system picks a free port, so the test never clashes with a running server.
        Settings settings = new Settings(0, DRONE_COUNT, new SearchArea(100, 50), 100, 42L,
                BoidsConfig.builder().build(), DiagnosticsConfig.builder().build(),
                PredatorConfig.defaults(), true, LossRiskLevel.NONE,
                Settings.DEFAULT_DRONE_CATALOG, List.of());
        server = SwarmServerBootstrap.create(settings, null); // null: no metrics CSV file
        server.start();
        channel = ManagedChannelBuilder.forAddress("localhost", server.port())
                .usePlaintext()
                .build();
    }

    @AfterEach
    void stopServer() throws Exception {
        channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        server.stop();
    }

    @Test
    void subscribe_receivesTheDronesAndTheSearchAreaSizeInNed() {
        WorldState firstState = subscribe().next();

        assertThat(firstState.getAgentsCount()).isEqualTo(DRONE_COUNT);
        // server width 100 (East), height 100 (Down), depth 50 (North)
        assertThat(firstState.getSearchAreaSizeM().getX()).isEqualTo(50f);
        assertThat(firstState.getSearchAreaSizeM().getY()).isEqualTo(100f);
        assertThat(firstState.getSearchAreaSizeM().getZ()).isEqualTo(100f);
    }

    @Test
    void placeTarget_insideTheSearchArea_showsUpInTheStream() {
        Iterator<WorldState> stream = subscribe();
        Vec3 targetInNed = Vec3.newBuilder().setX(25).setY(50).setZ(60).build();

        controlStub().placeTarget(PlaceTargetRequest.newBuilder().setPosition(targetInNed).build());

        WorldState stateWithTarget = nextStateWithTarget(stream);
        assertThat(stateWithTarget.getSearchStatus().getTargetPosition()).isEqualTo(targetInNed);
    }

    @Test
    void placeTarget_belowTheSeabed_isRejected() {
        Vec3 belowSeabed = Vec3.newBuilder().setX(25).setY(50).setZ(500).build();
        PlaceTargetRequest request = PlaceTargetRequest.newBuilder().setPosition(belowSeabed).build();

        assertThatThrownBy(() -> controlStub().placeTarget(request))
                .isInstanceOfSatisfying(StatusRuntimeException.class, exception ->
                        assertThat(exception.getStatus().getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT));
    }

    private Iterator<WorldState> subscribe() {
        return SwarmObserverGrpc.newBlockingStub(channel)
                .withDeadlineAfter(CALL_DEADLINE_SECONDS, TimeUnit.SECONDS)
                .subscribeWorldState(SubscribeRequest.newBuilder().setClientId("end-to-end-test").build());
    }

    private SimulationControlGrpc.SimulationControlBlockingStub controlStub() {
        return SimulationControlGrpc.newBlockingStub(channel)
                .withDeadlineAfter(CALL_DEADLINE_SECONDS, TimeUnit.SECONDS);
    }

    // The stream sends 20 frames per second: the target appears within a few frames.
    private static WorldState nextStateWithTarget(Iterator<WorldState> stream) {
        while (stream.hasNext()) {
            WorldState state = stream.next();
            if (state.getSearchStatus().getTargetPlaced()) return state;
        }
        throw new AssertionError("the stream ended before showing the target");
    }
}
