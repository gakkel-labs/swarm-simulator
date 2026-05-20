package fr.gakkel.swarmsimulator.swarmserver.server;

import fr.gakkel.swarmsimulator.swarmserver.domain.TriggerSource;
import fr.gakkel.swarmsimulator.swarmserver.domain.Vector3D;
import fr.gakkel.swarmsimulator.swarmserver.simulation.SimulationService;
import io.gakkel.swarm.contracts.v1.PlaceTargetRequest;
import io.gakkel.swarm.contracts.v1.PlaceTargetResponse;
import io.gakkel.swarm.contracts.v1.Vec3;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SimulationControlImplTest {

    @Mock private SimulationService mockSimulationService;
    @Mock private StreamObserver<PlaceTargetResponse> mockResponseObserver;

    private SimulationControlImpl simulationControl;

    @BeforeEach
    void setUp() {
        simulationControl = new SimulationControlImpl(mockSimulationService);
    }

    @Test
    void placeTarget_validRequest_delegatesToSimulationService() {
        // NED position: North=10, East=20, Down=30
        PlaceTargetRequest request = PlaceTargetRequest.newBuilder()
                .setPosition(Vec3.newBuilder().setX(10).setY(20).setZ(30).build())
                .build();
        ArgumentCaptor<Vector3D> positionCaptor = ArgumentCaptor.forClass(Vector3D.class);

        simulationControl.placeTarget(request, mockResponseObserver);

        verify(mockSimulationService).placeTarget(positionCaptor.capture(), eq(TriggerSource.OPERATOR_CLICK));
        // NED → server: server.x = NED.y=20, server.y = -NED.z=-30, server.z = NED.x=10
        Vector3D serverPosition = positionCaptor.getValue();
        assertThat(serverPosition.x()).isEqualTo(20.0);
        assertThat(serverPosition.y()).isEqualTo(-30.0);
        assertThat(serverPosition.z()).isEqualTo(10.0);
    }

    @Test
    void placeTarget_validRequest_respondsWithEmptyResponse() {
        PlaceTargetRequest request = PlaceTargetRequest.newBuilder()
                .setPosition(Vec3.newBuilder().setX(0).setY(0).setZ(0).build())
                .build();

        simulationControl.placeTarget(request, mockResponseObserver);

        verify(mockResponseObserver).onNext(any(PlaceTargetResponse.class));
        verify(mockResponseObserver).onCompleted();
        verify(mockResponseObserver, never()).onError(any());
    }

    @Test
    void placeTarget_missingPosition_returnsInvalidArgument() {
        PlaceTargetRequest request = PlaceTargetRequest.newBuilder().build();

        simulationControl.placeTarget(request, mockResponseObserver);

        verify(mockResponseObserver).onError(any());
        verify(mockResponseObserver, never()).onNext(any());
        verifyNoInteractions(mockSimulationService);
    }
}
