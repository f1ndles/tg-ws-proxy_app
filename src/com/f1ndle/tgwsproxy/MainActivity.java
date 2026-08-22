package com.f1ndle.tgwsproxy;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {

    private static final String MD = "/data/adb/modules/tg_ws_proxy_f1ndle";
    private static final String CONF = MD + "/config.conf";
    private static final String ACTION_WEB = MD + "/action_web.sh";
    private static final String LOG = MD + "/proxy.log";

    private TextView statusText;
    private TextView subText;
    private TextView logText;
    private Button btnToggle;
    private Button btnTelegram;
    private EditText domainInput;
    private LinearLayout switchesBox;
    private boolean busy = false;
    private boolean running = false;
    private String tgLink = "";

    private static final String[][] SWITCHES = {
        {"AUTOSTART", "Автозапуск при загрузке"},
        {"CF_PRIORITY", "CF прокси приоритетнее прямого"},
        {"CF_BALANCE", "Балансировка между CF доменами"},
        {"DEFAULT_DOMAINS", "Домены по умолчанию (GitHub)"},
        {"CD_BYPASS", "Автовыбор лучшего CF домена"},
        {"AUTO_TG", "Автооткрытие Telegram после старта"},
    };

    static String sh(String cmd) {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"su", "-c", cmd});
            StringBuilder out = new StringBuilder();
            BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8));
            BufferedReader e = new BufferedReader(new InputStreamReader(p.getErrorStream(), StandardCharsets.UTF_8));
            String l;
            while ((l = r.readLine()) != null) out.append(l).append('\n');
            while ((l = e.readLine()) != null) out.append(l).append('\n');
            p.waitFor();
            return out.toString();
        } catch (Exception ex) {
            return "error: " + ex;
        }
    }

    static String confGet(String out, String key) {
        for (String l : out.split("\n")) {
            String t = l.trim();
            if (t.startsWith(key + "=")) return t.substring(key.length() + 1).trim();
        }
        return "";
    }

    private void runAsync(final Runnable bg, final Runnable ui) {
        setBusy(true);
        new Thread(() -> {
            bg.run();
            runOnUiThread(() -> {
                setBusy(false);
                if (ui != null) ui.run();
            });
        }).start();
    }

    private void setBusy(boolean b) {
        busy = b;
        btnToggle.setEnabled(!b);
        btnTelegram.setEnabled(!b);
        if (b) btnToggle.setText("...");
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private TextView sectionLabel(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(0xff9e9e9e);
        t.setTextSize(12);
        t.setLetterSpacing(0.1f);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(20);
        lp.bottomMargin = dp(6);
        t.setLayoutParams(lp);
        return t;
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackgroundColor(0xff1e1f22);
        c.setPadding(dp(16), dp(16), dp(16), dp(16));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(8);
        c.setLayoutParams(lp);
        return c;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(0xff121212);
        scroll.setFillViewport(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(24), dp(16), dp(24));

        TextView title = new TextView(this);
        title.setText("TG WS PROXY");
        title.setTextColor(Color.WHITE);
        title.setTextSize(26);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title);

        TextView path = new TextView(this);
        path.setText("Telegram через WebSocket + Cloudflare");
        path.setTextColor(0xff8e8e8e);
        path.setTextSize(13);
        root.addView(path);

        LinearLayout statusCard = card();
        statusText = new TextView(this);
        statusText.setText("…");
        statusText.setTextColor(0xff9e9e9e);
        statusText.setTextSize(20);
        statusText.setTypeface(Typeface.DEFAULT_BOLD);
        statusCard.addView(statusText);
        subText = new TextView(this);
        subText.setText("");
        subText.setTextColor(0xffb0b0b0);
        subText.setTextSize(13);
        statusCard.addView(subText);
        root.addView(statusCard);

        btnToggle = new Button(this);
        btnToggle.setText("Запустить / Остановить");
        btnToggle.setTextColor(Color.WHITE);
        btnToggle.setTextSize(15);
        btnToggle.setAllCaps(false);
        btnToggle.setOnClickListener(v -> doToggle());
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-1, -2);
        blp.topMargin = dp(16);
        btnToggle.setLayoutParams(blp);
        root.addView(btnToggle);

        btnTelegram = new Button(this);
        btnTelegram.setText("Применить в Telegram");
        btnTelegram.setTextColor(Color.WHITE);
        btnTelegram.setAllCaps(false);
        btnTelegram.setOnClickListener(v -> applyTelegram());
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(-1, -2);
        tlp.topMargin = dp(8);
        btnTelegram.setLayoutParams(tlp);
        root.addView(btnTelegram);

        root.addView(sectionLabel("НАСТРОЙКИ"));

        switchesBox = new LinearLayout(this);
        switchesBox.setOrientation(LinearLayout.VERTICAL);
        switchesBox.setBackgroundColor(0xff1e1f22);
        switchesBox.setPadding(dp(16), dp(8), dp(16), dp(8));
        root.addView(switchesBox);

        LinearLayout domBox = new LinearLayout(this);
        domBox.setOrientation(LinearLayout.HORIZONTAL);
        domBox.setBackgroundColor(0xff1e1f22);
        domBox.setPadding(dp(16), dp(8), dp(16), dp(8));
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(-1, -2);
        dlp.topMargin = dp(8);
        domBox.setLayoutParams(dlp);

        domainInput = new EditText(this);
        domainInput.setHint("CF домен");
        domainInput.setTextColor(Color.WHITE);
        domainInput.setHintTextColor(0xff777777);
        domainInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        domainInput.setSingleLine();
        LinearLayout.LayoutParams elp = new LinearLayout.LayoutParams(0, -2, 1f);
        domainInput.setLayoutParams(elp);
        domBox.addView(domainInput);

        Button btnSave = new Button(this);
        btnSave.setText("OK");
        btnSave.setTextColor(Color.WHITE);
        btnSave.setAllCaps(false);
        btnSave.setOnClickListener(v -> saveDomain());
        domBox.addView(btnSave);
        root.addView(domBox);

        root.addView(sectionLabel("ПОСЛЕДНИЙ ПРОКСИ"));
        TextView linkText = new TextView(this);
        linkKeyHolder[0] = linkText;
        linkText.setTextColor(0xff64b5f6);
        linkText.setTextSize(12);
        LinearLayout linkCard = card();
        linkCard.addView(linkText);
        root.addView(linkCard);

        root.addView(sectionLabel("ЛОГ"));
        logText = new TextView(this);
        logText.setTypeface(Typeface.MONOSPACE);
        logText.setTextSize(11);
        logText.setTextColor(0xffa0a0a0);
        logText.setText("");
        LinearLayout logCard = card();
        logCard.addView(logText);
        root.addView(logCard);

        scroll.addView(root);
        setContentView(scroll);

        refresh();
    }

    private final TextView[] linkKeyHolder = new TextView[1];

    private void doToggle() {
        if (busy) return;
        btnToggle.setText("...");
        final String[] res = new String[1];
        runAsync(() -> res[0] = sh("sh " + ACTION_WEB), () -> {
            String first = res[0] == null ? "" : res[0].trim();
            Toast.makeText(this, first.isEmpty() ? "готово" : first.split("\n")[0], Toast.LENGTH_LONG).show();
            refresh();
        });
    }

    private void applyTelegram() {
        if (tgLink == null || tgLink.isEmpty()) {
            Toast.makeText(this, "Прокси ещё не запущен", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(tgLink)));
        } catch (Exception e) {
            Toast.makeText(this, "Telegram не найден", Toast.LENGTH_SHORT).show();
        }
    }

    private void saveDomain() {
        if (busy) return;
        final String val = domainInput.getText().toString().trim();
        if (val.isEmpty()) return;
        runAsync(() -> sh("sed -i 's|^CF_DOMAIN=.*|CF_DOMAIN=" + val + "|' " + CONF), () -> {
            Toast.makeText(this, "CF домен: " + val, Toast.LENGTH_SHORT).show();
        });
    }

    private void setSwitch(final String key, boolean value) {
        if (busy) return;
        final String v = value ? "ON" : "OFF";
        runAsync(() -> sh("sed -i 's|^" + key + "=.*|" + key + "=" + v + "|' " + CONF), null);
    }

    private void refresh() {
        final String[] statusOut = new String[1];
        final String[] confOut = new String[1];
        final String[] logOut = new String[1];
        final String[] linkOut = new String[1];
        runAsync(() -> {
            statusOut[0] = sh("if [ -f " + MD + "/proxy.pid ] && kill -0 $(cat " + MD + "/proxy.pid) 2>/dev/null; then echo RUNNING; else echo STOPPED; fi");
            confOut[0] = sh("cat " + CONF);
            logOut[0] = sh("tail -14 " + LOG + " 2>/dev/null");
            linkOut[0] = sh("grep -o 'tg://proxy?server=[^ ]*' " + LOG + " 2>/dev/null | tail -1");
        }, () -> {
            running = "RUNNING".equals(statusOut[0] == null ? "" : statusOut[0].trim());
            applyStatus(confOut[0]);
            applySwitches(confOut[0]);
            logText.setText(logOut[0] == null ? "" : logOut[0].trim());
            tgLink = linkOut[0] == null ? "" : linkOut[0].trim();
            if (linkKeyHolder[0] != null) {
                linkKeyHolder[0].setText(tgLink.isEmpty() ? "— запусти прокси —" : tgLink);
            }
        });
    }

    private void applyStatus(String conf) {
        String port = confGet(conf == null ? "" : conf, "PORT");
        String dom = confGet(conf == null ? "" : conf, "CF_DOMAIN");
        if (running) {
            statusText.setText("● РАБОТАЕТ");
            statusText.setTextColor(0xff4caf50);
        } else {
            statusText.setText("● ОСТАНОВЛЕН");
            statusText.setTextColor(0xfff44336);
        }
        String sub = "";
        if (!port.isEmpty()) sub += "MTProto: 127.0.0.1:" + port;
        if (!dom.isEmpty()) sub += (sub.isEmpty() ? "" : "\n") + "CF: " + dom;
        subText.setText(sub);
        btnToggle.setText(running ? "Остановить" : "Запустить");
    }

    private boolean switchesBuilt = false;

    private void applySwitches(String conf) {
        if (!switchesBuilt) {
            switchesBuilt = true;
            for (final String[] sw : SWITCHES) {
                Switch s = new Switch(this);
                s.setText(sw[1]);
                s.setTextColor(Color.WHITE);
                s.setTextSize(14);
                s.setPadding(0, dp(10), 0, dp(10));
                s.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton b, boolean checked) {
                        if (b.isPressed()) setSwitch(sw[0], checked);
                    }
                });
                switchesBox.addView(s);
            }
        }
        String c = conf == null ? "" : conf;
        for (int i = 0; i < switchesBox.getChildCount(); i++) {
            Switch s = (Switch) switchesBox.getChildAt(i);
            String key = SWITCHES[i][0];
            s.setOnCheckedChangeListener(null);
            s.setChecked("ON".equals(confGet(c, key)));
            final String[] swDef = SWITCHES[i];
            s.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton b, boolean checked) {
                    if (b.isPressed()) setSwitch(swDef[0], checked);
                }
            });
        }
        domainInput.setText(confGet(c, "CF_DOMAIN"));
    }
}
