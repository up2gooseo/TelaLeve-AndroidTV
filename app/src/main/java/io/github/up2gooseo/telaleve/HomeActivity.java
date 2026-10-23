package io.github.up2gooseo.telaleve;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.BroadcastReceiver;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ScrollView;
import android.util.DisplayMetrics;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Tela inicial otimizada para Android TV; o catálogo é carregado somente
 * depois dos aplicativos, na rede, sem bloquear a navegação do controle.
 */
public final class HomeActivity extends Activity {
    private static final String FAVORITES_KEY = "list";
    private static final String HIDDEN_KEY = "hidden_packages";
    private static final String ORDER_KEY = "manual_order";
    private static final String RECENT_KEY = "recent_apps";
    private static final String TMDB_KEY = "tmdb_access_token";

    private final ArrayList<AppEntry> allApps = new ArrayList<>();
    private final ArrayList<AppEntry> visibleApps = new ArrayList<>();

    private SharedPreferences preferences;
    private CatalogClient catalog;
    private LinearLayout appRow, recentRow, providerRow, mediaRow;
    private TextView date, status, heading, movieTab, tvTab;
    private EditText searchInput;
    private boolean appsLoaded;
    private String chosenProvider = CatalogClient.ALL;
    private String chosenType = "movie";
    private int requestId = 0;
    private boolean catalogModeIsSearch = false;

