package fr.gakkel.swarmsimulator.swarmserver.server;

import fr.gakkel.swarmsimulator.swarmserver.domain.Agent;
import fr.gakkel.swarmsimulator.swarmserver.domain.Obstacle;
import fr.gakkel.swarmsimulator.swarmserver.domain.Predator;
import fr.gakkel.swarmsimulator.swarmserver.domain.Target;
import fr.gakkel.swarmsimulator.swarmserver.domain.Vector3D;
import fr.gakkel.swarmsimulator.swarmserver.domain.World;
import io.gakkel.swarm.contracts.v1.AgentState;
import io.gakkel.swarm.contracts.v1.AgentType;
import io.gakkel.swarm.contracts.v1.WorldState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class WorldStateBuilderTest {

    private World world;
    private WorldStateBuilder builder;

    @BeforeEach
    void setUp() {
        world = new World(100, 100, 50);
        builder = new WorldStateBuilder(world, () -> 0.0);
    }

    @Test
    void build_withOneExplorerAgent_allFieldsMappedCorrectly() {
        UUID id = UUID.randomUUID();
        Vector3D position = new Vector3D(1.0, 2.0, 3.0);
        Vector3D velocity = new Vector3D(0.5, 0.6, 0.7);
        world.addAgent(new Agent(id, fr.gakkel.swarmsimulator.swarmserver.domain.AgentType.EXPLORER, position, velocity));

        WorldState state = builder.build();

        assertThat(state.getAgentsCount()).isEqualTo(1);
        AgentState agentState = state.getAgents(0);
        assertThat(agentState.getId()).isEqualTo(id.toString());
        assertThat(agentState.getType()).isEqualTo(AgentType.AGENT_TYPE_EXPLORER);
        // NED encoding: North=server.Z, East=server.X, Down=-server.Y (Y=0 is water surface)
        // pos=(1,2,3) → proto(x=3, y=1, z=-2)
        assertThat(agentState.getPositionXyz().getX()).isEqualTo(3.0f);
        assertThat(agentState.getPositionXyz().getY()).isEqualTo(1.0f);
        assertThat(agentState.getPositionXyz().getZ()).isEqualTo(-2.0f);
        // vel=(0.5,0.6,0.7) → proto.X = server.Z = 0.7
        assertThat(agentState.getVelocityMps().getX()).isEqualTo(0.7f);
    }

    @Test
    void build_emptyWorld_returnsNoAgents() {
        WorldState state = builder.build();

        assertThat(state.getAgentsCount()).isZero();
        assertThat(state.getTimestampUnixMs()).isPositive();
    }

    @Test
    void build_alwaysIncludesSensorRadius() {
        WorldState state = builder.build();

        // Clients need this to size the detection-zone overlay
        assertThat(state.getSensorRadiusM())
                .isEqualTo((float) fr.gakkel.swarmsimulator.swarmserver.simulation.SimulationConstants.SENSOR_RADIUS_M);
    }

    @Test
    void toAgentState_carrierAgent_mapsToProtoCarrierType() {
        Agent agent = new Agent(UUID.randomUUID(),
                fr.gakkel.swarmsimulator.swarmserver.domain.AgentType.CARRIER,
                new Vector3D(0, 0, 0), Vector3D.ZERO);

        AgentState result = builder.toAgentState(agent);

        assertThat(result.getType()).isEqualTo(AgentType.AGENT_TYPE_CARRIER);
    }

    @Test
    void toAgentState_operatorAgent_mapsToProtoOperatorType() {
        Agent agent = new Agent(UUID.randomUUID(),
                fr.gakkel.swarmsimulator.swarmserver.domain.AgentType.OPERATOR,
                new Vector3D(0, 0, 0), Vector3D.ZERO);

        AgentState result = builder.toAgentState(agent);

        assertThat(result.getType()).isEqualTo(AgentType.AGENT_TYPE_OPERATOR);
    }

    @Test
    void build_withObstacles_obstaclesMappedToProto() {
        world.addObstacle(new Obstacle(new Vector3D(10, 20, 5), 3.0));
        world.addObstacle(new Obstacle(new Vector3D(40, 50, 25), 6.5));

        WorldState state = builder.build();

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
    void build_noObstacles_obstaclesListEmpty() {
        WorldState state = builder.build();

        assertThat(state.getObstaclesCount()).isZero();
    }

    @Test
    void build_withPredator_predatorMappedToProto() {
        // predator at server(10, -20, 5) → NED proto(x=5, y=10, z=20)
        world.setPredator(new Predator(new Vector3D(10, -20, 5), Vector3D.ZERO));

        WorldState state = builder.build();

        assertThat(state.getPredatorsCount()).isEqualTo(1);
        var predatorState = state.getPredators(0);
        assertThat(predatorState.getId()).isEqualTo("predator-0");
        assertThat(predatorState.getPositionXyz().getX()).isEqualTo(5.0f);
        assertThat(predatorState.getPositionXyz().getY()).isEqualTo(10.0f);
        assertThat(predatorState.getPositionXyz().getZ()).isEqualTo(20.0f);
    }

    @Test
    void build_noPredator_predatorsListEmpty() {
        WorldState state = builder.build();

        assertThat(state.getPredatorsCount()).isZero();
    }

    @Test
    void build_withTarget_includesSearchStatus() {
        world.setTarget(new Target(new Vector3D(50, -30, 25)));

        WorldState state = builder.build();

        assertThat(state.hasSearchStatus()).isTrue();
        assertThat(state.getSearchStatus().getTargetPlaced()).isTrue();
        assertThat(state.getSearchStatus().getElapsedSimS()).isGreaterThanOrEqualTo(0f);
        // server(50,-30,25) → NED proto(x=25, y=50, z=30)
        assertThat(state.getSearchStatus().getTargetPosition().getX()).isEqualTo(25.0f);
        assertThat(state.getSearchStatus().getTargetPosition().getY()).isEqualTo(50.0f);
        assertThat(state.getSearchStatus().getTargetPosition().getZ()).isEqualTo(30.0f);
    }

    @Test
    void build_withFoundTarget_includesFoundEvent() {
        Target target = new Target(new Vector3D(50, -30, 25));
        target.markFound("drone-007");
        world.setTarget(target);

        WorldState state = builder.build();

        assertThat(state.getSearchStatus().hasFoundEvent()).isTrue();
        assertThat(state.getSearchStatus().getFoundEvent().getAgentId()).isEqualTo("drone-007");
        assertThat(state.getSearchStatus().getFoundEvent().getElapsedSimS()).isGreaterThanOrEqualTo(0f);
    }

    @Test
    void build_noTarget_searchStatusAbsent() {
        WorldState state = builder.build();

        assertThat(state.hasSearchStatus()).isFalse();
    }

    @Test
    void build_cohesionSupplierValue_isIncludedInWorldState() {
        WorldStateBuilder builderWithCohesion = new WorldStateBuilder(world, () -> 12.5);

        WorldState state = builderWithCohesion.build();

        assertThat(state.getCohesionSpreadM()).isEqualTo(12.5f);
    }
}
