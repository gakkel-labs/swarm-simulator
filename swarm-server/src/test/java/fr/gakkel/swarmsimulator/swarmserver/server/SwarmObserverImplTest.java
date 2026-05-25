package fr.gakkel.swarmsimulator.swarmserver.server;

import fr.gakkel.swarmsimulator.swarmserver.domain.World;
import io.gakkel.swarm.contracts.v1.SubscribeRequest;
import io.gakkel.swarm.contracts.v1.WorldState;
import io.grpc.stub.ServerCallStreamObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.ScheduledExecutorService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SwarmObserverImplTest {

    @Mock private ScheduledExecutorService mockExecutor;
    @Mock private ServerCallStreamObserver<WorldState> mockObserver;

    private SwarmObserverImpl impl;

    @BeforeEach
    void setUp() {
        World world = new World(100, 100, 50);
        // mock executor prevents background scheduling — broadcast() called manually in tests
        impl = new SwarmObserverImpl(world, mockExecutor);
    }

    @Test
    void subscribeWorldState_newClient_receivesWorldStateOnBroadcast() {
        when(mockObserver.isCancelled()).thenReturn(false);
        SubscribeRequest request = SubscribeRequest.newBuilder().setClientId("grpcurl").build();

        impl.subscribeWorldState(request, mockObserver);
        impl.broadcast();

        verify(mockObserver, times(1)).onNext(any(WorldState.class));
    }

    @Test
    void subscribeWorldState_whenClientCancels_noLongerReceivesFrames() {
        SubscribeRequest request = SubscribeRequest.newBuilder().setClientId("unity-client").build();
        ArgumentCaptor<Runnable> cancelCaptor = ArgumentCaptor.forClass(Runnable.class);

        impl.subscribeWorldState(request, mockObserver);
        verify(mockObserver).setOnCancelHandler(cancelCaptor.capture());
        cancelCaptor.getValue().run(); // simulate client disconnect
        impl.broadcast();

        verify(mockObserver, never()).onNext(any());
    }

    @Test
    void broadcast_whenObserverAlreadyCancelled_removesWithoutPushing() {
        when(mockObserver.isCancelled()).thenReturn(false);
        SubscribeRequest request = SubscribeRequest.newBuilder().setClientId("stale").build();
        impl.subscribeWorldState(request, mockObserver);

        // simulate isCancelled becoming true between subscribe and next broadcast
        when(mockObserver.isCancelled()).thenReturn(true);

        impl.broadcast();

        verify(mockObserver, never()).onNext(any());
    }
}
