package com.example.mock.api;

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue;

public enum ChannelType {
    EMAIL,
    SMS,
    KAKAO,
    @JsonEnumDefaultValue
    UNKNOWN
}
