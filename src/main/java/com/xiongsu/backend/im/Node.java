package com.xiongsu.backend.im;

import com.xiongsu.backend.common.SubArray;
import com.xiongsu.backend.dm.dataltem.DataItem;
import com.xiongsu.backend.tm.TransactionManagerImpl;
import com.xiongsu.backend.utils.Parser;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Node 结构如下：
 * [LeafFlag] [KeyNumber] [SiblingUid]
 * [Son0][Key0][Son1][Key1]...[SonN][KeyN]
 */
public class Node {
    static final int IS_LEAF_OFFSET = 0;// 表示该节点是否为叶子结点
    static final int NO_KEYS_OFFSET = IS_LEAF_OFFSET+1;// 表示该节点中key的个数
    static final int SIBLING_OFFSET = NO_KEYS_OFFSET+2;// 表示节点的兄弟节点的UID属性
    static final int NODE_HEADER_SIZE = SIBLING_OFFSET+8;// 表示节点头部的大小的常量

    static final int BALANCE_NUMBER = 32; // 节点的平衡因子的常量，一个节点最多可以包含32个key
    static final int NODE_SIZE = NODE_HEADER_SIZE + (2*8)*(BALANCE_NUMBER*2+2);// 节点的大小

    BPlusTree tree;
    DataItem dataItem;
    SubArray raw;
    long uid;

    /**
     * 设置是否为叶子节点，1表示是叶子节点，0表示非叶子节点
     * @param raw
     * @param isLeaf
     */
    static void setRawIsLeaf(SubArray raw, boolean isLeaf) {
        if (isLeaf) {
            raw.raw[raw.start + IS_LEAF_OFFSET] = (byte)1;
        } else {
            raw.raw[raw.start + IS_LEAF_OFFSET] = (byte)0;
        }
    }

    /**
     * 判断是否为叶子节点
     * @param raw
     * @return
     */
    static boolean getRawIfLeaf(SubArray raw) {
        return raw.raw[raw.start + IS_LEAF_OFFSET] == (byte)1;
    }

    /**
     * 设置节点个数
     * @param raw
     * @param noKeys
     */
    static void setRawNoKeys(SubArray raw, int noKeys) {
        System.arraycopy(Parser.short2Byte((short) noKeys), 0, raw.raw, raw.start+NO_KEYS_OFFSET, 2);
    }

    /**
     * 获取节点个数
     * @param raw
     * @return
     */
    static int getRawNoKeys(SubArray raw) {
        return (int)Parser.parseShort(Arrays.copyOfRange(raw.raw, raw.start+SIBLING_OFFSET, raw.start+NO_KEYS_OFFSET+2));
    }

    /**
     * 设置兄弟节点的uid,占用八个字节
     * @param raw
     * @param sibling
     */
    static void setRawSibling(SubArray raw, long sibling) {
        System.arraycopy(Parser.long2Byte(sibling), 0, raw.raw, raw.start+SIBLING_OFFSET, 8);
    }

    /**
     * 获取兄弟节点的uid
     * @param raw
     * @return
     */
    static long getRawSibling(SubArray raw) {
        return Parser.parseLong(Arrays.copyOfRange(raw.raw, raw.start+SIBLING_OFFSET, raw.start+SIBLING_OFFSET+8));
    }

    /**
     * 设置第k个子节点的UID
     * 注: k 是从0开始的
     * @param raw 节点的原始字节数组
     * @param uid 要设置的UID
     * @param kth 子节点的索引
     *            raw.start是字节数组的起始位置，NODE_HEADER_SIZE是节点头部的大小
     *            kth * (8*2)是第k个子节点或键的偏移量，所以，raw.start + NODE_HEADER_SIZE + ket*(8*2)
     *            就是第k个子节点或键再字节数组中的起始位置
     */
    static void setRawKthSon(SubArray raw, long uid, int kth) {
        int offset = raw.start+NODE_HEADER_SIZE+kth*(8*2);
        System.arraycopy(Parser.long2Byte(uid), 0, raw.raw, offset, 8);
    }

    /**
     * 获取第k个字节点的UID
     * @param raw
     * @param kth
     * @return
     */
    static long getRawKthSon(SubArray raw, int kth) {
        int offset = raw.start+NODE_HEADER_SIZE+kth*(8*2);
        return Parser.parseLong(Arrays.copyOfRange(raw.raw, offset, offset+8));
    }

