package com.xiongsu.backend.im;

import com.xiongsu.backend.common.SubArray;
import com.xiongsu.backend.dm.DataManager;
import com.xiongsu.backend.dm.dataltem.DataItem;
import com.xiongsu.backend.tm.TransactionManager;
import com.xiongsu.backend.tm.TransactionManagerImpl;
import com.xiongsu.backend.utils.Parser;
import org.checkerframework.checker.units.qual.A;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class BPlusTree {

    //作用: 这是 B+ 树与底层存储交互的接口。它负责将 Node 对象的字节数组 (raw) 存储到磁盘或内存，并根据 UID 重新加载它们。
    // 整个 B+ 树实际上是由 DataManager 管理的一系列 DataItem（每个 DataItem 包含一个 Node 的数据）组成的。
    DataManager dm;
    //作用: 这是这个 B+ 树索引的固定标识符。它不是根节点的UID，而是存储根节点UID的那个 DataItem 的UID。
    // 这样做的好处是，即使根节点因为分裂而变化（UID改变），指向根节点的 bootUid 保持不变，外部只需要知道这个固定的 bootUid 就能找到这个 B+ 树的当前根。
    long bootUid;
    //作用: 存储了 bootUid 对应的那个 DataItem 对象。这个 DataItem 的数据内容就是一个 long 值，即当前 B+ 树的根节点的 UID。
    DataItem bootDataItem;
    //作用: 一个 ReentrantLock，用于保护 bootDataItem，确保在并发环境下对根节点 UID 的读写是线程安全的。当需要读取或更新根节点 UID 时，需要获取这个锁。
    Lock bootLock;

    /**
     * 用于在DataManager中创建一个新的空的B+树索引
     * @param dm
     * @return
     * @throws Exception
     */
    public static long create(DataManager dm) throws Exception {
        byte[] rawRoot = Node.newNilRootRaw();
        long rootUid = dm.insert(TransactionManagerImpl.SUPER_XID, rawRoot);
        return dm.insert(TransactionManagerImpl.SUPER_XID, Parser.long2Byte(rootUid));
    }

    /**
     * 用于从DataManager中加载一个已存在的B+树索引
     * @param bootUid
     * @param dm
     * @return
     * @throws Exception
     */
    public static BPlusTree load(long bootUid, DataManager dm) throws Exception {
        DataItem bootDataItem = dm.read(bootUid);
        assert bootDataItem != null;
        BPlusTree t = new BPlusTree();
        t.bootUid = bootUid;
        t.dm = dm;
        t.bootDataItem = bootDataItem;
        t.bootLock = new ReentrantLock();
        return t;
    }

    /**
     * 获取当前B+树的根节点的UID
     * @return
     */
    private long rootUid() {
        bootLock.lock();
        try {
            SubArray sa = bootDataItem.data();
            return Parser.parseLong(Arrays.copyOfRange(sa.raw, sa.start, sa.start+8));
        } finally {
            bootLock.unlock();
        }
    }

    /**
     * 用于在根节点因为分裂而产生新的根节点时，更新bootDataItem中的根节点UID
     * @param left
     * @param right
     * @param rightKey
     * @throws Exception
     */
    private void updateRootUid(long left, long right, long rightKey) throws Exception {
        bootLock.lock();
        try {
            byte[] rootRaw = Node.newRootRaw(left, right, rightKey);
            long newRootUid = dm.insert(TransactionManagerImpl.SUPER_XID, rootRaw);
            bootDataItem.before();
            SubArray diRaw = bootDataItem.data();
            System.arraycopy(Parser.long2Byte(newRootUid), 0, diRaw.raw, diRaw.start, 8);
            bootDataItem.after(TransactionManagerImpl.SUPER_XID);
        } finally {
            bootLock.unlock();
        }
    }

    /**
     * 从给定的nodeUid开始，沿着树向下搜索，找到包含给定key的潜在范围的那个叶子节点
     * @param nodeUid
     * @param key
     * @return
     * @throws Exception
     */
    private long searchLeaf(long nodeUid, long key) throws Exception {
        Node node = Node.loadNode(this, nodeUid);
        boolean isLeaf = node.isLeaf();
        node.release();

        if (isLeaf) {
            return nodeUid;
        } else {
            long next = searchNext(nodeUid, key);
            return searchLeaf(next, key);
        }
    }

    /**
     * 给定一个非叶子节点的UID和一个key,找到一个继续搜索的下一个节点的UID,这个下一个节点可能是当前节点的某个子节点，也可能是当前节点的兄弟节点
     * @param nodeUid
     * @param key
     * @return
     * @throws Exception
     */
    private long searchNext(long nodeUid, long key) throws Exception {
        while (true) {
            Node node = Node.loadNode(this, nodeUid);
            Node.SearchNextRes res = node.searchNext(key);
            node.release();
            if (res.uid != 0) return res.uid;
            nodeUid = res.siblingUid;
        }
    }

    /**
     * 搜索 B+ 树中所有与精确匹配给定 key 关联的数据项 UID。
     * @param key
     * @return
     * @throws Exception
     */
    public List<Long> search(long key) throws Exception {
        return searchRange(key, key);
    }

    /**
     * 搜索 B+ 树中所有与键在 [leftKey, rightKey] 范围内的关联的数据项 UID。
     * @param leftKey
     * @param rightKey
     * @return
     * @throws Exception
     */
    public List<Long> searchRange(long leftKey, long rightKey) throws Exception {
        long rootUid = rootUid();
        long leafUid = searchLeaf(rootUid, leftKey);
        List<Long> uids = new ArrayList<>();
        while (true) {
            Node leaf = Node.loadNode(this, leafUid);
            Node.LeafSearchRangeRes res = leaf.leafSearchRange(leftKey, rightKey);
            leaf.release();
            uids.addAll(res.uids);
            if (res.siblingUid == 0) {
                break;
            } else {
                leafUid = res.siblingUid;
            }
        }
        return uids;
    }

    /**
     * 将一个新的键值对 (key, uid) 插入到 B+ 树中。这里的 uid 可能是指向实际数据行的指针。
     * @param key
     * @param uid
     * @throws Exception
     */
    public void insert(long key, long uid) throws Exception {
        long rootUid = rootUid();
        InsertRes res = insert(rootUid, uid, key);
        assert res != null;
        if (res.newNode != 0) {
            updateRootUid(rootUid, res.newNode, res.newKey);
        }
    }

    //用于在插入操作中传递结果。当一个节点（无论是叶子还是非叶子）因为插入而发生分裂时，这个类用来报告分裂产生的新节点 (newNode 的 UID) 和作为分隔符的新键 (newKey)。
    // 如果插入成功但没有分裂，或者插入导致子节点分裂但子节点的上层节点不需要分裂，newNode 通常为 0。
    class InsertRes {
        long newNode, newKey;
    }

    /**
     * 递归执行插入操作。它负责沿着树找到正确的插入路径，并在找到子节点后进行递归调用，处理子节点返回的分裂结果
     * @param nodeUid
     * @param uid
     * @param key
     * @return
     * @throws Exception
     */
    private InsertRes insert(long nodeUid, long uid, long key) throws Exception {
        Node node = Node.loadNode(this, nodeUid);
        boolean isLeaf = node.isLeaf();
        node.release();

        InsertRes res = null;
        if (isLeaf) {
            res = insertAndSplit(nodeUid, uid, key);
        } else {
            long next = searchNext(nodeUid, key);
            InsertRes ir = insert(next, uid, key);
            if (ir.newNode != 0) {
                res = insertAndSplit(nodeUid, ir.newNode, ir.newKey);
            } else {
                res = new InsertRes();
            }
        }
        return res;
    }

    /**
     * 在给定的 nodeUid 节点中尝试插入 uid 和 key，并在必要时处理该节点的分裂。
     * @param nodeUid
     * @param uid
     * @param key
     * @return
     * @throws Exception
     */
    private InsertRes insertAndSplit(long nodeUid, long uid, long key) throws Exception {
        while (true) {
            Node node = Node.loadNode(this, nodeUid);
            Node.InsertAndSplitRes iasr = node.insertAndSplit(uid, key);
            node.release();
            if (iasr.siblingUid != 0) {
                nodeUid = iasr.siblingUid;
            } else {
                InsertRes res = new InsertRes();
                res.newNode = iasr.newSon;
                res.newKey = iasr.newKey;
                return res;
            }
        }
    }

    /**
     *  释放 B+ 树所持有的资源。
     */
    public void close() {
        bootDataItem.release();
    }
}
