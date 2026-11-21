package com.demo.deviceservice.exception;

public class SerialNumberAlreadyUsedException extends RuntimeException {
    public SerialNumberAlreadyUsedException(String serialNumber) {
        super("Serial number already in use: " + serialNumber);
    }
}