    /**
     * 设置第k个键的值
     * @param raw
     * @param key
     * @param kth
     */
    static void setRawKthKey(SubArray raw, long key, int kth) {
        int offset = raw.start+NODE_HEADER_SIZE+kth*(8*2)+8;
        System.arraycopy(Parser.long2Byte(key), 0, raw.raw, offset, 8);
    }

    /**
     * 获取第k个键的值
     * @param raw
     * @param kth
     * @return
     */
    static long getRawKthKey(SubArray raw, int kth) {
        int offset = raw.start+NODE_HEADER_SIZE+kth*(8*2)+8;
        return Parser.parseLong(Arrays.copyOfRange(raw.raw, offset, offset+8));
    }

    /**
     * 从一个节点的原始字节数组中复制一部分数据到另一个节点的原始字节数组中
     * @param from
     * @param to
     * @param kth
     */
    static void copyRawFromKth(SubArray from, SubArray to, int kth) {
        //计算要复制的数据再源节点的原始字节数组中的起始位置
        int offset = from.start+NODE_HEADER_SIZE+kth*(8*2);
        //将源节点的原始字节数组中的数据复制到目标节点的原始字节数组中
        //复制的数据包括从起始位置到源节点的原始字节数组的末尾的所有数据
        System.arraycopy(from.raw, offset, to.raw, to.start+NODE_HEADER_SIZE, from.end-offset);
    }

    static void shiftRawKth(SubArray raw, int kth) {
        int begin = raw.start+NODE_HEADER_SIZE+(kth+1)*(8*2);
        int end = raw.start+NODE_SIZE-1;
        for(int i = end; i >= begin; i --) {
            raw.raw[i] = raw.raw[i-(8*2)];
        }
    }


    /*
        [LeafFlag: 0]
        [KeyNumber: 2]
        [SiblingUid: 0]
        [Son0: left][Key0: key][Son1: right][Key1: MAX_VALUE]

        注：一个简单的演示

                (key)
               /     \
              /       \
             /         \
          [left]     [right]
     */


    /**
     * 创建一个新的根节点的原始字节数组
     * 这个新的根节点包含两个子节点，它们的键分别是’key‘和'Long.MAX_VALUE'， UID分别是'left'和'right'
     * @param left
     * @param right
     * @param key
     * @return
     */
    static byte[] newRootRaw(long left, long right, long key)  {
        //创建一个新的字节数组，大小为节点的大小
        SubArray raw = new SubArray(new byte[NODE_SIZE], 0, NODE_SIZE);
        setRawIsLeaf(raw, false);//设置节点为非叶子节点
        setRawNoKeys(raw, 2);//设置节点的键的数量为2
        setRawSibling(raw, 0);//设置节点的兄弟节点的UID为0
        setRawKthSon(raw, left, 0);//设置第0个子节点的UID为left
        setRawKthKey(raw, key, 0);//设置第0个键的值为key
        setRawKthSon(raw, right, 1);//设置第1个子节点的UID为right
        setRawKthKey(raw, Long.MAX_VALUE, 1);//设置第1个键的值为Long.MAX_VALUE

        return raw.raw;//返回新创建的根节点的原始字节数组
    }

    /**
     * 创建一个新的空根节点的原始字节数组，这个新的根节点没有子节点和键
     * @return
     */
    static byte[] newNilRootRaw()  {
        SubArray raw = new SubArray(new byte[NODE_SIZE], 0, NODE_SIZE);//创建一个新的字节数组，大小为节点的大小
        setRawIsLeaf(raw, true);//设置节点为叶子节点
        setRawNoKeys(raw, 0);//设置节点的键的数量为0
        setRawSibling(raw, 0);//设置节点的兄弟节点的UID为0

        return raw.raw;//返回新创建的空根节点的原始字节数组
    }

    static Node loadNode(BPlusTree bTree, long uid) throws Exception {
        DataItem di = bTree.dm.read(uid);
        assert di != null;
        Node n = new Node();
        n.tree = bTree;
        n.dataItem = di;
        n.raw = di.data();
        n.uid = uid;
        return n;
    }

    public void release() {
        dataItem.release();
    }

    public boolean isLeaf() {
        dataItem.rLock();
        try {
            return getRawIfLeaf(raw);
        } finally {
            dataItem.rUnLock();
        }
    }

    class SearchNextRes {
        long uid;
        long siblingUid;
    }

