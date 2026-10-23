package io.github.up2gooseo.telaleve;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Launcher nativo para Android TV 9: sem WebView nem serviços em segundo plano. */
public final class HomeActivity extends Activity {
    private static final String FAVORITES_KEY = "list"; // Mantém os favoritos da v0.1
    private static final String HIDDEN_KEY = "hidden_packages";
    private static final String ORDER_KEY = "manual_order";

    private final ArrayList<AppEntry> allApps = new ArrayList<>();
    private final ArrayList<AppEntry> visibleApps = new ArrayList<>();
    private SharedPreferences preferences;
    private GridView appGrid;
    private TextView appCount;
    private TextView date;
    private AppAdapter adapter;
    private boolean appsLoaded;

    private final BroadcastReceiver packageReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            reloadApps();
        }
    };

    private static final class AppEntry {
        final String packageName;
        final String title;
        final Drawable icon;

        AppEntry(String packageName, String title, Drawable icon) {
            this.packageName = packageName;
            this.title = title;
            this.icon = icon;
        }
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_home);
        preferences = getSharedPreferences("favorites", MODE_PRIVATE);
        appGrid = findViewById(R.id.app_grid);
        appCount = findViewById(R.id.app_count);
        date = findViewById(R.id.date);

        adapter = new AppAdapter();
        appGrid.setAdapter(adapter);
        appGrid.setOnItemClickListener((parent, view, position, id) -> {
            if (position >= 0 && position < visibleApps.size()) launch(visibleApps.get(position));
        });
        appGrid.setOnItemLongClickListener((parent, view, position, id) -> {
            if (position < 0 || position >= visibleApps.size()) return false;
            showAppOptions(visibleApps.get(position));
            return true;
        });
        findViewById(R.id.organize_button).setOnClickListener(v -> showManageOptions());
        findViewById(R.id.settings_button).setOnClickListener(v -> {
            try {
                startActivity(new Intent(Settings.ACTION_SETTINGS));
            } catch (ActivityNotFoundException exception) {
                Toast.makeText(this, "Configurações indisponíveis nesta TV", Toast.LENGTH_SHORT).show();
            }
        });

        IntentFilter updates = new IntentFilter();
        updates.addAction(Intent.ACTION_PACKAGE_ADDED);
        updates.addAction(Intent.ACTION_PACKAGE_REMOVED);
        updates.addAction(Intent.ACTION_PACKAGE_CHANGED);
        updates.addDataScheme("package");
        registerReceiver(packageReceiver, updates);
    }

    @Override protected void onResume() {
        super.onResume();
        date.setText(new SimpleDateFormat("EEE, dd MMM", new Locale("pt", "BR"))
                .format(new java.util.Date()));
        if (!appsLoaded) reloadApps();
    }

    @Override protected void onDestroy() {
        unregisterReceiver(packageReceiver);
        super.onDestroy();
    }

    private Set<String> favorites() {
        return new HashSet<>(preferences.getStringSet(FAVORITES_KEY, Collections.emptySet()));
    }

    private Set<String> hidden() {
        return new HashSet<>(preferences.getStringSet(HIDDEN_KEY, Collections.emptySet()));
    }

    private void toggleFavorite(AppEntry entry) {
        Set<String> current = favorites();
        boolean enabled = current.add(entry.packageName);
        if (!enabled) current.remove(entry.packageName);
        preferences.edit().putStringSet(FAVORITES_KEY, current).apply();
        Toast.makeText(this, entry.title + (enabled ? " adicionado aos favoritos"
                : " removido dos favoritos"), Toast.LENGTH_SHORT).show();
        refreshApps();
    }

    private void setHidden(String pkg, boolean hide) {
        Set<String> values = hidden();
        if (hide) values.add(pkg);
        else values.remove(pkg);
        preferences.edit().putStringSet(HIDDEN_KEY, values).apply();
        refreshApps();
    }

    private void showAppOptions(AppEntry entry) {
        String[] options = {
            "Mover uma posição à esquerda",
            "Mover uma posição à direita",
            favorites().contains(entry.packageName) ? "Remover dos favoritos" : "Adicionar aos favoritos",
            "Ocultar da tela inicial"
        };
        new AlertDialog.Builder(this).setTitle(entry.title)
                .setItems(options, (dialog, which) -> {
                    if (which == 0) move(entry, -1);
                    else if (which == 1) move(entry, 1);
                    else if (which == 2) toggleFavorite(entry);
                    else if (which == 3) setHidden(entry.packageName, true);
                })
                .setNegativeButton("Cancelar", null).show();
    }

    private void move(AppEntry entry, int delta) {
        int oldIndex = visibleApps.indexOf(entry);
        int newIndex = oldIndex + delta;
        if (oldIndex < 0 || newIndex < 0 || newIndex >= visibleApps.size()) {
            Toast.makeText(this, "O aplicativo já está no limite da lista",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        Collections.swap(visibleApps, oldIndex, newIndex);
        // Mantém os apps ocultos na ordem salva para reaparecerem quando solicitados.
        ArrayList<String> packages = new ArrayList<>();
        for (AppEntry item : visibleApps) packages.add(item.packageName);
        for (AppEntry item : allApps) {
            if (!packages.contains(item.packageName)) packages.add(item.packageName);
        }
        preferences.edit().putString(ORDER_KEY, TextUtils.join("|", packages)).apply();
        refreshApps();
        appGrid.setSelection(newIndex);
    }

    private void showManageOptions() {
        String[] options = {
            "Mostrar aplicativo oculto",
            "Restaurar ordem sugerida",
            "Reexibir todos os aplicativos"
        };
        new AlertDialog.Builder(this).setTitle("Organizar aplicativos")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) showHiddenApps();
                    else if (which == 1) {
                        preferences.edit().remove(ORDER_KEY).apply();
                        refreshApps();
                        Toast.makeText(this, "Ordem padrão restaurada", Toast.LENGTH_SHORT).show();
                    } else if (which == 2) {
                        preferences.edit().remove(HIDDEN_KEY).apply();
                        refreshApps();
                        Toast.makeText(this, "Todos os aplicativos estão visíveis",
                                Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancelar", null).show();
    }

    private void showHiddenApps() {
        Set<String> hidden = hidden();
        ArrayList<AppEntry> invisible = new ArrayList<>();
        for (AppEntry item : allApps) {
            if (hidden.contains(item.packageName)) invisible.add(item);
        }
        if (invisible.isEmpty()) {
            Toast.makeText(this, "Nenhum aplicativo oculto", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] titles = new String[invisible.size()];
        for (int i = 0; i < invisible.size(); i++) titles[i] = invisible.get(i).title;
        new AlertDialog.Builder(this).setTitle("Reexibir aplicativo")
                .setItems(titles, (dialog, which) -> {
                    setHidden(invisible.get(which).packageName, false);
                    Toast.makeText(this, "Aplicativo reexibido", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancelar", null).show();
    }

    private void reloadApps() {
        PackageManager pm = getPackageManager();
        Map<String, AppEntry> unique = new HashMap<>();
        Intent tv = new Intent(Intent.ACTION_MAIN);
        tv.addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        collectApps(pm, tv, unique);
        // Alguns apps de TV expõem somente a categoria LAUNCHER.
        Intent generic = new Intent(Intent.ACTION_MAIN);
        generic.addCategory(Intent.CATEGORY_LAUNCHER);
        collectApps(pm, generic, unique);
        allApps.clear();
        allApps.addAll(unique.values());
        appsLoaded = true;
        refreshApps();
    }

    private void collectApps(PackageManager pm, Intent query, Map<String, AppEntry> unique) {
        List<ResolveInfo> resolved = pm.queryIntentActivities(query, 0);
        for (ResolveInfo item : resolved) {
            if (item.activityInfo == null) continue;
            String pkg = item.activityInfo.packageName;
            if (pkg.equals(getPackageName()) || unique.containsKey(pkg)) continue;
            CharSequence label = item.loadLabel(pm);
            unique.put(pkg, new AppEntry(pkg,
                    label == null ? pkg : label.toString(), item.loadIcon(pm)));
        }
    }

    private void refreshApps() {
        Set<String> favs = favorites();
        Set<String> invisible = hidden();
        Map<String, Integer> manual = new HashMap<>();
        String saved = preferences.getString(ORDER_KEY, "");
        if (saved != null && !saved.isEmpty()) {
            String[] packages = saved.split("\\|");
            for (int i = 0; i < packages.length; i++) manual.put(packages[i], i);
        }

        Collections.sort(allApps, (a, b) -> {
            if (!manual.isEmpty()) {
                int ia = manual.containsKey(a.packageName) ? manual.get(a.packageName) : Integer.MAX_VALUE;
                int ib = manual.containsKey(b.packageName) ? manual.get(b.packageName) : Integer.MAX_VALUE;
                if (ia != ib) return Integer.compare(ia, ib);
            }
            int pa = AppOrdering.priority(a.packageName, a.title);
            int pb = AppOrdering.priority(b.packageName, b.title);
            if (pa != pb) {
                if (pa == -1) return 1;
                if (pb == -1) return -1;
                return Integer.compare(pa, pb);
            }
            boolean fa = favs.contains(a.packageName);
            boolean fb = favs.contains(b.packageName);
            if (fa != fb) return fa ? -1 : 1;
            int c = a.title.compareToIgnoreCase(b.title);
            return c != 0 ? c : a.packageName.compareTo(b.packageName);
        });

        int selected = appGrid.getSelectedItemPosition();
        visibleApps.clear();
        for (AppEntry entry : allApps) {
            if (!invisible.contains(entry.packageName)) visibleApps.add(entry);
        }
        adapter.notifyDataSetChanged();
        appCount.setText(visibleApps.size() + " apps");
        if (visibleApps.isEmpty()) findViewById(R.id.organize_button).requestFocus();
        else {
            appGrid.setSelection(Math.min(Math.max(selected, 0), visibleApps.size() - 1));
            if (!appGrid.hasFocus()) appGrid.requestFocus();
        }
    }

    private void launch(AppEntry entry) {
        PackageManager pm = getPackageManager();
        Intent intent = pm.getLeanbackLaunchIntentForPackage(entry.packageName);
        if (intent == null) intent = pm.getLaunchIntentForPackage(entry.packageName);
        if (intent == null) {
            Toast.makeText(this, "Não foi possível abrir " + entry.title,
                    Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            startActivity(intent);
        } catch (RuntimeException exception) {
            Toast.makeText(this, "Erro ao abrir " + entry.title, Toast.LENGTH_SHORT).show();
        }
    }

    private int dp(float value) {
        return Math.round(getResources().getDisplayMetrics().density * value);
    }

    private final class AppAdapter extends BaseAdapter {
        @Override public int getCount() { return visibleApps.size(); }
        @Override public Object getItem(int position) { return visibleApps.get(position); }
        @Override public long getItemId(int position) { return position; }

        @Override public View getView(int position, View recycled, ViewGroup parent) {
            LinearLayout tile;
            ImageView icon;
            TextView name;
            TextView favorite;
            if (recycled instanceof LinearLayout) {
                tile = (LinearLayout) recycled;
                icon = (ImageView) tile.getChildAt(0);
                name = (TextView) tile.getChildAt(1);
                favorite = (TextView) tile.getChildAt(2);
            } else {
                tile = new LinearLayout(HomeActivity.this);
                tile.setOrientation(LinearLayout.VERTICAL);
                tile.setGravity(Gravity.CENTER);
                tile.setBackgroundResource(R.drawable.tile);
                tile.setPadding(dp(6), dp(9), dp(6), dp(8));
                tile.setLayoutParams(new android.widget.AbsListView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dp(116)));
                icon = new ImageView(HomeActivity.this);
                icon.setLayoutParams(new LinearLayout.LayoutParams(dp(47), dp(47)));
                icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
                tile.addView(icon);

                name = new TextView(HomeActivity.this);
                LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dp(27));
                nl.topMargin = dp(5);
                name.setLayoutParams(nl);
                name.setGravity(Gravity.CENTER);
                name.setTextColor(getColor(R.color.ink));
                name.setTextSize(12);
                name.setSingleLine(true);
                name.setEllipsize(TextUtils.TruncateAt.END);
                tile.addView(name);

                favorite = new TextView(HomeActivity.this);
                favorite.setGravity(Gravity.CENTER);
                favorite.setTextColor(getColor(R.color.focus));
                favorite.setTextSize(10);
                tile.addView(favorite);
            }
            AppEntry entry = visibleApps.get(position);
            icon.setImageDrawable(entry.icon);
            name.setText(entry.title);
            boolean isFavorite = favorites().contains(entry.packageName);
            favorite.setText(isFavorite ? "★ FAVORITO" : "");
            tile.setContentDescription(entry.title + (isFavorite ? ", favorito" : ""));
            return tile;
        }
    }
}
