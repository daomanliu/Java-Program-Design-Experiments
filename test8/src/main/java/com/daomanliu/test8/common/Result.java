package com.daomanliu.test8.common;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class Result<T> {
    private int code;
    private String msg;
    private T data;

    public static <T> Result<T> success(T data) {
        return new Result<T>(200, "success", data);
    }
    public static <T> Result<T> success(String msg, T data) {
        return new Result<T>(200, msg, data);
    }
    public static <T> Result<T> fail(int code, String msg) {
        return new Result<T>(code, msg, null);
    }
}
