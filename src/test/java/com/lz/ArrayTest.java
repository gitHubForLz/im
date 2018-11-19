package com.lz;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ArrayTest {
    public static void main(String[] args) {
        String str ="[user:pass]";
        Map<String,Long> stringLongMap = new ConcurrentHashMap<>();
        stringLongMap.put("1",3L);
        stringLongMap.put("2",2L);
        System.out.println(stringLongMap.keySet());
    }
}