    private final BroadcastReceiver packageReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            reloadApps();
        }
    };

    private static final class AppEntry {
        final String packageName;
        final String title;
        final Drawable icon;

        AppEntry(String pkg, String title, Drawable icon) {
            this.packageName = pkg;
            this.title = title;
            this.icon = icon;
        }
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_home);
        preferences = getSharedPreferences("favorites", MODE_PRIVATE);
        importTokenFromAdb();
        catalog = new CatalogClient();
        appRow = findViewById(R.id.app_row);
        recentRow = findViewById(R.id.recent_row);
        providerRow = findViewById(R.id.provider_row);
        mediaRow = findViewById(R.id.media_row);
        date = findViewById(R.id.date);
        status = findViewById(R.id.catalog_status);
        heading = findViewById(R.id.catalog_heading);
        movieTab = findViewById(R.id.type_movie);
        tvTab = findViewById(R.id.type_tv);
        searchInput = findViewById(R.id.search_input);
        findViewById(R.id.organize_button).setOnClickListener(v -> showManageOptions());
        findViewById(R.id.tmdb_button).setOnClickListener(v -> showTmdbSettings());
        findViewById(R.id.settings_button).setOnClickListener(v -> {
            try {
                startActivity(new Intent(Settings.ACTION_SETTINGS));
            } catch (ActivityNotFoundException error) {
                toast("Configurações indisponíveis nesta TV.");
            }
        });
        findViewById(R.id.credits).setOnClickListener(v -> showCredits());
        findViewById(R.id.search_button).setOnClickListener(v -> runSearch());
        searchInput.setOnEditorActionListener((view, action, event) -> {
            if (action == EditorInfo.IME_ACTION_SEARCH
                    || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                runSearch();
                return true;
            }
            return false;
        });
        movieTab.setOnClickListener(v -> {
            chosenType = "movie";
            if (!catalogModeIsSearch) loadRecent();
        });
        tvTab.setOnClickListener(v -> {
            chosenType = "tv";
            if (!catalogModeIsSearch) loadRecent();
        });
        findViewById(R.id.reset_search).setOnClickListener(v -> {
            searchInput.setText("");
            catalogModeIsSearch = false;
            findViewById(R.id.reset_search).setVisibility(View.GONE);
            loadRecent();
        });

        IntentFilter packages = new IntentFilter();
        packages.addAction(Intent.ACTION_PACKAGE_ADDED);
        packages.addAction(Intent.ACTION_PACKAGE_REMOVED);
        packages.addAction(Intent.ACTION_PACKAGE_CHANGED);
        packages.addDataScheme("package");
        registerReceiver(packageReceiver, packages);

        date.setText(new SimpleDateFormat("EEE, dd MMM", new Locale("pt", "BR"))
                .format(new java.util.Date()));
        reloadApps();
        setupProviders();
        loadRecent();
    }

    @Override protected void onResume() {
        super.onResume();
        if (!appsLoaded) reloadApps();
        date.setText(new SimpleDateFormat("EEE, dd MMM", new Locale("pt", "BR"))
                .format(new java.util.Date()));
    }

    @Override protected void onDestroy() {
        requestId++;
        unregisterReceiver(packageReceiver);
        catalog.close();
        super.onDestroy();
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private int dp(int size) {
        return (int) (getResources().getDisplayMetrics().density * size + 0.5f);
    }

    private Set<String> favorites() {
        return new HashSet<>(preferences.getStringSet(FAVORITES_KEY, Collections.emptySet()));
    }

    private Set<String> hidden() {
        return new HashSet<>(preferences.getStringSet(HIDDEN_KEY, Collections.emptySet()));
    }

    private void reloadApps() {
        PackageManager pm = getPackageManager();
        Map<String, AppEntry> unique = new HashMap<>();
        Intent tv = new Intent(Intent.ACTION_MAIN);
        tv.addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        collectApps(pm, tv, unique);
        Intent generic = new Intent(Intent.ACTION_MAIN);
        generic.addCategory(Intent.CATEGORY_LAUNCHER);
        collectApps(pm, generic, unique);
        allApps.clear();
        allApps.addAll(unique.values());
        appsLoaded = true;
        refreshApps();
    }

    private void collectApps(PackageManager pm, Intent query, Map<String, AppEntry> unique) {
        List<ResolveInfo> results = pm.queryIntentActivities(query, 0);
        for (ResolveInfo info : results) {
            if (info.activityInfo == null) continue;
            String pkg = info.activityInfo.packageName;
            if (pkg.equals(getPackageName()) || unique.containsKey(pkg)) continue;
            CharSequence title = info.loadLabel(pm);
            unique.put(pkg, new AppEntry(pkg,
                    title == null ? pkg : title.toString(), info.loadIcon(pm)));
        }
    }

    private void refreshApps() {
        Set<String> favorites = favorites();
        Set<String> invisible = hidden();
        Map<String, Integer> manual = new HashMap<>();
        String order = preferences.getString(ORDER_KEY, "");
        if (order != null && !order.isEmpty()) {
            String[] list = order.split("\\|");
            for (int i = 0; i < list.length; i++) manual.put(list[i], i);
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
            boolean fa = favorites.contains(a.packageName);
            boolean fb = favorites.contains(b.packageName);
            if (fa != fb) return fa ? -1 : 1;
            return a.title.compareToIgnoreCase(b.title);
        });
        visibleApps.clear();
        for (AppEntry entry : allApps) {
            if (!invisible.contains(entry.packageName)) visibleApps.add(entry);
        }
        appRow.removeAllViews();
        for (AppEntry entry : visibleApps) appRow.addView(makeAppTile(entry, false));
        if (visibleApps.size() > 0) {
            ((HorizontalScrollView) findViewById(R.id.app_scroll)).fullScroll(View.FOCUS_LEFT);
        }
        refreshRecent();
    }

    private View makeAppTile(AppEntry entry, boolean small) {
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER);
        tile.setPadding(dp(9), dp(10), dp(9), dp(8));
        tile.setBackgroundResource(R.drawable.tile_selector);
        tile.setFocusable(true);
        tile.setClickable(true);
        int width = small ? 108 : 129;
        int height = small ? 73 : 108;
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(width), dp(height));
        lp.rightMargin = dp(11);
        tile.setLayoutParams(lp);

        ImageView icon = new ImageView(this);
        int size = small ? 33 : 47;
        icon.setLayoutParams(new LinearLayout.LayoutParams(dp(size), dp(size)));
        icon.setImageDrawable(entry.icon);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        tile.addView(icon);

        TextView label = new TextView(this);
        label.setText(entry.title);
        label.setTextSize(small ? 11 : 12);
        label.setSingleLine(true);
        label.setEllipsize(TextUtils.TruncateAt.END);
        label.setTextColor(getColor(R.color.ink));
        label.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lbl = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(22));
        lbl.topMargin = dp(4);
        label.setLayoutParams(lbl);
        tile.addView(label);
        if (!small && favorites().contains(entry.packageName)) {
            label.setText("★ " + entry.title);
        }
        tile.setContentDescription(entry.title);
        tile.setOnClickListener(v -> launch(entry));
        if (!small) tile.setOnLongClickListener(v -> {
            showAppOptions(entry);
            return true;
        });
        tile.setOnFocusChangeListener((view, focused) -> {
            if (focused) {
                ((HorizontalScrollView) (small
                        ? findViewById(R.id.recent_scroll)
                        : findViewById(R.id.app_scroll))).smoothScrollTo(
                        Math.max(0, view.getLeft() - dp(35)), 0);
            }
        });
        return tile;
    }

    private void launch(AppEntry entry) {
        Intent intent = getPackageManager().getLeanbackLaunchIntentForPackage(entry.packageName);
        if (intent == null) intent = getPackageManager().getLaunchIntentForPackage(entry.packageName);
        if (intent == null) {
            toast("Não foi possível abrir " + entry.title);
            return;
        }
        try {
            startActivity(intent);
            noteRecent(entry.packageName);
        } catch (RuntimeException error) {
            toast("Erro ao abrir " + entry.title);
        }
    }

    private void noteRecent(String packageName) {
        ArrayList<String> current = recentPackages();
        current.remove(packageName);
        current.add(0, packageName);
        while (current.size() > 5) current.remove(current.size() - 1);
        preferences.edit().putString(RECENT_KEY, TextUtils.join("|", current)).apply();
        refreshRecent();
    }

    private ArrayList<String> recentPackages() {
        ArrayList<String> list = new ArrayList<>();
        String saved = preferences.getString(RECENT_KEY, "");
        if (saved != null && !saved.isEmpty()) {
            for (String pkg : saved.split("\\|")) {
                if (!pkg.isEmpty()) list.add(pkg);
            }
        }
        return list;
    }

    private void refreshRecent() {
        recentRow.removeAllViews();
        Map<String, AppEntry> lookup = new HashMap<>();
        for (AppEntry app : allApps) lookup.put(app.packageName, app);
        for (String pkg : recentPackages()) {
            AppEntry entry = lookup.get(pkg);
            if (entry != null && !hidden().contains(pkg)) recentRow.addView(makeAppTile(entry, true));
        }
        int visibility = recentRow.getChildCount() == 0 ? View.GONE : View.VISIBLE;
        findViewById(R.id.recent_heading).setVisibility(visibility);
        findViewById(R.id.recent_scroll).setVisibility(visibility);
    }

    private void showAppOptions(AppEntry entry) {
        boolean isFavorite = favorites().contains(entry.packageName);
        String[] options = {"Mover uma posição à esquerda", "Mover uma posição à direita",
                isFavorite ? "Remover dos favoritos" : "Adicionar aos favoritos",
                "Ocultar da tela inicial"};
        new AlertDialog.Builder(this).setTitle(entry.title).setItems(options, (dialog, which) -> {
            if (which == 0) move(entry, -1);
            else if (which == 1) move(entry, 1);
            else if (which == 2) {
                Set<String> fav = favorites();
                if (!fav.add(entry.packageName)) fav.remove(entry.packageName);
                preferences.edit().putStringSet(FAVORITES_KEY, fav).apply();
                refreshApps();
            } else if (which == 3) setHidden(entry.packageName, true);
        }).setNegativeButton("Cancelar", null).show();
    }

    private void move(AppEntry entry, int delta) {
        int from = visibleApps.indexOf(entry), to = from + delta;
        if (from < 0 || to < 0 || to >= visibleApps.size()) {
            toast("O aplicativo já está no limite da lista.");
            return;
        }
        Collections.swap(visibleApps, from, to);
        ArrayList<String> pkgs = new ArrayList<>();
        for (AppEntry item : visibleApps) pkgs.add(item.packageName);
        for (AppEntry item : allApps)
            if (!pkgs.contains(item.packageName)) pkgs.add(item.packageName);
        preferences.edit().putString(ORDER_KEY, TextUtils.join("|", pkgs)).apply();
        refreshApps();
        if (to < appRow.getChildCount()) appRow.getChildAt(to).requestFocus();
    }

    private void setHidden(String pkg, boolean hide) {
        Set<String> hidden = hidden();
        if (hide) hidden.add(pkg);
        else hidden.remove(pkg);
        preferences.edit().putStringSet(HIDDEN_KEY, hidden).apply();
        refreshApps();
    }

    private void showManageOptions() {
        String[] choices = {"Mostrar aplicativo oculto", "Restaurar ordem sugerida",
                "Reexibir todos os aplicativos"};
        new AlertDialog.Builder(this).setTitle("Organizar aplicativos")
                .setItems(choices, (d, which) -> {
                    if (which == 0) showHiddenApps();
                    else if (which == 1) {
                        preferences.edit().remove(ORDER_KEY).apply();
                        refreshApps();
                        toast("Ordem inicial restaurada.");
                    } else if (which == 2) {
                        preferences.edit().remove(HIDDEN_KEY).apply();
                        refreshApps();
                        toast("Todos os aplicativos estão visíveis.");
                    }
                }).setNegativeButton("Cancelar", null).show();
    }

    private void showHiddenApps() {
        ArrayList<AppEntry> hiddenApps = new ArrayList<>();
        for (AppEntry app : allApps)
            if (hidden().contains(app.packageName)) hiddenApps.add(app);
        if (hiddenApps.isEmpty()) {
            toast("Nenhum aplicativo oculto.");
            return;
        }
        String[] names = new String[hiddenApps.size()];
        for (int i = 0; i < names.length; i++) names[i] = hiddenApps.get(i).title;
        new AlertDialog.Builder(this).setTitle("Reexibir aplicativo")
                .setItems(names, (d, which) -> setHidden(hiddenApps.get(which).packageName, false))
                .setNegativeButton("Cancelar", null).show();
    }

    private String token() {
        return preferences.getString(TMDB_KEY, "").trim();
    }

    /** Importação única via ADB, no armazenamento privado do aplicativo. */
    private void importTokenFromAdb() {
        File tokenFile = new File(getFilesDir(), "tmdb_access_token");
        if (!tokenFile.isFile()) return;
        try {
            if (tokenFile.length() > 4096) {
                toast("Arquivo TMDB inválido.");
                return;
            }
            try (BufferedReader in = new BufferedReader(
                    new InputStreamReader(new FileInputStream(tokenFile), StandardCharsets.UTF_8))) {
                String value = in.readLine();
                if (value == null) {
                    toast("Token TMDB vazio.");
                    return;
                }
                value = value.trim();
                if (value.startsWith("Bearer ")) value = value.substring(7).trim();
                if (value.length() < 50 || value.length() > 2048
                        || !value.matches("[A-Za-z0-9._-]+")) {
                    toast("Formato do token TMDB inválido.");
                    return;
                }
                preferences.edit().putString(TMDB_KEY, value).apply();
                toast("Token TMDB importado do Mac.");
            }
        } catch (IOException error) {
            toast("Erro ao importar o token TMDB.");
        } finally {
            tokenFile.delete();
        }
    }

    private void showTmdbSettings() {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint("API Read Access Token");
        input.setText(token());
        input.setSelectAllOnFocus(true);
        input.setTextSize(15);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        int margin = dp(20);
        LinearLayout outer = new LinearLayout(this);
        outer.setPadding(margin, 0, margin, 0);
        outer.addView(input, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        new AlertDialog.Builder(this).setTitle("Chave TMDB")
                .setMessage("Cole o API Read Access Token da sua conta TMDB. "
                        + "A chave será guardada apenas neste aparelho.")
                .setView(outer)
                .setPositiveButton("Salvar", (dialog, which) -> {
                    String newToken = input.getText().toString().trim();
                    if (newToken.startsWith("Bearer ")) newToken = newToken.substring(7).trim();
                    preferences.edit().putString(TMDB_KEY, newToken).apply();
                    // Quando uma credencial muda, invalidate os resultados do cliente anterior.
                    catalog.close();
                    catalog = new CatalogClient();
                    if (catalogModeIsSearch) runSearch();
                    else loadRecent();
                })
                .setNeutralButton("Limpar", (dialog, which) -> {
                    preferences.edit().remove(TMDB_KEY).apply();
                    catalog.close();
                    catalog = new CatalogClient();
                    mediaRow.removeAllViews();
                    status.setText("Configure o TMDB para consultar títulos.");
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void setupProviders() {
        providerRow.removeAllViews();
        for (int i = 0; i < CatalogClient.PROVIDERS.length; i++) {
            final String id = CatalogClient.PROVIDERS[i];
            TextView chip = chip(CatalogClient.PROVIDER_LABELS[i]);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = dp(7);
            chip.setLayoutParams(lp);
            providerRow.addView(chip);
            chip.setOnClickListener(v -> {
                chosenProvider = id;
                loadRecent();
            });
        }
        updateFilters();
    }

    private TextView chip(String label) {
        TextView v = new TextView(this);
        v.setText(label);
        v.setGravity(Gravity.CENTER);
        v.setTextSize(12);
        v.setTextColor(getColor(R.color.ink));
        v.setPadding(dp(14), dp(10), dp(14), dp(10));
        v.setBackgroundResource(R.drawable.tile_selector);
        v.setFocusable(true);
        v.setClickable(true);
        return v;
    }

    private void updateFilters() {
        movieTab.setAlpha("movie".equals(chosenType) ? 1f : .6f);
        tvTab.setAlpha("tv".equals(chosenType) ? 1f : .6f);
        for (int i = 0; i < providerRow.getChildCount(); i++) {
            View view = providerRow.getChildAt(i);
            view.setAlpha(CatalogClient.PROVIDERS[i].equals(chosenProvider) ? 1f : .55f);
        }
    }

    private void loadRecent() {
        catalogModeIsSearch = false;
        findViewById(R.id.reset_search).setVisibility(View.GONE);
        heading.setText("movie".equals(chosenType)
                ? "Filmes recentes disponíveis no Brasil" : "Séries recentes disponíveis no Brasil");
        updateFilters();
        final int id = ++requestId;
        mediaRow.removeAllViews();
        status.setText("Carregando novidades...");
        if (token().isEmpty()) {
            status.setText("Configure sua chave TMDB para ver os lançamentos.");
            return;
        }
        catalog.recent(token(), chosenType, chosenProvider, new CatalogClient.Callback<List<CatalogClient.Title>>() {
            @Override public void success(List<CatalogClient.Title> result) {
                if (id == requestId) showTitles(result);
            }
            @Override public void error(String reason) {
                if (id == requestId) status.setText(reason);
            }
        });
    }

    private void runSearch() {
        String query = searchInput.getText().toString().trim();
        if (query.length() < 2) {
            toast("Digite pelo menos dois caracteres.");
            return;
        }
        catalogModeIsSearch = true;
        findViewById(R.id.reset_search).setVisibility(View.VISIBLE);
        heading.setText("Resultados de busca");
        status.setText("Buscando filmes e séries...");
        mediaRow.removeAllViews();
        final int id = ++requestId;
        catalog.search(token(), query, new CatalogClient.Callback<List<CatalogClient.Title>>() {
            @Override public void success(List<CatalogClient.Title> result) {
                if (id == requestId) showTitles(result);
            }
            @Override public void error(String reason) {
                if (id == requestId) status.setText(reason);
            }
        });
    }

    private void showTitles(List<CatalogClient.Title> result) {
        mediaRow.removeAllViews();
        status.setText(result.isEmpty() ? "Nenhum título encontrado neste filtro."
                : result.size() + " títulos · Notas TMDB · Disponibilidade JustWatch");
        final int generation = requestId;
        for (CatalogClient.Title title : result) {
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
            card.setBackgroundResource(R.drawable.tile_selector);
            card.setPadding(dp(8), dp(8), dp(8), dp(10));
            card.setFocusable(true);
            card.setClickable(true);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(149), dp(267));
            lp.rightMargin = dp(13);
            card.setLayoutParams(lp);

            ImageView poster = new ImageView(this);
            poster.setScaleType(ImageView.ScaleType.FIT_CENTER);
            poster.setBackgroundColor(0xFF202E40);
            poster.setLayoutParams(new LinearLayout.LayoutParams(dp(115), dp(172)));
            card.addView(poster);
            catalog.poster(title.poster, poster);

            TextView name = new TextView(this);
            name.setText(title.title);
            name.setTextColor(getColor(R.color.ink));
            name.setTextSize(12);
            name.setMaxLines(2);
            name.setEllipsize(TextUtils.TruncateAt.END);
            name.setGravity(Gravity.CENTER);
            name.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(37)));
            card.addView(name);

            TextView score = new TextView(this);
            score.setText(title.votes > 0
                    ? String.format(Locale.forLanguageTag("pt-BR"), "TMDB %.1f/10", title.rating)
                    : "Sem nota");
            score.setTextColor(getColor(R.color.focus));
            score.setTextSize(11);
            score.setGravity(Gravity.CENTER);
            card.addView(score);

            TextView badge = makeStreamingBadge("Verificando...");
            LinearLayout.LayoutParams badgeParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(26));
            badgeParams.topMargin = dp(5);
            badge.setLayoutParams(badgeParams);
            badge.setVisibility(View.INVISIBLE);
            card.addView(badge);

            card.setContentDescription(title.title + ", " + score.getText());
            card.setOnClickListener(v -> showTitle(title));
            mediaRow.addView(card);

            // Apenas a disponibilidade de assinatura, nunca aluguel/compra.
            // Duas consultas paralelas no máximo, com cache na sessão.
            catalog.whereToWatch(token(), title, new CatalogClient.Callback<CatalogClient.WhereToWatch>() {
                @Override public void success(CatalogClient.WhereToWatch where) {
                    if (generation != requestId || mediaRow.indexOfChild(card) < 0) return;
                    if (where.providers.isEmpty()) {
                        badge.setVisibility(View.GONE);
                        return;
                    }
                    badge.setText(shortStreamingLabel(where.providers));
                    badge.setVisibility(View.VISIBLE);
                    card.setContentDescription(title.title + ", disponível em "
                            + TextUtils.join(", ", where.providers));
                }

                @Override public void error(String reason) {
                    if (generation == requestId) badge.setVisibility(View.GONE);
                }
            });
        }
    }

    private String shortStreamingLabel(List<String> providers) {
        if (providers.isEmpty()) return "";
        String selected = "";
        if (!catalogModeIsSearch && !CatalogClient.ALL.equals(chosenProvider)) {
            String filter = providerLabel(chosenProvider);
            for (String provider : providers) {
                if (provider.equalsIgnoreCase(filter)) {
                    selected = provider;
                    break;
                }
            }
        }
        if (selected.isEmpty()) selected = providers.get(0);
        int others = providers.size() - 1;
        return others > 0 ? selected + " +" + others : selected;
    }

    private String providerLabel(String id) {
        for (int i = 0; i < CatalogClient.PROVIDERS.length; i++) {
            if (CatalogClient.PROVIDERS[i].equals(id)) return CatalogClient.PROVIDER_LABELS[i];
        }
        return "";
    }

    private TextView makeStreamingBadge(String name) {
        TextView badge = new TextView(this);
        badge.setText(name);
        badge.setTextColor(0xFFE2F4FF);
        badge.setTextSize(11);
        badge.setSingleLine(true);
        badge.setEllipsize(TextUtils.TruncateAt.END);
        badge.setGravity(Gravity.CENTER);
        badge.setPadding(dp(6), dp(4), dp(6), dp(4));
        badge.setBackgroundResource(R.drawable.streaming_badge);
        return badge;
    }

    private TextView makeDetailButton(String label) {
        TextView button = new TextView(this);
        button.setText(label);
        button.setTextColor(getColor(R.color.ink));
        button.setTextSize(12);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(12), dp(10), dp(12), dp(10));
        button.setBackgroundResource(R.drawable.tile_selector);
        button.setFocusable(true);
        button.setClickable(true);
        return button;
    }

    private TextView detailText(String value, int size, int color) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextColor(color);
        text.setTextSize(size);
        return text;
    }

    private void showTitle(CatalogClient.Title title) {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.HORIZONTAL);
        panel.setGravity(Gravity.TOP);
        panel.setPadding(dp(21), dp(21), dp(22), dp(20));
        panel.setBackgroundResource(R.drawable.detail_panel);

        ImageView poster = new ImageView(this);
        poster.setScaleType(ImageView.ScaleType.FIT_CENTER);
        poster.setBackgroundColor(0xFF202E40);
        LinearLayout.LayoutParams posterParams = new LinearLayout.LayoutParams(dp(175), dp(258));
        posterParams.rightMargin = dp(24);
        panel.addView(poster, posterParams);
        catalog.poster(title.poster, poster);

        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        panel.addView(right, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        ScrollView detailsScroll = new ScrollView(this);
        detailsScroll.setFillViewport(false);
        detailsScroll.setVerticalScrollBarEnabled(false);
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(218));
        right.addView(detailsScroll, scrollParams);

        LinearLayout details = new LinearLayout(this);
        details.setOrientation(LinearLayout.VERTICAL);
        detailsScroll.addView(details);

        TextView name = detailText(title.title, 22, getColor(R.color.ink));
        name.setMaxLines(2);
        name.setEllipsize(TextUtils.TruncateAt.END);
        name.setTypeface(null, android.graphics.Typeface.BOLD);
        details.addView(name);

        String year = title.date.length() >= 4 ? title.date.substring(0, 4) : "Ano indisponível";
        TextView meta = detailText(
                ("tv".equals(title.type) ? "Série" : "Filme") + "  ·  " + year
                        + "  ·  " + (title.votes > 0
                        ? String.format(Locale.forLanguageTag("pt-BR"), "TMDB %.1f/10", title.rating)
                        : "Ainda sem nota"),
                14, getColor(R.color.focus));
        LinearLayout.LayoutParams metaParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        metaParams.topMargin = dp(7);
        details.addView(meta, metaParams);

        TextView availableHeading = detailText(
                "ONDE ASSISTIR · BRASIL", 12, getColor(R.color.subtle));
        LinearLayout.LayoutParams availableParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        availableParams.topMargin = dp(16);
        details.addView(availableHeading, availableParams);

        TextView availability = detailText(
                "Consultando serviços por assinatura...", 12, getColor(R.color.subtle));
        details.addView(availability);

        HorizontalScrollView providerScroll = new HorizontalScrollView(this);
        providerScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout streamingRow = new LinearLayout(this);
        streamingRow.setOrientation(LinearLayout.HORIZONTAL);
        streamingRow.setPadding(0, dp(6), 0, dp(7));
        providerScroll.addView(streamingRow);
        details.addView(providerScroll);

        TextView source = detailText(
                "Notas: TMDB  ·  Disponibilidade: JustWatch via TMDB", 11, getColor(R.color.subtle));
        details.addView(source);

        TextView synopsisHeading = detailText("SINOPSE", 12, getColor(R.color.subtle));
        LinearLayout.LayoutParams subLabelParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subLabelParams.topMargin = dp(14);
        details.addView(synopsisHeading, subLabelParams);

        TextView synopsis = detailText(
                title.overview.isEmpty() ? "Sinopse não disponível em português." : title.overview,
                14, getColor(R.color.ink));
        LinearLayout.LayoutParams synopsisParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        synopsisParams.topMargin = dp(3);
        details.addView(synopsis, synopsisParams);

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams buttonsParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        buttonsParams.topMargin = dp(10);
        right.addView(buttons, buttonsParams);

        TextView close = makeDetailButton("VOLTAR");
        LinearLayout.LayoutParams closeParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        closeParams.rightMargin = dp(8);
        buttons.addView(close, closeParams);

        TextView tmdb = makeDetailButton("VER NO TMDB");
        LinearLayout.LayoutParams tmdbParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tmdbParams.rightMargin = dp(8);
        buttons.addView(tmdb, tmdbParams);

        TextView launchApp = makeDetailButton("ABRIR STREAMING (APP)");
        launchApp.setVisibility(View.GONE);
        buttons.addView(launchApp);

        AlertDialog dialog = new AlertDialog.Builder(this).setView(panel).create();
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
            DisplayMetrics metrics = getResources().getDisplayMetrics();
            window.setLayout(Math.max(dp(300), Math.min(dp(850), metrics.widthPixels - dp(44))),
                    WindowManager.LayoutParams.WRAP_CONTENT);
        }
        close.setOnClickListener(v -> dialog.dismiss());
        tmdb.setOnClickListener(v ->
                openUrl("https://www.themoviedb.org/" + title.type + "/" + title.id));
        close.requestFocus();

        catalog.whereToWatch(token(), title, new CatalogClient.Callback<CatalogClient.WhereToWatch>() {
            @Override public void success(CatalogClient.WhereToWatch found) {
                if (!dialog.isShowing()) return;
                streamingRow.removeAllViews();
                if (found.providers.isEmpty()) {
                    availability.setText("Nenhum serviço de assinatura informado para o Brasil.");
                    return;
                }
                availability.setText("Disponível por assinatura:");
                for (String service : found.providers) {
                    TextView badge = makeStreamingBadge(service);
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT, dp(27));
                    lp.rightMargin = dp(8);
                    streamingRow.addView(badge, lp);
                }

                ArrayList<AppEntry> installed = streamingApps(found.providers);
                if (!installed.isEmpty()) {
                    launchApp.setVisibility(View.VISIBLE);
                    launchApp.setOnClickListener(v -> {
                        if (installed.size() == 1) {
                            toast("Abrindo o aplicativo; o filme não é reproduzido diretamente.");
                            dialog.dismiss();
                            launch(installed.get(0));
                            return;
                        }
                        String[] names = new String[installed.size()];
                        for (int i = 0; i < installed.size(); i++)
                            names[i] = "Abrir " + installed.get(i).title + " (app)";
                        new AlertDialog.Builder(HomeActivity.this)
                                .setTitle("Escolha o aplicativo")
                                .setItems(names, (d, which) -> {
                                    dialog.dismiss();
                                    toast("Abrindo o aplicativo, não o filme diretamente.");
                                    launch(installed.get(which));
                                })
                                .setNegativeButton("Cancelar", null)
                                .show();
                    });
                }
            }

            @Override public void error(String reason) {
                if (dialog.isShowing()) availability.setText(
                        "Não foi possível consultar o streaming. " + reason);
            }
        });
    }

    private ArrayList<AppEntry> streamingApps(List<String> providers) {
        ArrayList<AppEntry> installed = new ArrayList<>();
        for (String service : providers) {
            int priority = AppOrdering.priority("", service);
            if (priority < 0) continue;
            for (AppEntry app : allApps) {
                if (AppOrdering.priority(app.packageName, app.title) == priority
                        && !installed.contains(app)) {
                    installed.add(app);
                    break;
                }
            }
        }
        return installed;
    }

    private void showCredits() {
        LinearLayout credits = new LinearLayout(this);
        credits.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(20);
        credits.setPadding(padding, dp(8), padding, dp(4));

        ImageView tmdbLogo = new ImageView(this);
        tmdbLogo.setImageResource(R.drawable.tmdb_logo);
        tmdbLogo.setScaleType(ImageView.ScaleType.FIT_START);
        credits.addView(tmdbLogo, new LinearLayout.LayoutParams(dp(245), dp(38)));

        TextView notice = detailText(
                "This product uses TMDB and the TMDB APIs but is not endorsed, certified, "
                        + "or otherwise approved by TMDB.\n\n"
                        + "Dados, capas e avaliações: The Movie Database (TMDB).\n"
                        + "Disponibilidade de streaming: JustWatch, via TMDB.\n\n"
                        + "As notas exibidas são do TMDB, não do IMDb ou Rotten Tomatoes. "
                        + "A lista de filmes recentes indica data de estreia do título, "
                        + "não a data de entrada em cada catálogo. "
                        + "A busca não acessa seu histórico de reprodução.",
                13, getColor(R.color.ink));
        LinearLayout.LayoutParams noticeParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        noticeParams.topMargin = dp(12);
        credits.addView(notice, noticeParams);

        new AlertDialog.Builder(this).setTitle("Sobre o TelaLeve")
                .setView(credits)
                .setPositiveButton("TMDB", (d, which) -> openUrl("https://www.themoviedb.org"))
                .setNeutralButton("JustWatch", (d, which) -> openUrl("https://www.justwatch.com"))
                .setNegativeButton("Fechar", null).show();
    }

    private void openUrl(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)));
        } catch (RuntimeException exception) {
            toast("Nenhum navegador disponível para abrir o link.");
        }
    }
}
