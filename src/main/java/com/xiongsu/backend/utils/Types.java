package com.xiongsu.backend.utils;

public class Types {
    public static long addressToUid(int pgno, short offset) {
        long u0 = (long) pgno;
        long u1 = (long) offset;
        return u0<<32 | u1;//或运算是全0则0，见1则1
    }
}