    /**
     * 在B+树的节点中搜索下一个节点的方法
     * @param key
     * @return
     */
    public SearchNextRes searchNext(long key) {
        dataItem.rLock();//获取节点的读锁
        try {
            SearchNextRes res = new SearchNextRes();// 创建一个SearchNextRes对象，用于存储搜索结果
            int noKeys = getRawNoKeys(raw);// 获取节点个数
            for(int i = 0; i < noKeys; i ++) {
                long ik = getRawKthKey(raw, i);// 获取第i个key的值
                if(key < ik) {// 如果key小于ik，那么找到了下一个节点
                    res.uid = getRawKthSon(raw, i);// 设置下一个节点的UID
                    res.siblingUid = 0;// 设置兄弟节点的UID为0
                    return res;// 返回搜索结果
                }
            }
            res.uid = 0;// 如果没有找到下一个节点，设置uid为0
            res.siblingUid = getRawSibling(raw); // 设置兄弟节点的UID为当前节点的兄弟节点的UID
            return res; // 返回搜索结果

        } finally {
            dataItem.rUnLock();//释放节点的读锁
        }
    }

    class LeafSearchRangeRes {
        List<Long> uids;
        long siblingUid;
    }

    /**
     * 在B+树的叶子节点中搜索一个键值范围的办法
     * @param leftKey
     * @param rightKey
     * @return
     */
    public LeafSearchRangeRes leafSearchRange(long leftKey, long rightKey) {
        dataItem.rLock();//获取数据项的读锁
        try {
            int noKeys = getRawNoKeys(raw);//获取节点中的键的数量
            int kth = 0;
            while(kth < noKeys) {//找到第一个大于等于左键的键
                long ik = getRawKthKey(raw, kth);
                if(ik >= leftKey) {
                    break;
                }
                kth ++;
            }
            List<Long> uids = new ArrayList<>();//创建一个列表，用于存储所有在键值范围内的子节点的UID
            while(kth < noKeys) {//遍历所有的键，将所有小于等于右键的键对应的子节点的UID添加到列表中
                long ik = getRawKthKey(raw, kth);
                if(ik <= rightKey) {
                    uids.add(getRawKthSon(raw, kth));
                    kth ++;
                } else {
                    break;
                }
            }
            long siblingUid = 0;//如果所有的键都被遍历过，获取兄弟节点的UID
            if(kth == noKeys) {
                siblingUid = getRawSibling(raw);
            }
            LeafSearchRangeRes res = new LeafSearchRangeRes();//创建一个LeafSearchRangeRes对象，用于存储搜索结果
            res.uids = uids;
            res.siblingUid = siblingUid;
            return res;//返回搜索结果
        } finally {
            dataItem.rUnLock();//释放数据项的读锁
        }
    }

    class InsertAndSplitRes {
        long siblingUid, newSon, newKey;
    }

    /**
     * 在B+树的节点中插入一个键值对，并在需要时分裂节点
     * @param uid
     * @param key
     * @return
     * @throws Exception
     */
    public InsertAndSplitRes insertAndSplit(long uid, long key) throws Exception {
        boolean success = false;// 创建一个标志位，用于标记插入操作是否成功
        Exception err = null;// 创建一个异常对象，用于存储在插入或分裂节点时发生的异常
        InsertAndSplitRes res = new InsertAndSplitRes();// 创建一个InsertAndSplitRes对象，用于存储插入和分裂节点的结果

        dataItem.before();// 在数据项上设置一个保存点
        try {
            success = insert(uid, key); // 尝试在节点中插入键值对，并获取插入结果
            if(!success) {// 如果插入失败，设置兄弟节点的UID，并返回结果
                res.siblingUid = getRawSibling(raw);
                return res;
            }
            if(needSplit()) {// 如果需要分裂节点
                try {
                    SplitRes r = split();// 分裂节点，并获取分裂结果
                    res.newSon = r.newSon;// 设置新节点的UID和新键，并返回结果
                    res.newKey = r.newKey;
                    return res;
                } catch(Exception e) {
                    err = e;// 如果在分裂节点时发生错误，保存异常并抛出
                    throw e;
                }
            } else {
                return res;// 如果不需要分裂节点，直接返回结果
            }
        } finally {
            if(err == null && success) {// 如果没有发生错误并且插入成功，提交数据项的修改
                dataItem.after(TransactionManagerImpl.SUPER_XID);
            } else { // 如果发生错误或插入失败，回滚数据项的修改
                dataItem.unBefore();
            }
        }
    }

