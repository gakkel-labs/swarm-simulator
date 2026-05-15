package fr.gakkel.swarmsimulator.swarmserver.server;

import fr.gakkel.swarmsimulator.swarmserver.domain.Agent;
import fr.gakkel.swarmsimulator.swarmserver.domain.Obstacle;
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
        assertThat(as.getPositionXyz().getX()).isEqualTo(1.0f);
        assertThat(as.getPositionXyz().getY()).isEqualTo(2.0f);
        assertThat(as.getPositionXyz().getZ()).isEqualTo(3.0f);
        assertThat(as.getVelocityMps().getX()).isEqualTo(0.5f);
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
        var first = state.getObstacles(0);
        assertThat(first.getPositionXyz().getX()).isEqualTo(10.0f);
        assertThat(first.getPositionXyz().getY()).isEqualTo(20.0f);
        assertThat(first.getPositionXyz().getZ()).isEqualTo(5.0f);
        assertThat(first.getRadiusM()).isEqualTo(3.0f);
        assertThat(state.getObstacles(1).getRadiusM()).isEqualTo(6.5f);
    }

    @Test
    void buildWorldState_noObstacles_obstaclesListEmpty() {
        WorldState state = impl.buildWorldState();

        assertThat(state.getObstaclesCount()).isZero();
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
