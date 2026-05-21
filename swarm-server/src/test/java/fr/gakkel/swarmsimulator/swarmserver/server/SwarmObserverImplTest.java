package fr.gakkel.swarmsimulator.swarmserver.server;

import fr.gakkel.swarmsimulator.swarmserver.domain.Agent;
import fr.gakkel.swarmsimulator.swarmserver.domain.Obstacle;
import fr.gakkel.swarmsimulator.swarmserver.domain.Predator;
import fr.gakkel.swarmsimulator.swarmserver.domain.Target;
import fr.gakkel.swarmsimulator.swarmserver.domain.Vector3D;
import fr.gakkel.swarmsimulator.swarmserver.domain.World;
import io.gakkel.swarm.contracts.v1.AgentState;
import io.gakkel.swarm.contracts.v1.AgentType;
import io.gakkel.swarm.contracts.v1.SubscribeRequest;
import io.gakkel.swarm.contracts.v1.WorldState;
import io.grpc.stub.ServerCallStreamObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SwarmObserverImplTest {

    @Mock private ScheduledExecutorService mockExecutor;
    @Mock private ServerCallStreamObserver<WorldState> mockObserver;

    private World world;
    private SwarmObserverImpl impl;

    @BeforeEach
    void setUp() {
        world = new World(100, 100, 50);
        // mock executor prevents background scheduling — broadcast() called manually in tests
        impl = new SwarmObserverImpl(world, mockExecutor);
    }

    @Test
    void buildWorldState_withOneExplorerAgent_allFieldsMappedCorrectly() {
        // Arrange
        UUID id = UUID.randomUUID();
        Vector3D pos = new Vector3D(1.0, 2.0, 3.0);
        Vector3D vel = new Vector3D(0.5, 0.6, 0.7);
        world.addAgent(new Agent(id, fr.gakkel.swarmsimulator.swarmserver.domain.AgentType.EXPLORER, pos, vel));

        // Act
        WorldState state = impl.buildWorldState();

        // Assert
        assertThat(state.getAgentsCount()).isEqualTo(1);
        AgentState as = state.getAgents(0);
        assertThat(as.getId()).isEqualTo(id.toString());
        assertThat(as.getType()).isEqualTo(AgentType.AGENT_TYPE_EXPLORER);
        // NED encoding: North=server.Z, East=server.X, Down=-server.Y (Y=0 is water surface)
        // pos=(1,2,3) → proto(x=3, y=1, z=-2)
        assertThat(as.getPositionXyz().getX()).isEqualTo(3.0f);
        assertThat(as.getPositionXyz().getY()).isEqualTo(1.0f);
        assertThat(as.getPositionXyz().getZ()).isEqualTo(-2.0f);
        // vel=(0.5,0.6,0.7) → proto.X = server.Z = 0.7
        assertThat(as.getVelocityMps().getX()).isEqualTo(0.7f);
    }

    @Test
    void buildWorldState_emptyWorld_returnsNoAgents() {
        // Arrange — world has no agents

        // Act
        WorldState state = impl.buildWorldState();

        // Assert
        assertThat(state.getAgentsCount()).isZero();
        assertThat(state.getTimestampUnixMs()).isPositive();
    }

    @Test
    void buildWorldState_alwaysIncludesSensorRadius() {
        // Act
        WorldState state = impl.buildWorldState();

        // Assert — clients need this to size the detection-zone overlay
        assertThat(state.getSensorRadiusM())
                .isEqualTo((float) fr.gakkel.swarmsimulator.swarmserver.simulation.SimulationConstants.SENSOR_RADIUS_M);
    }

    @Test
    void toAgentState_carrierAgent_mapsToProtoCarrierType() {
        // Arrange
        Agent agent = new Agent(UUID.randomUUID(),
                fr.gakkel.swarmsimulator.swarmserver.domain.AgentType.CARRIER,
                new Vector3D(0, 0, 0), Vector3D.ZERO);

        // Act
        AgentState result = impl.toAgentState(agent);

        // Assert
        assertThat(result.getType()).isEqualTo(AgentType.AGENT_TYPE_CARRIER);
    }

    @Test
    void toAgentState_operatorAgent_mapsToProtoOperatorType() {
        // Arrange
        Agent agent = new Agent(UUID.randomUUID(),
                fr.gakkel.swarmsimulator.swarmserver.domain.AgentType.OPERATOR,
                new Vector3D(0, 0, 0), Vector3D.ZERO);

        // Act
        AgentState result = impl.toAgentState(agent);

        // Assert
        assertThat(result.getType()).isEqualTo(AgentType.AGENT_TYPE_OPERATOR);
    }

    @Test
    void subscribeWorldState_newClient_receivesWorldStateOnBroadcast() {
        // Arrange
        when(mockObserver.isCancelled()).thenReturn(false);
        SubscribeRequest req = SubscribeRequest.newBuilder().setClientId("grpcurl").build();

        // Act
        impl.subscribeWorldState(req, mockObserver);
        impl.broadcast();

        // Assert
        verify(mockObserver, times(1)).onNext(any(WorldState.class));
    }

    @Test
    void subscribeWorldState_whenClientCancels_noLongerReceivesFrames() {
        // Arrange
        SubscribeRequest req = SubscribeRequest.newBuilder().setClientId("unity-client").build();
        ArgumentCaptor<Runnable> cancelCaptor = ArgumentCaptor.forClass(Runnable.class);

        // Act
        impl.subscribeWorldState(req, mockObserver);
        verify(mockObserver).setOnCancelHandler(cancelCaptor.capture());
        cancelCaptor.getValue().run(); // simulate client disconnect
        impl.broadcast();

        // Assert
        verify(mockObserver, never()).onNext(any());
    }

    @Test
    void buildWorldState_withObstacles_obstaclesMappedToProto() {
        // Arrange
        world.addObstacle(new Obstacle(new Vector3D(10, 20, 5), 3.0));
        world.addObstacle(new Obstacle(new Vector3D(40, 50, 25), 6.5));

        // Act
        WorldState state = impl.buildWorldState();

        // Assert
        assertThat(state.getObstaclesCount()).isEqualTo(2);
        // obstacle at server(10,20,5) → NED proto(x=5, y=10, z=-20)
        var first = state.getObstacles(0);
        assertThat(first.getPositionXyz().getX()).isEqualTo(5.0f);
        assertThat(first.getPositionXyz().getY()).isEqualTo(10.0f);
        assertThat(first.getPositionXyz().getZ()).isEqualTo(-20.0f);
        assertThat(first.getRadiusM()).isEqualTo(3.0f);
        assertThat(state.getObstacles(1).getRadiusM()).isEqualTo(6.5f);
    }

    @Test
    void buildWorldState_noObstacles_obstaclesListEmpty() {
        WorldState state = impl.buildWorldState();

        assertThat(state.getObstaclesCount()).isZero();
    }

    @Test
    void buildWorldState_withPredator_predatorMappedToProto() {
        // predator at server(10, -20, 5) → NED proto(x=5, y=10, z=20)
        world.setPredator(new Predator(new Vector3D(10, -20, 5), Vector3D.ZERO));

        WorldState state = impl.buildWorldState();

        assertThat(state.getPredatorsCount()).isEqualTo(1);
        var ps = state.getPredators(0);
        assertThat(ps.getId()).isEqualTo("predator-0");
        assertThat(ps.getPositionXyz().getX()).isEqualTo(5.0f);
        assertThat(ps.getPositionXyz().getY()).isEqualTo(10.0f);
        assertThat(ps.getPositionXyz().getZ()).isEqualTo(20.0f);
    }

    @Test
    void buildWorldState_noPredator_predatorsListEmpty() {
        WorldState state = impl.buildWorldState();

        assertThat(state.getPredatorsCount()).isZero();
    }

    @Test
    void buildWorldState_withTarget_includesSearchStatus() {
        world.setTarget(new Target(new Vector3D(50, -30, 25)));

        WorldState state = impl.buildWorldState();

        assertThat(state.hasSearchStatus()).isTrue();
        assertThat(state.getSearchStatus().getTargetPlaced()).isTrue();
        assertThat(state.getSearchStatus().getElapsedSimS()).isGreaterThanOrEqualTo(0f);
        // server(50,-30,25) → NED proto(x=25, y=50, z=30)
        assertThat(state.getSearchStatus().getTargetPosition().getX()).isEqualTo(25.0f);
        assertThat(state.getSearchStatus().getTargetPosition().getY()).isEqualTo(50.0f);
        assertThat(state.getSearchStatus().getTargetPosition().getZ()).isEqualTo(30.0f);
    }

    @Test
    void buildWorldState_withFoundTarget_includesFoundEvent() {
        Target target = new Target(new Vector3D(50, -30, 25));
        target.markFound("drone-007");
        world.setTarget(target);

        WorldState state = impl.buildWorldState();

        assertThat(state.getSearchStatus().hasFoundEvent()).isTrue();
        assertThat(state.getSearchStatus().getFoundEvent().getAgentId()).isEqualTo("drone-007");
        assertThat(state.getSearchStatus().getFoundEvent().getElapsedSimS()).isGreaterThanOrEqualTo(0f);
    }

    @Test
    void buildWorldState_noTarget_searchStatusAbsent() {
        WorldState state = impl.buildWorldState();

        assertThat(state.hasSearchStatus()).isFalse();
    }

    @Test
    void broadcast_whenObserverAlreadyCancelled_removesWithoutPushing() {
        // Arrange
        when(mockObserver.isCancelled()).thenReturn(false);
        SubscribeRequest req = SubscribeRequest.newBuilder().setClientId("stale").build();
        impl.subscribeWorldState(req, mockObserver);

        // simulate isCancelled becoming true between subscribe and next broadcast
        when(mockObserver.isCancelled()).thenReturn(true);

        // Act
        impl.broadcast();

        // Assert
        verify(mockObserver, never()).onNext(any());
    }
}
