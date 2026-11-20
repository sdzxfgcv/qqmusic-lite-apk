package com.ima.qqmusiclite;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 直接对接 QQ 音乐官网（y.qq.com）网页版接口。
 * 仅获取公开数据与使用者自身账号权限内的播放地址，不做任何鉴权绕过。
 */
public class Api {

    private static final String UA =
            "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) "
                    + "Chrome/120.0.0.0 Mobile Safari/537.36";
    private static final String REFERER = "https://y.qq.com/";

    private static final String URL_SEARCH =
            "https://c.y.qq.com/soso/fcgi-bin/client_search_cp";
    private static final String URL_MUSICU =
            "https://u.y.qq.com/cgi-bin/musicu.fcg";
    private static final String URL_TOPLIST =
            "https://c.y.qq.com/v8/fcg-bin/fcg_v8_toplist_cp.fcg";
    private static final String URL_LYRIC =
            "https://c.y.qq.com/lyric/fcgi-bin/fcg_query_lyric_new.fcg";

    // ---------------- HTTP ----------------

    private static String fetch(String url, byte[] body) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(12000);
        conn.setReadTimeout(18000);
        conn.setInstanceFollowRedirects(true);
        conn.setRequestProperty("User-Agent", UA);
        conn.setRequestProperty("Referer", REFERER);
        conn.setRequestProperty("Accept", "*/*");
        conn.setRequestProperty("Accept-Encoding", "identity");

        if (body != null) {
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json;charset=UTF-8");
            try (OutputStream os = conn.getOutputStream()) {
                os.write(body);
            }
        }