    /**
     * 在B+树的节点中插入一个键值对的方法
     * @param uid
     * @param key
     * @return
     */
    private boolean insert(long uid, long key) {
        int noKeys = getRawNoKeys(raw);// 获取节点中的键的数量
        int kth = 0;// 初始化插入位置的索引
        while(kth < noKeys) {// 找到第一个大于或等于要插入的键的键的位置
            long ik = getRawKthKey(raw, kth);
            if(ik < key) {
                kth ++;
            } else {
                break;
            }
        }
        if(kth == noKeys && getRawSibling(raw) != 0) return false;// 如果所有的键都被遍历过，并且存在兄弟节点，插入失败

        if(getRawIfLeaf(raw)) {// 如果节点是叶子节点
            shiftRawKth(raw, kth);// 在插入位置后的所有键和子节点向后移动一位
            setRawKthKey(raw, key, kth); // 在插入位置插入新的键和子节点的UID
            setRawKthSon(raw, uid, kth);
            setRawNoKeys(raw, noKeys+1);// 更新节点中的键的数量
        } else {            // 如果节点是非叶子节点
            long kk = getRawKthKey(raw, kth);// 获取插入位置的键
            setRawKthKey(raw, key, kth);// 在插入位置插入新的键
            shiftRawKth(raw, kth+1);// 在插入位置后的所有键和子节点向后移动一位
            setRawKthKey(raw, kk, kth+1);// 在插入位置的下一个位置插入原来的键和新的子节点的UID
            setRawKthSon(raw, uid, kth+1);
            setRawNoKeys(raw, noKeys+1);// 更新节点中的键的数量
        }
        return true;// 插入成功
    }

    private boolean needSplit() {
        return BALANCE_NUMBER*2 == getRawNoKeys(raw);
    }

    class SplitRes {
        long newSon, newKey;
    }

    /**
     * 分裂B+树的节点
     * 当一个节点的键的数量达到'BALANCE_NUMBER * 2'时， 就意味着这个节点已经满了，需要进行分裂操作
     * 分裂操作的目的是将一个满的节点分裂成两个节点，每个节点包含一半的键
     * @return
     * @throws Exception
     */
    private SplitRes split() throws Exception {
        SubArray nodeRaw = new SubArray(new byte[NODE_SIZE], 0, NODE_SIZE);// 创建一个新的字节数组，用于存储新节点的原始数据
        setRawIsLeaf(nodeRaw, getRawIfLeaf(raw));// 设置新节点的叶子节点标志，与原节点相同
        setRawNoKeys(nodeRaw, BALANCE_NUMBER); // 设置新节点的键的数量为BALANCE_NUMBER
        setRawSibling(nodeRaw, getRawSibling(raw)); // 设置新节点的兄弟节点的UID，与原节点的兄弟节点的UID相同
        copyRawFromKth(raw, nodeRaw, BALANCE_NUMBER);    // 从原节点的原始字节数组中复制一部分数据到新节点的原始字节数组中
        long son = tree.dm.insert(TransactionManagerImpl.SUPER_XID, nodeRaw.raw);// 在数据管理器中插入新节点的原始数据，并获取新节点的UID
        setRawNoKeys(raw, BALANCE_NUMBER);// 更新原节点的键的数量为BALANCE_NUMBER
        setRawSibling(raw, son);// 更新原节点的兄弟节点的UID为新节点的UID

        SplitRes res = new SplitRes();// 创建一个SplitRes对象，用于存储分裂结果
        res.newSon = son;// 设置新节点的UID
        res.newKey = getRawKthKey(nodeRaw, 0);// 设置新键为新节点的第一个键的值
        return res;// 返回分裂结果
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Is leaf: ").append(getRawIfLeaf(raw)).append("\n");
        int KeyNumber = getRawNoKeys(raw);
        sb.append("KeyNumber: ").append(KeyNumber).append("\n");
        sb.append("sibling: ").append(getRawSibling(raw)).append("\n");
        for(int i = 0; i < KeyNumber; i ++) {
            sb.append("son: ").append(getRawKthSon(raw, i)).append(", key: ").append(getRawKthKey(raw, i)).append("\n");
        }
        return sb.toString();
    }
}
