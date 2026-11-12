package com.example.base;

import java.time.Duration;

public class Base {
    public final long defaultSeconds() {
        return Duration.ofSeconds(2).getSeconds();
    }
}
