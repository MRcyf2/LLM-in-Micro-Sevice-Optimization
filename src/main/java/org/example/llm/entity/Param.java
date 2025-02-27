package org.example.llm.entity;

import lombok.Data;

@Data
public class Param {
    private String Name;//参数名
    private String newValue;//参数值
    private String delta;

    public Param(String paramName, String newVal, String delta) {
        this.Name=paramName;
        this.newValue=newVal;
        this.delta=delta;

    }

}
