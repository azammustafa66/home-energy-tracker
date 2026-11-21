package com.demo.deviceservice.grpc;

import com.demo.common.grpc.device.DeviceInfo;
import com.demo.common.grpc.device.DeviceServiceGrpc.DeviceServiceImplBase;
import com.demo.common.grpc.device.GetDeviceUserRequest;
import com.demo.common.grpc.device.GetDeviceUserResponse;
import com.demo.common.grpc.device.GetDeviceUsersRequest;
import com.demo.common.grpc.device.GetDeviceUsersResponse;
import com.demo.common.grpc.device.GetUserDevicesRequest;
import com.demo.common.grpc.device.GetUserDevicesResponse;
import com.demo.deviceservice.entity.Device;
import com.demo.deviceservice.exception.DeviceNotFoundException;
import com.demo.deviceservice.repository.DeviceRepository;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import org.springframework.grpc.server.service.GrpcService;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@GrpcService
@RequiredArgsConstructor
public class DeviceGrpcService extends DeviceServiceImplBase {

    private final DeviceRepository deviceRepository;

    @Override
    public void getDeviceUser(GetDeviceUserRequest request, StreamObserver<GetDeviceUserResponse> responseObserver) {
        final UUID deviceId;
        try {
            deviceId = UUID.fromString(request.getDeviceId());
        } catch (IllegalArgumentException e) {
            responseObserver.onError(Status.INVALID_ARGUMENT
                    .withDescription("device_id is not a valid UUID")
                    .asRuntimeException());
            return;
        }

        deviceRepository.findById(deviceId)
                .ifPresentOrElse(
                        device -> {
                            responseObserver.onNext(GetDeviceUserResponse.newBuilder()
                                    .setUserId(device.getUserId().toString())
                                    .build());
                            responseObserver.onCompleted();
                        },
                        () -> responseObserver.onError(Status.NOT_FOUND
                                .withDescription(new DeviceNotFoundException(deviceId).getMessage())
                                .asRuntimeException())
                );
    }

    @Override
    public void getDeviceUsers(GetDeviceUsersRequest request, StreamObserver<GetDeviceUsersResponse> responseObserver) {
        List<UUID> deviceIds = new ArrayList<>(request.getDeviceIdCount());
        for (String raw : request.getDeviceIdList()) {
            try {
                deviceIds.add(UUID.fromString(raw));
            } catch (IllegalArgumentException e) {
                responseObserver.onError(Status.INVALID_ARGUMENT
                        .withDescription("device_id is not a valid UUID: " + raw)
                        .asRuntimeException());
                return;
            }
        }

        GetDeviceUsersResponse.Builder builder = GetDeviceUsersResponse.newBuilder();
        for (Device device : deviceRepository.findAllById(deviceIds)) {
            builder.putDeviceToUser(device.getId().toString(), device.getUserId().toString());
        }
        responseObserver.onNext(builder.build());
        responseObserver.onCompleted();
    }

    @Override
    public void getUserDevices(GetUserDevicesRequest request, StreamObserver<GetUserDevicesResponse> responseObserver) {
        final UUID userId;
        try {
            userId = UUID.fromString(request.getUserId());
        } catch (IllegalArgumentException e) {
            responseObserver.onError(Status.INVALID_ARGUMENT
                    .withDescription("user_id is not a valid UUID")
                    .asRuntimeException());
            return;
        }

        GetUserDevicesResponse.Builder builder = GetUserDevicesResponse.newBuilder();
        for (Device device : deviceRepository.findByUserId(userId)) {
            builder.addDevices(DeviceInfo.newBuilder()
                    .setDeviceId(device.getId().toString())
                    .setDeviceType(device.getType().name())
                    .build());
        }
        responseObserver.onNext(builder.build());
        responseObserver.onCompleted();
    }
}
