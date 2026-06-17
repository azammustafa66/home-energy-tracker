package com.demo.userservice.grpc;

import com.demo.common.grpc.energyThreshold.EnergyThresholdServiceGrpc;
import com.demo.common.grpc.energyThreshold.GetUserThresholdRequest;
import com.demo.common.grpc.energyThreshold.GetUserThresholdResponse;
import com.demo.common.grpc.energyThreshold.GetUserThresholdsRequest;
import com.demo.common.grpc.energyThreshold.GetUserThresholdsResponse;
import com.demo.common.grpc.energyThreshold.UserThresholdInfo;
import com.demo.userservice.entity.User;
import com.demo.userservice.repository.UserRepository;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import org.springframework.grpc.server.service.GrpcService;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@GrpcService
@RequiredArgsConstructor
public class AlertThresholdService extends EnergyThresholdServiceGrpc.EnergyThresholdServiceImplBase {

    private final UserRepository userRepository;

    @Override
    public void getUserEnergyThreshold(GetUserThresholdRequest request, StreamObserver<GetUserThresholdResponse> responseObserver) {
        final UUID userId;
        try {
            userId = UUID.fromString(request.getUserId());
        } catch (IllegalArgumentException e) {
            responseObserver.onError(Status.INVALID_ARGUMENT.withDescription(e.getMessage()).asRuntimeException());
            return;
        }

        userRepository.findById(userId).ifPresentOrElse(user -> {
            GetUserThresholdResponse.Builder builder = GetUserThresholdResponse.newBuilder()
                    .setEmail(user.getEmail());
            if (user.getEnergyAlertThreshold() != null) {
                builder.setAlertThreshold(user.getEnergyAlertThreshold());
            }
            responseObserver.onNext(builder.build());
            responseObserver.onCompleted();
        }, () -> responseObserver.onError(Status.NOT_FOUND
                .withDescription("User not found: " + userId)
                .asRuntimeException()));
    }

    @Override
    public void getUserEnergyThresholds(GetUserThresholdsRequest request, StreamObserver<GetUserThresholdsResponse> responseObserver) {
        List<UUID> userIds = new ArrayList<>(request.getUserIdCount());
        for (String raw : request.getUserIdList()) {
            try {
                userIds.add(UUID.fromString(raw));
            } catch (IllegalArgumentException e) {
                responseObserver.onError(Status.INVALID_ARGUMENT
                        .withDescription("user_id is not a valid UUID: " + raw)
                        .asRuntimeException());
                return;
            }
        }

        GetUserThresholdsResponse.Builder builder = GetUserThresholdsResponse.newBuilder();
        for (User user : userRepository.findAllById(userIds)) {
            if (!user.isAlerting()) continue;
            UserThresholdInfo.Builder info = UserThresholdInfo.newBuilder().setEmail(user.getEmail());
            if (user.getEnergyAlertThreshold() != null) {
                info.setAlertThreshold(user.getEnergyAlertThreshold());
            }
            builder.putThresholds(user.getId().toString(), info.build());
        }
        responseObserver.onNext(builder.build());
        responseObserver.onCompleted();
    }
}
