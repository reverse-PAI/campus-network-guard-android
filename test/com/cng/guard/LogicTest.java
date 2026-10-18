package com.cng.guard;

/** 纯逻辑单测：参数夹紧、异常文案、JSON 转义、SHA1。 */
public class LogicTest {
    static int pass, fail;

    static void eq(String name, Object got, Object exp) {
        if (String.valueOf(got).equals(String.valueOf(exp))) pass++;
        else { fail++; System.out.println("FAIL " + name + " got=[" + got + "] exp=[" + exp + "]"); }
    }

    static void contains(String name, String got, String part) {
        if (got != null && got.contains(part)) pass++;
        else { fail++; System.out.println("FAIL " + name + " got=[" + got + "]"); }
    }

    public static void main(String[] args) throws Exception {
        // 1) 参数夹紧
        eq("period 0->30", Prefs.clamp(0, 30, 3600), 30);
        eq("period 999999->3600", Prefs.clamp(999999, 30, 3600), 3600);
        eq("period 60 不变", Prefs.clamp(60, 30, 3600), 60);
        eq("fail 0->1", Prefs.clamp(0, 1, 99), 1);
        eq("fail 999->99", Prefs.clamp(999, 1, 99), 99);
        eq("cool -5->0", Prefs.clamp(-5, 0, 86400), 0);
        eq("cool 9999999->86400", Prefs.clamp(9999999, 0, 86400), 86400);

        // 1b) 节流与熔断状态机
        long now = 100000L;
        eq("未开启→跳过", Guard.shouldSkip(false, false, now - 60000, now, 8), true);
        eq("刚检测过→跳过", Guard.shouldSkip(false, true, now - 1000, now, 8), true);
        eq("超过最小间隔→放行", Guard.shouldSkip(false, true, now - 9000, now, 8), false);
        eq("手动永远放行", Guard.shouldSkip(true, false, now, now, 8), false);
        eq("最小间隔 0→放行", Guard.shouldSkip(false, true, now - 1000, now, 0), false);
        eq("时钟回拨不误判", Guard.shouldSkip(false, true, now + 5000, now, 8), false);
        eq("冷却中→是", Guard.inCooldown(now + 5000, now, false), true);
        eq("冷却中但手动→否", Guard.inCooldown(now + 5000, now, true), false);
        eq("冷却已过→否", Guard.inCooldown(now - 1, now, false), false);

        // 2) 异常文案
        eq("null->网络不通", Guard.friendly(null), "网络不通");
        eq("空串->网络不通", Guard.friendly(""), "网络不通");
        contains("DNS", Guard.friendly("http://a -> UnknownHostException: x"), "域名解析失败");
        contains("劫持", Guard.friendly("状态 200 被劫持"), "被门户劫持");
        contains("超时", Guard.friendly("java.net.SocketTimeoutException"), "连接超时");
        contains("拒绝", Guard.friendly("java.net.ConnectException: refused"), "连接被拒绝");
        eq("超长截断 80", Guard.friendly("x".repeat(200)).length(), 80);

        // 3) JSON 转义（jstr 返回带引号的 JSON 值）
        eq("普通", Srun.jstr("abc"), "\"abc\"");
        eq("引号", Srun.jstr("a\"b"), "\"a\\\"b\"");
        eq("反斜杠", Srun.jstr("a\\b"), "\"a\\\\b\"");
        eq("换行", Srun.jstr("a\nb"), "\"a\\nb\"");
        eq("中文", Srun.jstr("账号"), "\"账号\"");

        // 4) SHA1 已知向量
        eq("sha1(abc)", Srun.sha1Hex("abc"), "a9993e364706816aba3e25717850c26c9cd0d89d");
        eq("sha1(空)", Srun.sha1Hex(""), "da39a3ee5e6b4b0d3255bfef95601890afd80709");

        System.out.println("PASS=" + pass + " FAIL=" + fail);
        if (fail > 0) System.exit(1);
    }
}