        int code = conn.getResponseCode();
        InputStream is = (code >= 400) ? conn.getErrorStream() : conn.getInputStream();
        if (is == null) {
            throw new IOException("HTTP " + code);
        }
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = is.read(buf)) > 0) {
            bos.write(buf, 0, n);
        }
        is.close();
        conn.disconnect();
        return new String(bos.toByteArray(), StandardCharsets.UTF_8);
    }

    // ---------------- 搜索 ----------------

    public static List<Song> search(String keyword, int limit) throws Exception {
        String url = URL_SEARCH
                + "?format=json&n=" + limit
                + "&p=1&w=" + URLEncoder.encode(keyword, "UTF-8")
                + "&new_json=1&cr=1&g_tk=5381&t=0&flag_qc=0";
        JSONObject root = new JSONObject(fetch(url, null));
        List<Song> out = new ArrayList<>();
        JSONObject data = root.optJSONObject("data");
        if (data == null) return out;
        JSONObject song = data.optJSONObject("song");
        if (song == null) return out;
        JSONArray list = song.optJSONArray("list");
        if (list == null) return out;
        for (int i = 0; i < list.length(); i++) {
            JSONObject o = list.optJSONObject(i);
            if (o != null) out.add(parse(o));
        }
        return out;
    }

    // ---------------- 排行榜 ----------------

    public static List<Song> topList(int topId, int num) throws Exception {
        String url = URL_TOPLIST
                + "?format=json&topid=" + topId
                + "&page=detail&type=top&song_begin=0&song_num=" + num;
        JSONObject root = new JSONObject(fetch(url, null));
        List<Song> out = new ArrayList<>();
        JSONArray arr = root.optJSONArray("songlist");
        if (arr == null) return out;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject wrap = arr.optJSONObject(i);
            if (wrap == null) continue;
            JSONObject o = wrap.optJSONObject("data");
            if (o == null) o = wrap;
            out.add(parse(o));
        }
        return out;
    }

    // ---------------- 歌曲解析 ----------------

    private static Song parse(JSONObject o) {
        Song s = new Song();
        s.mid = firstNonEmpty(o.optString("mid"), o.optString("songmid"));
        s.name = firstNonEmpty(o.optString("name"), o.optString("songname"), o.optString("title"));
        s.interval = o.optInt("interval", 0);

        JSONArray singers = o.optJSONArray("singer");
        if (singers != null) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < singers.length(); i++) {
                JSONObject sg = singers.optJSONObject(i);
                if (sg == null) continue;
                String nm = sg.optString("name");
                if (nm.isEmpty()) continue;
                if (sb.length() > 0) sb.append(" / ");
                sb.append(nm);
            }
            s.singer = sb.toString();
        }
        if (s.singer.isEmpty()) s.singer = o.optString("singername");

        JSONObject album = o.optJSONObject("album");
        if (album != null) s.albumMid = album.optString("mid");
        if (s.albumMid.isEmpty()) s.albumMid = o.optString("albummid");
        return s;
    }

    private static String firstNonEmpty(String... vals) {
        for (String v : vals) {
            if (v != null && !v.isEmpty() && !"null".equals(v)) return v;
        }
        return "";
    }

    // ---------------- 播放地址 ----------------

    /**
     * @return 可播放的 http(s) 地址；若为 VIP / 版权受限返回 null
     */
    public static String playUrl(String songMid) throws Exception {
        JSONObject param = new JSONObject();
        param.put("guid", "10000");
        JSONArray mids = new JSONArray();
        mids.put(songMid);
        param.put("songmid", mids);
        JSONArray types = new JSONArray();
        types.put(0);
        param.put("songtype", types);
        param.put("uin", "0");
        param.put("loginflag", 1);
        param.put("platform", "20");

        JSONObject req0 = new JSONObject();
        req0.put("module", "vkey.GetVkeyServer");
        req0.put("method", "CgiGetVkey");
        req0.put("param", param);

        JSONObject comm = new JSONObject();
        comm.put("uin", 0);
        comm.put("format", "json");
        comm.put("ct", 24);
        comm.put("cv", 0);

        JSONObject body = new JSONObject();
        body.put("req_0", req0);
        body.put("comm", comm);

        JSONObject root = new JSONObject(
                fetch(URL_MUSICU, body.toString().getBytes(StandardCharsets.UTF_8)));

        JSONObject r0 = root.optJSONObject("req_0");
        if (r0 == null) return null;
        JSONObject data = r0.optJSONObject("data");
        if (data == null) return null;
        JSONArray info = data.optJSONArray("midurlinfo");
        if (info == null || info.length() == 0) return null;

        JSONObject first = info.optJSONObject(0);
        if (first == null) return null;
        String purl = first.optString("purl");
        if (purl == null || purl.isEmpty()) return null;

        String prefix = "http://aqqmusic.tc.qq.com/";
        JSONArray sip = data.optJSONArray("sip");
        if (sip != null) {
            for (int i = 0; i < sip.length(); i++) {
                String p = sip.optString(i);
                if (p != null && !p.isEmpty()) {
                    prefix = p;
                    break;
                }
            }
        }
        if (!prefix.endsWith("/")) prefix = prefix + "/";
        if (purl.startsWith("/")) purl = purl.substring(1);
        return prefix + purl;
    }

    // ---------------- 歌词 ----------------

    public static String lyric(String songMid) {
        try {
            String url = URL_LYRIC + "?format=json&nobase64=1&songmid=" + songMid;
            JSONObject root = new JSONObject(fetch(url, null));
            String raw = root.optString("lyric");
            return cleanLrc(raw);
        } catch (Exception e) {
            return "";
        }
    }

    private static String cleanLrc(String raw) {
        if (raw == null) return "";
        StringBuilder sb = new StringBuilder();
        String[] lines = raw.split("\n");
        for (String line : lines) {
            // 去掉 [mm:ss.xx] 之类时间标签
            String t = line.replaceAll("\\[[^\\]]*\\]", "").trim();
            if (t.isEmpty()) continue;
            sb.append(t).append('\n');
        }
        return sb.toString().trim();
    }

    // ---------------- 封面 ----------------

    public static String coverUrl(String albumMid) {
        if (albumMid == null || albumMid.isEmpty()) return null;
        return "https://y.gtimg.cn/music/photo_new/T002R300x300M000" + albumMid + ".jpg";
    }

}
