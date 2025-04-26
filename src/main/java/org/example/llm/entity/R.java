package org.example.llm.entity;

import lombok.Data;

import java.util.Map;

@Data
public class R<T> {
    private int code;
    private String msg;
    private T data;

    public R(int code, String msg, T data) {
        this.code = code;
        this.msg = msg;
        this.data = data;
    }

    public static <T> R<T> success(T data) {
        return new R<>(200, "success", data);
    }

    public static <T> R<T> error(T data) {
        return new R<>(200, "error", data);
    }
}