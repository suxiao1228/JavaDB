package com.xiongsu.backend.common;

public class SubArray {
    public byte[] raw;
    public int start;
    public int end;

    public SubArray(byte[] raw, int start, int end) {
        this.raw = raw;
        this.start = start;
        this.end = end;
    }
}

//import java.util.Arrays;
//
//public class SubArray {
//    public final byte[] raw; // 底层数组，设为 final 防止被替换
//    public final int offset;   // 这个 SubArray 在 raw 数组中的起始偏移量
//
//    private int position;    // 当前读写位置，相对于 offset
//    private final int capacity;  // 这个 SubArray 的容量 (length)
//
//    /**
//     * @param raw 底层共享的原始数组
//     * @param start 此子数组在 raw 中的起始位置
//     * @param length 此子数组的长度
//     */
//    public SubArray(byte[] raw, int start, int length) {
//        if (raw == null || start < 0 || length < 0 || start + length > raw.length) {
//            throw new IllegalArgumentException("Invalid arguments for SubArray");
//        }
//        this.raw = raw;
//        this.offset = start;
//        this.capacity = length;
//        this.position = 0; // 初始位置在开头
//    }
//
//    // --- 核心 API ---
//
//    /**
//     * 获取此子数组的容量（长度）。
//     */
//    public int length() {
//        return this.capacity;
//    }
//
//    /**
//     * 获取或设置当前读写指针的位置。
//     */
//    public int position() {
//        return this.position;
//    }
//
//    public SubArray position(int newPosition) {
//        if (newPosition < 0 || newPosition > capacity) {
//            throw new IllegalArgumentException("Invalid new position: " + newPosition);
//        }
//        this.position = newPosition;
//        return this; // 支持链式调用
//    }
//
//    /**
//     * 从当前位置读取一个字节，并将 position 后移一位。
//     */
//    public byte get() {
//        if (position >= capacity) {
//            throw new IndexOutOfBoundsException("Buffer underflow");
//        }
//        return raw[offset + position++];
//    }
//
//    /**
//     * 从指定索引处读取一个字节（不改变 position）。
//     */
//    public byte get(int index) {
//        checkIndex(index);
//        return raw[offset + index];
//    }
//
//    /**
//     * 在当前位置写入一个字节，并将 position 后移一位。
//     */
//    public SubArray put(byte b) {
//        if (position >= capacity) {
//            throw new IndexOutOfBoundsException("Buffer overflow");
//        }
//        raw[offset + position++] = b;
//        return this;
//    }
//
//    /**
//     * 在指定索引处写入一个字节（不改变 position）。
//     */
//    public SubArray put(int index, byte b) {
//        checkIndex(index);
//        raw[offset + index] = b;
//        return this;
//    }
//
//    /**
//     * 将此 SubArray 的内容拷贝到一个全新的 byte[] 数组中。
//     */
//    public byte[] copy() {
//        return Arrays.copyOfRange(raw, offset, offset + capacity);
//    }
//
//    /**
//     * 重置 position 到 0，以便重新读取。
//     */
//    public SubArray rewind() {
//        this.position = 0;
//        return this;
//    }
//
//    // --- 辅助方法 ---
//
//    private void checkIndex(int index) {
//        if (index < 0 || index >= capacity) {
//            throw new IndexOutOfBoundsException("Index: " + index + ", Capacity: " + capacity);
//        }
//    }
//
//    @Override
//    public String toString() {
//        return "SubArray[pos=" + position + " cap=" + capacity + "]";
//    }
//}