package io.github.up2gooseo.telaleve;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.provider.Settings;
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
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Launcher Android TV 9 nativo, sem bibliotecas e sem serviços em segundo plano. */
public final class HomeActivity extends Activity {
    private final ArrayList<AppEntry> apps = new ArrayList<>();
    private GridView appGrid;
    private TextView appCount;
    private TextView date;
    private AppAdapter adapter;
    private SharedPreferences preferences;
    private boolean appsLoaded = false;

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
        appGrid.setOnItemClickListener((parent, view, position, id) -> launch(apps.get(position)));
        appGrid.setOnItemLongClickListener((parent, view, position, id) -> {
            toggleFavorite(apps.get(position));
            return true;
        });
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
        return new HashSet<>(preferences.getStringSet("list", Collections.emptySet()));
    }

    private void toggleFavorite(AppEntry entry) {
        Set<String> current = favorites();
        boolean wasFavorite = !current.add(entry.packageName);
        if (wasFavorite) current.remove(entry.packageName);
        preferences.edit().putStringSet("list", current).apply();
        Toast.makeText(this, entry.title + (wasFavorite ? " removido dos favoritos" : " adicionado aos favoritos"), Toast.LENGTH_SHORT).show();
        sortAndRefresh();
    }

    private void reloadApps() {
        final PackageManager pm = getPackageManager();
        final Map<String, AppEntry> unique = new HashMap<>();
        final Intent filter = new Intent(Intent.ACTION_MAIN);
        filter.addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        List<ResolveInfo> resolved = pm.queryIntentActivities(filter, 0);
        for (ResolveInfo item : resolved) {
            if (item.activityInfo == null) continue;
            String pkg = item.activityInfo.packageName;
            if (pkg.equals(getPackageName()) || unique.containsKey(pkg)) continue;
            CharSequence name = item.loadLabel(pm);
            String label = name == null ? pkg : name.toString();
            unique.put(pkg, new AppEntry(pkg, label, item.loadIcon(pm)));
        }

        // Inclui apps que oferecem somente a categoria LAUNCHER.
        Intent generic = new Intent(Intent.ACTION_MAIN);
        generic.addCategory(Intent.CATEGORY_LAUNCHER);
        for (ResolveInfo item : pm.queryIntentActivities(generic, 0)) {
            if (item.activityInfo == null) continue;
            String pkg = item.activityInfo.packageName;
            if (pkg.equals(getPackageName()) || unique.containsKey(pkg)) continue;
            CharSequence name = item.loadLabel(pm);
            String label = name == null ? pkg : name.toString();
            unique.put(pkg, new AppEntry(pkg, label, item.loadIcon(pm)));
        }
        apps.clear();
        apps.addAll(unique.values());
        appsLoaded = true;
        sortAndRefresh();
    }

    private void sortAndRefresh() {
        Set<String> favs = favorites();
        Collections.sort(apps, (a, b) -> {
            boolean af = favs.contains(a.packageName), bf = favs.contains(b.packageName);
            if (af != bf) return af ? -1 : 1;
            return a.title.compareToIgnoreCase(b.title);
        });
        adapter.notifyDataSetChanged();
        appCount.setText(apps.size() + " apps");
        if (!apps.isEmpty() && !appGrid.hasFocus()) appGrid.requestFocus();
    }

    private void launch(AppEntry entry) {
        PackageManager pm = getPackageManager();
        Intent appIntent = pm.getLeanbackLaunchIntentForPackage(entry.packageName);
        if (appIntent == null) appIntent = pm.getLaunchIntentForPackage(entry.packageName);
        if (appIntent == null) {
            Toast.makeText(this, "Não foi possível abrir " + entry.title, Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            startActivity(appIntent);
        } catch (RuntimeException exception) {
            Toast.makeText(this, "Erro ao abrir " + entry.title, Toast.LENGTH_SHORT).show();
        }
    }

    private int dp(float value) {
        return Math.round(getResources().getDisplayMetrics().density * value);
    }

    private final class AppAdapter extends BaseAdapter {
        @Override public int getCount() { return apps.size(); }
        @Override public Object getItem(int position) { return apps.get(position); }
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
                tile.setPadding(dp(8), dp(9), dp(8), dp(8));
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
                name.setTextSize(13);
                name.setSingleLine(true);
                name.setEllipsize(android.text.TextUtils.TruncateAt.END);
                tile.addView(name);

                favorite = new TextView(HomeActivity.this);
                favorite.setGravity(Gravity.CENTER);
                favorite.setTextColor(getColor(R.color.focus));
                favorite.setTextSize(11);
                tile.addView(favorite);
            }
            AppEntry entry = apps.get(position);
            icon.setImageDrawable(entry.icon);
            name.setText(entry.title);
            favorite.setText(favorites().contains(entry.packageName) ? "★ FAVORITO" : "");
            tile.setContentDescription(entry.title + (favorites().contains(entry.packageName) ? ", favorito" : ""));
            return tile;
        }
    }
}
