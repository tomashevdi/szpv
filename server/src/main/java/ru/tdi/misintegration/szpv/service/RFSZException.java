package ru.tdi.misintegration.szpv.service;

public class RFSZException extends RuntimeException {
    private Integer code;
    private String message;

    public RFSZException(Integer code, String message) {
        this.code = code;
        this.message = message;
    }


    public Integer getCode() {
        return code;
    }

    @Override
    public String getMessage() {
        return message;
    }

    @Override
    public String toString() {
        return "RFSZException: ["+String.valueOf(code)+" ; "+message+"]";
    }
}
