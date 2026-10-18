package com.cng.guard;

import java.util.Base64;

/** 协议单测：xencode / jstr / hmacMd5 / chksum 与 Python 参考实现逐字节对比。
 *  注：android.util.Base64 在桌面 JVM 是桩，这里用 JDK Base64 + 同样的自定义字母表等价实现。 */
public class SrunTest {
    static final String ALPHA = "LVoJPiCN2R8G90yg+hmFHuacZ1OWMnrsSTXkYpUq/3dlbfKwv6xztjI7DeBE45QA";
    static final String STD   = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";

    static String b64(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            int k = STD.indexOf(c);
            sb.append(k < 0 ? c : ALPHA.charAt(k));   // '=' 等保留
        }
        return sb.toString();
    }

    public static void main(String[] args) throws Exception {
        String u = "testuser", p = "testpass", ip = "10.50.1.2", acid = "1", tok = "tok123";
        String raw = "{\"username\":" + Srun.jstr(u)
                + ",\"password\":" + Srun.jstr(p)
                + ",\"ip\":" + Srun.jstr(ip)
                + ",\"acid\":" + Srun.jstr(acid)
                + ",\"enc_ver\":\"srun_bx1\"}";
        String info = "{SRBX1}" + b64(Base64.getEncoder().encodeToString(
                Srun.xencode(raw.getBytes("UTF-8"), tok.getBytes("UTF-8"))));
        String h = Srun.hmacMd5(tok, p);
        String sum = Srun.chksum(tok, u, h, acid, ip, 200, 1, info);

        String expInfo = "{SRBX1}wg6q0QChdnC56155q5JzBncHyz/+QmzZLam5FGzDfh4/u44qlwpJhVDOn/Vfij44MqHbHWu/R8OB+Fwh4Snd4ixMRjEJvL21X05w8P4XDutL1bK63gRyXPK3RgSvpNTjQmmkn+==";
        String expH = "2027e55737a6c189c07668d187ebcf33";
        String expSum = "e84e9fd72b49e62f5171e47d36d44b0e091e0e55";

        boolean ok = true;
        if (!info.equals(expInfo)) { ok = false; System.out.println("INFO 不一致\n  got=" + info + "\n  exp=" + expInfo); }
        if (!h.equals(expH)) { ok = false; System.out.println("HMD5 不一致 got=" + h); }
        if (!sum.equals(expSum)) { ok = false; System.out.println("SUM 不一致 got=" + sum); }
        System.out.println(ok ? "协议对比：xencode / hmacMd5 / chksum 全部与参考实现一致 ✅" : "协议对比：存在差异 ❌");
        if (!ok) System.exit(1);
    }
}
