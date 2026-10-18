package com.cng.guard;

/** 纯函数单测：校园网网段判定（不依赖 Android 运行时）。 */
public class NetTest {
    static int pass, fail;

    static void eq(String name, Object got, Object exp) {
        if (String.valueOf(got).equals(String.valueOf(exp))) {
            pass++;
        } else {
            fail++;
            System.out.println("FAIL " + name + " got=" + got + " exp=" + exp);
        }
    }

    public static void main(String[] args) {
        // subnetOf：网关 IP → 网段前缀
        eq("subnetOf 10.50.1.1", Net.subnetOf("10.50.1.1"), "10.50.");
        eq("subnetOf 192.168.1.1", Net.subnetOf("192.168.1.1"), "192.168.");
        eq("subnetOf 172.16.5.1", Net.subnetOf("172.16.5.1"), "172.16.");
        eq("subnetOf 空", Net.subnetOf(""), "");
        eq("subnetOf 非法", Net.subnetOf("abc"), "");

        // prefixMatches：网关是否在已标记网段内
        eq("匹配 10.50.1.1/10.50.", Net.prefixMatches("10.50.1.1", "10.50."), true);
        eq("不匹配 10.51.1.1/10.50.", Net.prefixMatches("10.51.1.1", "10.50."), false);
        eq("空网关", Net.prefixMatches("", "10.50."), false);
        eq("空标记", Net.prefixMatches("10.50.1.1", ""), false);
        eq("多网段命中第二个", Net.prefixMatches("10.51.1.1", "10.50.,10.51."), true);
        eq("多网段都不命中", Net.prefixMatches("10.52.1.1", "10.50.,10.51."), false);
        eq("中文逗号分隔", Net.prefixMatches("10.51.1.1", "10.50.，10.51."), true);
        eq("子串不算命中", Net.prefixMatches("110.50.1.1", "10.50."), false);

        System.out.println("结果：通过 " + pass + "，失败 " + fail);
        if (fail > 0) System.exit(1);
    }
}
