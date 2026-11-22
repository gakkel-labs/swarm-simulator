package fr.gakkel.swarmsimulator.swarmserver.server;

import fr.gakkel.swarmsimulator.swarmserver.domain.World;
import io.gakkel.swarm.contracts.v1.SubscribeRequest;
import io.gakkel.swarm.contracts.v1.WorldState;
import io.grpc.Status;
import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SwarmObserverImplTest {

    @Mock private ScheduledExecutorService mockExecutor;
    @Mock private ServerCallStreamObserver<WorldState> mockObserver;
    @Mock private StreamObserver<WorldState> plainObserver;

    private SwarmObserverImpl impl;

    @BeforeEach
    void setUp() {
        World world = new World(100, 100, 50);
        // mock executor prevents background scheduling — broadcast() called manually in tests
        impl = new SwarmObserverImpl(world, () -> 0.0, mockExecutor);
    }

    @Test
    void subscribeWorldState_newClient_receivesWorldStateOnBroadcast() {
        when(mockObserver.isCancelled()).thenReturn(false);
        when(mockObserver.isReady()).thenReturn(true);
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

    @Test
    void broadcast_whenBuildingTheStateThrows_doesNotPropagateTheException() {
        // scheduleAtFixedRate stops a task for good, with no log, if it throws once.
        World world = new World(100, 100, 50);
        SwarmObserverImpl failingImpl = new SwarmObserverImpl(world, () -> {
            throw new IllegalStateException("metric not ready");
        }, mockExecutor);
        failingImpl.subscribeWorldState(SubscribeRequest.newBuilder().setClientId("unity").build(), mockObserver);

        assertDoesNotThrow(failingImpl::broadcast);
    }

    @Test
    void constructor_doesNotStartTheBroadcaster() {
        ScheduledExecutorService untouchedExecutor = mock(ScheduledExecutorService.class);

        new SwarmObserverImpl(new World(100, 100, 50), () -> 0.0, untouchedExecutor);

        verifyNoInteractions(untouchedExecutor);
    }

    @Test
    void start_schedulesTheBroadcastAtTheStreamRate() {
        impl.start();

        verify(mockExecutor).scheduleAtFixedRate(any(Runnable.class), eq(0L),
                eq(1000L / SwarmObserverImpl.STREAM_RATE_HZ), eq(TimeUnit.MILLISECONDS));
    }

    @Test
    void broadcast_whenClientIsNotReady_skipsTheFrameAndKeepsTheClient() {
        // A slow client is not ready: sending anyway would pile frames up in server memory.
        when(mockObserver.isCancelled()).thenReturn(false);
        when(mockObserver.isReady()).thenReturn(false, true);
        impl.subscribeWorldState(SubscribeRequest.newBuilder().setClientId("slow").build(), mockObserver);

        impl.broadcast();
        impl.broadcast();

        verify(mockObserver, times(1)).onNext(any(WorldState.class));
    }

    @Test
    void subscribeWorldState_withAPlainStreamObserver_failsWithInternalError() {
        ArgumentCaptor<Throwable> errorCaptor = ArgumentCaptor.forClass(Throwable.class);

        impl.subscribeWorldState(SubscribeRequest.newBuilder().setClientId("odd").build(), plainObserver);

        verify(plainObserver).onError(errorCaptor.capture());
        assertThat(Status.fromThrowable(errorCaptor.getValue()).getCode()).isEqualTo(Status.Code.INTERNAL);
    }

    @Test
    void stop_closesTheStreamOfEachClientOnTheBroadcasterThread() {
        impl.subscribeWorldState(SubscribeRequest.newBuilder().setClientId("unity").build(),
                mockObserver);
        ArgumentCaptor<Runnable> closeTaskCaptor = ArgumentCaptor.forClass(Runnable.class);

        impl.stop();
        verify(mockExecutor).execute(closeTaskCaptor.capture());
        closeTaskCaptor.getValue().run(); // what the broadcaster thread does next

        verify(mockObserver).onCompleted();
        verify(mockExecutor).shutdown();
    }
}
