package fr.gakkel.swarmsimulator.swarmserver.server;

import fr.gakkel.swarmsimulator.contracts.PingRequest;
import fr.gakkel.swarmsimulator.contracts.PingResponse;
import fr.gakkel.swarmsimulator.contracts.PingServiceGrpc;
import io.grpc.stub.StreamObserver;

public class PingServiceImpl extends PingServiceGrpc.PingServiceImplBase {

    @Override
    public void sendPing(PingRequest request, StreamObserver<PingResponse> responseObserver) {
        String reply = "pong";
        PingResponse response = PingResponse.newBuilder()
                .setReply(reply)
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }
}
