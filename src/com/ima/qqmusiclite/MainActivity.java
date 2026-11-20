package com.ima.qqmusiclite;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.SimpleAdapter;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {

    private static final int ACCENT = 0xFF31C27C;
    private static final int BG = 0xFFF5F6F8;
    private static final int TEXT = 0xFF191919;
    private static final int SUB = 0xFF9AA0A6;
    private static final int LINE = 0xFFEDEFF2;

    private final ExecutorService pool = Executors.newFixedThreadPool(2);
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final List<Song> songs = new ArrayList<>();

    private SimpleAdapter adapter;
    private List<Map<String, Object>> rows = new ArrayList<>();
    private ListView listView;
    private EditText input;
    private ProgressBar loading;
    private ImageView cover;
    private TextView nowTitle, nowArtist, timeLabel;
    private SeekBar seek;
    private Button playBtn;
    private Button downloadBtn;

    private MediaPlayer player;
    private Song current;
    private int reqSeq = 0;
    private boolean dragging = false;

    // region ---------- 生命周期 ----------

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            getWindow().setStatusBarColor(ACCENT);
        } catch (Exception ignored) {
        }
        buildUi();
        loadTopList(26);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        releasePlayer();
        pool.shutdownNow();
    }

    // endregion

    // region ---------- UI ----------

    private int dp(float v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private GradientDrawable round(int fill, int radiusDp, int stroke, int strokeDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(dp(radiusDp));
        if (strokeDp > 0) d.setStroke(dp(strokeDp), stroke);
        return d;
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setFitsSystemWindows(true);

        // 顶部标题
        TextView header = new TextView(this);
        header.setText("QQ音乐轻听");
        header.setTextSize(19);
        header.setTypeface(Typeface.DEFAULT_BOLD);
        header.setTextColor(0xFFFFFFFF);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(16), dp(13), dp(16), dp(13));
        header.setBackgroundColor(ACCENT);
        root.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // 搜索行
        LinearLayout searchRow = new LinearLayout(this);
        searchRow.setOrientation(LinearLayout.HORIZONTAL);
        searchRow.setGravity(Gravity.CENTER_VERTICAL);
        searchRow.setPadding(dp(12), dp(10), dp(12), dp(4));

        input = new EditText(this);
        input.setHint("搜索歌曲、歌手");
        input.setTextSize(15);
        input.setSingleLine(true);
        input.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setPadding(dp(14), dp(8), dp(14), dp(8));
        input.setBackground(round(0xFFFFFFFF, 20, 0xFFE3E6EA, 1));
        input.setHintTextColor(0xFFB0B4B9);
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        searchRow.addView(input, ip);

        Button btnSearch = new Button(this);
        btnSearch.setText("搜索");
        btnSearch.setTextSize(14);
        btnSearch.setTextColor(0xFFFFFFFF);
        btnSearch.setAllCaps(false);
        btnSearch.setMinHeight(0);
        btnSearch.setMinimumHeight(0);
        btnSearch.setMinWidth(0);
        btnSearch.setMinimumWidth(0);
        btnSearch.setPadding(dp(20), 0, dp(20), 0);
        btnSearch.setBackground(round(ACCENT, 20, 0, 0));
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(40));
        bp.leftMargin = dp(8);
        searchRow.addView(btnSearch, bp);
        root.addView(searchRow);

        // 榜单标签行
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabs.setPadding(dp(12), dp(2), dp(12), dp(6));
        tabs.addView(chip("热歌榜", 26), chipLp());
        tabs.addView(chip("新歌榜", 27), chipLp());
        tabs.addView(chip("飙升榜", 62), chipLp());
        tabs.addView(chip("欧美榜", 3), chipLp());
        hs.addView(tabs);
        root.addView(hs, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // 加载条
        loading = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        loading.setIndeterminate(true);
        loading.setVisibility(View.GONE);
        root.addView(loading, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(3)));

        // 列表
        listView = new ListView(this);
        listView.setDivider(new ColorDrawable(LINE));
        listView.setDividerHeight(1);
        listView.setCacheColorHint(0);
        listView.setSelector(new ColorDrawable(0x1431C27C));
        root.addView(listView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        // 播放条
        root.addView(buildPlayerBar(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        setContentView(root);

        // 事件
        btnSearch.setOnClickListener(v -> doSearch());
        input.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                doSearch();
                return true;
            }
            return false;
        });

        adapter = new SimpleAdapter(this, rows, android.R.layout.simple_list_item_2,
                new String[]{"t1", "t2"},
                new int[]{android.R.id.text1, android.R.id.text2});
        listView.setAdapter(adapter);
        listView.setOnItemClickListener((parent, view, position, id) -> {
            if (position >= 0 && position < songs.size()) playSong(songs.get(position));
        });
        listView.setOnItemLongClickListener((parent, view, position, id) -> {
            if (position >= 0 && position < songs.size()) showLyric(songs.get(position));
            return true;
        });
    }

    private LinearLayout.LayoutParams chipLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = dp(8);
        return lp;
    }

    private TextView chip(String label, final int topId) {
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextSize(13);
        tv.setTextColor(TEXT);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(dp(16), dp(7), dp(16), dp(7));
        tv.setBackground(round(0xFFFFFFFF, 16, 0xFFE7EAEE, 1));
        tv.setOnClickListener(v -> loadTopList(topId));
        return tv;
    }

    private View buildPlayerBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.VERTICAL);
        bar.setBackgroundColor(0xFFFFFFFF);
        bar.setPadding(dp(12), dp(8), dp(12), dp(4));
        bar.setElevation(dp(10));

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        cover = new ImageView(this);
        cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
        cover.setBackground(round(0xFFEEEEEE, 8, 0, 0));
        top.addView(cover, new LinearLayout.LayoutParams(dp(50), dp(50)));

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setPadding(dp(10), 0, dp(6), 0);

        nowTitle = new TextView(this);
        nowTitle.setText("未在播放");
        nowTitle.setTextSize(14);
        nowTitle.setTextColor(TEXT);
        nowTitle.setSingleLine(true);
        nowTitle.setEllipsize(TextUtils.TruncateAt.END);

        nowArtist = new TextView(this);
        nowArtist.setText("点击列表中的歌曲开始播放");
        nowArtist.setTextSize(11.5f);
        nowArtist.setTextColor(SUB);
        nowArtist.setSingleLine(true);
        nowArtist.setEllipsize(TextUtils.TruncateAt.END);

        info.addView(nowTitle);
        info.addView(nowArtist);
        top.addView(info, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        playBtn = new Button(this);
        playBtn.setText("▶");
        playBtn.setTextSize(16);
        playBtn.setTextColor(0xFFFFFFFF);
        playBtn.setAllCaps(false);
        playBtn.setMinHeight(0);
        playBtn.setMinimumHeight(0);
        playBtn.setMinWidth(0);
        playBtn.setMinimumWidth(0);
        playBtn.setPadding(0, 0, 0, 0);
        playBtn.setBackground(round(ACCENT, 24, 0, 0));
        playBtn.setOnClickListener(v -> togglePlay());
        top.addView(playBtn, new LinearLayout.LayoutParams(dp(46), dp(46)));

        downloadBtn = new Button(this);
        downloadBtn.setText("↓");
        downloadBtn.setTextSize(20);
        downloadBtn.setTextColor(0xFFFFFFFF);
        downloadBtn.setAllCaps(false);
        downloadBtn.setMinHeight(0);
        downloadBtn.setMinimumHeight(0);
        downloadBtn.setMinWidth(0);
        downloadBtn.setMinimumWidth(0);
        downloadBtn.setPadding(0, 0, 0, 0);
        downloadBtn.setBackground(round(0xFF4A90E2, 23, 0, 0));
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(dp(42), dp(42));
        dlp.leftMargin = dp(6);
        downloadBtn.setOnClickListener(v -> downloadCurrent());
        top.addView(downloadBtn, dlp);

        bar.addView(top, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // 进度条
        LinearLayout progRow = new LinearLayout(this);
        progRow.setOrientation(LinearLayout.HORIZONTAL);
        progRow.setGravity(Gravity.CENTER_VERTICAL);
        progRow.setPadding(0, 0, 0, 0);

        seek = new SeekBar(this);
        seek.setMax(1000);
        seek.setPadding(0, 0, 0, 0);
        progRow.addView(seek, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        timeLabel = new TextView(this);
        timeLabel.setText("00:00/00:00");
        timeLabel.setTextSize(10);
        timeLabel.setTextColor(SUB);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.leftMargin = dp(8);
        progRow.addView(timeLabel, tlp);

        bar.addView(progRow);

        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                if (fromUser && player != null) {
                    try {
                        int dur = player.getDuration();
                        if (dur > 0) {
                            player.seekTo((int) (progress * 1L * dur / sb.getMax()));
                            timeLabel.setText(fmtMs((int) (progress * 1L * dur / sb.getMax()))
                                    + "/" + fmtMs(dur));
                        }
                    } catch (Exception ignored) {
                    }
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar sb) {
                dragging = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar sb) {
                dragging = false;
            }
        });
        return bar;
    }

    // endregion

    // region ---------- 数据加载 ----------

    private void showLoading(final boolean show) {
        loading.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private void loadTopList(final int topId) {
        final int seq = ++reqSeq;
        showLoading(true);
        pool.execute(() -> {
            try {
                final List<Song> list = Api.topList(topId, 30);
                ui.post(() -> {
                    if (seq != reqSeq) return;
                    showLoading(false);
                    if (list.isEmpty()) toast("榜单为空或接口异常");
                    fill(list);
                });
            } catch (Exception e) {
                ui.post(() -> {
                    if (seq != reqSeq) return;
                    showLoading(false);
                    toast("加载榜单失败：" + msg(e));
                });
            }
        });
    }

    private void doSearch() {
        final String kw = input.getText().toString().trim();
        if (kw.isEmpty()) {
            toast("请输入关键词");
            return;
        }
        final int seq = ++reqSeq;
        showLoading(true);
        ui.post(() -> {
            songs.clear();
            rows.clear();
            if (adapter != null) adapter.notifyDataSetChanged();
        });
        pool.execute(() -> {
            try {
                final List<Song> list = Api.search(kw, 30);
                ui.post(() -> {
                    if (seq != reqSeq) return;
                    showLoading(false);
                    if (list.isEmpty()) toast("没有找到相关歌曲");
                    fill(list);
                });
            } catch (Exception e) {
                ui.post(() -> {
                    if (seq != reqSeq) return;
                    showLoading(false);
                    toast("搜索失败：" + msg(e));
                });
            }
        });
    }

    private void fill(List<Song> list) {
        songs.clear();
        songs.addAll(list);
        rows.clear();
        for (int i = 0; i < songs.size(); i++) {
            Song s = songs.get(i);
            Map<String, Object> m = new HashMap<>();
            m.put("t1", (i + 1) + ".  " + s.name);
            String sub = s.singer;
            if (s.interval > 0) sub = sub + "  ·  " + fmt(s.interval);
            m.put("t2", sub);
            rows.add(m);
        }
        adapter.notifyDataSetChanged();
        listView.setSelection(0);
    }

    // endregion

    // region ---------- 播放 ----------

    private void playSong(final Song s) {
        current = s;
        nowTitle.setText(s.name);
        nowArtist.setText(s.singer);
        loadCover(s.albumMid);
        timeLabel.setText("00:00/00:00");
        seek.setProgress(0);

        releasePlayer();
        playBtn.setText("…");
        pool.execute(() -> {
            String url = null;
            String err = null;
            try {
                url = Api.playUrl(s.mid);
            } catch (Exception e) {
                err = msg(e);
            }
            final String fUrl = url;
            final String fErr = err;
            ui.post(() -> {
                if (fErr != null) {
                    playBtn.setText("▶");
                    toast("获取播放地址失败：" + fErr);
                    return;
                }
                if (fUrl == null) {
                    playBtn.setText("▶");
                    nowArtist.setText(s.singer + "  (版权/VIP 限制，无法播放)");
                    toast("《" + s.name + "》受版权或 VIP 限制，无法播放");
                    return;
                }
                startPlayer(fUrl);
            });
        });
    }

    private void startPlayer(String url) {
        try {
            releasePlayer();
            MediaPlayer mp = new MediaPlayer();
            mp.setAudioStreamType(AudioManager.STREAM_MUSIC);
            mp.setOnPreparedListener(m -> {
                player = m;
                m.start();
                playBtn.setText("⏸");
                seek.setMax(Math.max(1, m.getDuration()));
                startProgress();
            });
            mp.setOnCompletionListener(m -> {
                playBtn.setText("▶");
                seek.setProgress(seek.getMax());
                ui.removeCallbacks(progressTick);
            });
            mp.setOnErrorListener((m, what, extra) -> {
                playBtn.setText("▶");
                toast("播放失败，可能是网络或版权限制");
                return true;
            });
            mp.setDataSource(url);
            mp.prepareAsync();
            player = mp;
        } catch (Exception e) {
            playBtn.setText("▶");
            toast("播放器初始化失败：" + msg(e));
        }
    }

    private void togglePlay() {
        if (player == null) {
            if (current != null) playSong(current);
            else toast("请先在列表中选择一首歌");
            return;
        }
        try {
            if (player.isPlaying()) {
                player.pause();
                playBtn.setText("▶");
                ui.removeCallbacks(progressTick);
            } else {
                player.start();
                playBtn.setText("⏸");
                startProgress();
            }
        } catch (Exception e) {
            toast("操作失败：" + msg(e));
        }
    }

    private void startProgress() {
        ui.removeCallbacks(progressTick);
        ui.post(progressTick);
    }

    private final Runnable progressTick = new Runnable() {
        @Override
        public void run() {
            if (player != null) {
                try {
                    int pos = player.getCurrentPosition();
                    int dur = player.getDuration();
                    if (!dragging && dur > 0) {
                        seek.setProgress((int) (pos * 1L * seek.getMax() / dur));
                    }
                    timeLabel.setText(fmtMs(pos) + "/" + fmtMs(dur));
                } catch (Exception ignored) {
                }
            }
            ui.postDelayed(this, 400);
        }
    };

    private void releasePlayer() {
        ui.removeCallbacks(progressTick);
        if (player != null) {
            try {
                player.reset();
                player.release();
            } catch (Exception ignored) {
            }
            player = null;
        }
    }

    // endregion

    // region ---------- 下载（仅可播放的免费歌曲） ----------

    /** 只下载当前这首、且官网返回了播放地址的歌曲；VIP/版权歌曲会被拦截 */
    private void downloadCurrent() {
        final Song s = current;
        if (s == null) {
            toast("请先选择一首歌");
            return;
        }
        toast("正在准备下载…");
        pool.execute(() -> {
            String url = null;
            String err = null;
            try {
                url = Api.playUrl(s.mid);
            } catch (Exception e) {
                err = msg(e);
            }
            final String fUrl = url;
            final String fErr = err;
            ui.post(() -> {
                if (fErr != null) {
                    toast("获取下载地址失败：" + fErr);
                } else if (fUrl == null) {
                    toast("《" + s.name + "》受版权/VIP 限制，无法下载");
                } else {
                    saveSong(s, fUrl);
                }
            });
        });
    }

    private void saveSong(final Song s, final String url) {
        pool.execute(() -> {
            String note;
            try {
                byte[] data = readAllBytes(url);
                String name = safeFileName(s.name + " - " + s.singer + ".m4a");
                boolean ok;
                if (Build.VERSION.SDK_INT >= 29) {
                    ContentValues cv = new ContentValues();
                    cv.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
                    cv.put(MediaStore.MediaColumns.MIME_TYPE, "audio/mp4");
                    cv.put(MediaStore.MediaColumns.RELATIVE_PATH,
                            Environment.DIRECTORY_MUSIC + "/QQ音乐轻听");
                    Uri uri = getContentResolver().insert(
                            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, cv);
                    if (uri == null) throw new IOException("无法创建媒体文件");
                    OutputStream os = getContentResolver().openOutputStream(uri);
                    if (os == null) throw new IOException("无法写入媒体文件");
                    os.write(data);
                    os.flush();
                    os.close();
                    ok = true;
                } else {
                    if (checkSelfPermission("android.permission.WRITE_EXTERNAL_STORAGE")
                            != PackageManager.PERMISSION_GRANTED) {
                        requestPermissions(
                                new String[]{"android.permission.WRITE_EXTERNAL_STORAGE"}, 2001);
                        return;
                    }
                    File dir = new File(Environment.getExternalStoragePublicDirectory(
                            Environment.DIRECTORY_MUSIC), "QQ音乐轻听");
                    if (!dir.exists() && !dir.mkdirs()) throw new IOException("无法创建目录");
                    FileOutputStream fos = new FileOutputStream(new File(dir, name));
                    fos.write(data);
                    fos.flush();
                    fos.close();
                    ok = true;
                }
                note = ok ? ("已保存到 音乐/QQ音乐轻听/" + name) : "保存失败";
            } catch (Exception e) {
                note = "下载失败：" + msg(e);
            }
            final String n = note;
            ui.post(() -> toast(n));
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 2001) {
            boolean granted = grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            toast(granted ? "已授权，请再点一次下载" : "需要存储权限才能保存歌曲");
        }
    }

    private byte[] readAllBytes(String url) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(30000);
        c.setInstanceFollowRedirects(true);
        c.setRequestProperty("User-Agent", "Mozilla/5.0");
        c.setRequestProperty("Referer", "https://y.qq.com/");
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        try (InputStream is = c.getInputStream()) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) > 0) bos.write(buf, 0, n);
        }
        return bos.toByteArray();
    }

    private String safeFileName(String name) {
        String s = name.replaceAll("[\\\\/:*?\"<>|\\r\\n]", "_");
        s = s.replaceAll("\\s+", " ").trim();
        if (s.length() > 120) s = s.substring(0, 120);
        return s.isEmpty() ? "song.m4a" : s;
    }

    // endregion

    // region ---------- 歌词 / 封面 ----------

    private void showLyric(final Song s) {
        toast("正在加载歌词…");
        pool.execute(() -> {
            final String ly = Api.lyric(s.mid);
            ui.post(() -> {
                if (ly == null || ly.isEmpty()) {
                    toast("暂无歌词");
                    return;
                }
                TextView tv = new TextView(MainActivity.this);
                tv.setText(ly);
                tv.setTextSize(14);
                tv.setTextColor(TEXT);
                tv.setLineSpacing(dp(7), 1f);
                tv.setPadding(dp(20), dp(16), dp(20), dp(16));
                ScrollView sv = new ScrollView(MainActivity.this);
                sv.addView(tv);
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle(s.name + " - " + s.singer)
                        .setView(sv)
                        .setPositiveButton("关闭", null)
                        .show();
            });
        });
    }

    private void loadCover(final String albumMid) {
        cover.setImageDrawable(null);
        cover.setBackground(round(0xFFEEEEEE, 8, 0, 0));
        final String url = Api.coverUrl(albumMid);
        if (url == null) return;
        pool.execute(() -> {
            Bitmap bm = null;
            try {
                HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
                c.setConnectTimeout(8000);
                c.setReadTimeout(8000);
                c.setRequestProperty("User-Agent", "Mozilla/5.0");
                c.setRequestProperty("Referer", "https://y.qq.com/");
                InputStream is = c.getInputStream();
                bm = BitmapFactory.decodeStream(is);
                is.close();
            } catch (Exception ignored) {
            }
            final Bitmap fb = bm;
            if (fb == null) return;
            ui.post(() -> {
                if (current != null && albumMid != null && albumMid.equals(current.albumMid)) {
                    cover.setImageBitmap(fb);
                }
            });
        });
    }

    // endregion

    // region ---------- 工具 ----------

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    private String msg(Exception e) {
        String m = e.getMessage();
        return (m == null || m.isEmpty()) ? e.getClass().getSimpleName() : m;
    }

    private String fmt(int seconds) {
        return String.format(Locale.CHINA, "%d:%02d", seconds / 60, seconds % 60);
    }

    private String fmtMs(int ms) {
        if (ms < 0) ms = 0;
        int total = ms / 1000;
        return String.format(Locale.CHINA, "%02d:%02d", total / 60, total % 60);
    }

    // endregion
}
