package com.association.workflow.enumerations;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Arrays;

@AllArgsConstructor
@NoArgsConstructor
@Getter
public enum EnumForApproveType {
    JOIN_ASSOCIATION(1, "加入社团请求"),
    HOLD_ACTIVITY(2,"发起活动请求"),
    COMPLAIN(3,"申诉请求"),
    CERTIFIED(4,"资格证请求"), // 教师认证
    CREATE_ASSOCIATION(5,"创建社团"),
    UNKNOWN(-99,"未知")
    ;
    private int code;
    private String info;

    public static EnumForApproveType parse(int code){
        try {
           return Arrays.stream(EnumForApproveType.values()).filter(enumeration -> enumeration.getCode() == code).findAny().orElse(EnumForApproveType.UNKNOWN);
        }catch (Exception e){
            return EnumForApproveType.UNKNOWN;
        }
    }

}
