package com.fastrag.module.knowledge.parser;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/**
 * 文档内嵌图片频次去重（模板与频次规则）：
 * <p>同文档内 MD5 / pHash 重复出现次数 ≥ {@link #REPEAT_THRESHOLD} 次的图片
 * 视为模板装饰（公司公文模板 Logo、全局水印、每页重复的装饰图），<b>整组过滤</b>。</p>
 *
 * <p>两级判定：</p>
 * <ol>
 *   <li><b>MD5 精确去重</b>：字节完全相同的图片（同一 logo/水印的精确重复），成本低；</li>
 *   <li><b>pHash 感知聚类</b>：汉明距离 ≤ {@link #PHASH_DISTANCE_THRESHOLD} 的图片视为相似
 *       （缩放/重压缩导致的字节不同），用并查集聚类后组大小 ≥ 阈值即过滤。
 *       仅对可解码图片计算（EMF/WMF/SVG 等矢量格式跳过，解码失败返回的全 0 哈希不参与聚类）。</li>
 * </ol>
 */
public final class ImageDedup {

    /** 频次阈值：同文档内重复出现 ≥ 该次数视为模板装饰（logo/水印） */
    public static final int REPEAT_THRESHOLD = 3;
    /** pHash 汉明距离阈值：≤ 该值视为同一图片（缩放/重压缩容忍度） */
    public static final int PHASH_DISTANCE_THRESHOLD = 8;
    /** computePerceptualHash 解码失败时的占位哈希（不参与感知聚类） */
    private static final String HASH_FAILED = "0".repeat(16);

    private ImageDedup() {
    }

    /**
     * 计算应过滤的重复图片 key 集合。
     *
     * @param keyToData 图片 key → 图片原始字节
     * @return 应过滤（整组剔除）的图片 key 集合；不足阈值或无可解码图片时返回空集合
     */
    public static Set<String> findRepeatedKeys(Map<String, byte[]> keyToData) {
        if (keyToData == null || keyToData.size() < REPEAT_THRESHOLD) {
            return Set.of();
        }
        Set<String> repeated = new HashSet<>();

        // ① MD5 精确分组：字节完全相同的图片
        Map<String, List<String>> byMd5 = new HashMap<>();
        for (Map.Entry<String, byte[]> e : keyToData.entrySet()) {
            byMd5.computeIfAbsent(md5(e.getValue()), k -> new ArrayList<>()).add(e.getKey());
        }
        for (List<String> group : byMd5.values()) {
            if (group.size() >= REPEAT_THRESHOLD) {
                repeated.addAll(group);
            }
        }

        // ② pHash 感知聚类：剩余图片中缩放/重压缩造成的相似重复
        List<Map.Entry<String, String>> hashes = new ArrayList<>();
        for (Map.Entry<String, byte[]> e : keyToData.entrySet()) {
            if (repeated.contains(e.getKey())) continue;
            String hash = MediaExtractor.computePerceptualHash(e.getValue());
            if (!HASH_FAILED.equals(hash)) {
                hashes.add(Map.entry(e.getKey(), hash));
            }
        }

        // 两两比较 + 并查集聚类（文档内图片数量有限，O(n²) 可接受）
        int n = hashes.size();
        int[] parent = new int[n];
        for (int i = 0; i < n; i++) parent[i] = i;
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                if (MediaExtractor.hammingDistance(hashes.get(i).getValue(), hashes.get(j).getValue())
                        <= PHASH_DISTANCE_THRESHOLD) {
                    union(parent, i, j);
                }
            }
        }
        Map<Integer, List<Integer>> clusters = new HashMap<>();
        for (int i = 0; i < n; i++) {
            clusters.computeIfAbsent(find(parent, i), k -> new ArrayList<>()).add(i);
        }
        for (List<Integer> cluster : clusters.values()) {
            if (cluster.size() >= REPEAT_THRESHOLD) {
                for (int idx : cluster) {
                    repeated.add(hashes.get(idx).getKey());
                }
            }
        }
        return repeated;
    }

    private static String md5(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            return java.util.HexFormat.of().formatHex(md.digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 algorithm unavailable", e);
        }
    }

    private static int find(int[] parent, int i) {
        while (parent[i] != i) {
            parent[i] = parent[parent[i]]; // 路径压缩
            i = parent[i];
        }
        return i;
    }

    private static void union(int[] parent, int a, int b) {
        int ra = find(parent, a);
        int rb = find(parent, b);
        if (ra != rb) {
            parent[ra] = rb;
        }
    }
}
