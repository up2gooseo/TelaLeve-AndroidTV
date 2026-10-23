package io.github.up2gooseo.telaleve;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Cliente TMDB somente leitura. Nenhuma credencial é incluída no APK:
 * cada usuário informa seu próprio token nas configurações locais.
 */
public final class CatalogClient {
    private static final String API = "https://api.themoviedb.org/3";
    private static final String IMAGE = "https://image.tmdb.org/t/p/w185";
    public static final String ALL = "TODOS";
    public static final String[] PROVIDERS = {
            ALL, "NETFLIX", "PRIME", "DISNEY", "APPLE", "HBO", "CRUNCHYROLL"
    };
    public static final String[] PROVIDER_LABELS = {
            "Todos", "Netflix", "Prime Video", "Disney+", "Apple TV+", "HBO Max", "Crunchyroll"
    };

    public interface Callback<T> {
        void success(T result);
        void error(String message);
    }

    public static final class Title {
        public final int id;
        public final String type;
        public final String title;
        public final String overview;
        public final String date;
        public final String poster;
        public final double rating;
        public final int votes;

        public Title(int id, String type, String title, String overview,
                     String date, String poster, double rating, int votes) {
            this.id = id;
            this.type = type;
            this.title = title;
            this.overview = overview;
            this.date = date;
            this.poster = poster;
            this.rating = rating;
            this.votes = votes;
        }

        public String summary() {
            String kind = "tv".equals(type) ? "Série" : "Filme";
            String score = votes > 0
                    ? String.format(Locale.forLanguageTag("pt-BR"),
                    "TMDB %.1f/10 (%d votos)", rating, votes)
                    : "TMDB: ainda sem avaliações";
            String year = date.length() >= 4 ? date.substring(0, 4) : "Data não informada";
            return kind + " · " + year + "\n" + score + "\n\n"
                    + (overview.isEmpty() ? "Sinopse não disponível em português." : overview);
        }
    }

    public static final class WhereToWatch {
        public final List<String> providers;
        public final String tmdbLink;

        public WhereToWatch(List<String> providers, String tmdbLink) {
            this.providers = providers;
            this.tmdbLink = tmdbLink;
        }
    }

    private static final class CachedResults {
        final long timestamp;
        final List<Title> results;
        CachedResults(List<Title> results) {
            this.timestamp = System.currentTimeMillis();
            this.results = results;
        }
    }

    private static final long WATCH_TTL_MS = 30L * 60L * 1000L;

    private static final class CachedWatch {
        final WhereToWatch data;
        final long timestamp;
        CachedWatch(WhereToWatch value) {
            data = value;
            timestamp = System.currentTimeMillis();
        }
    }

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService requests = Executors.newSingleThreadExecutor();
    private final ExecutorService images = Executors.newFixedThreadPool(2);
    // Separado das buscas: badges não bloqueiam a consulta de novos títulos.
    private final ExecutorService watchRequests = Executors.newFixedThreadPool(2);
    private final Map<String, CachedWatch> watchCache = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Integer>> providerCache = new HashMap<>();
    private final Map<String, CachedResults> discoverCache = new HashMap<>();
    private final LruCache<String, Bitmap> posterCache =
            new LruCache<String, Bitmap>(4 * 1024 * 1024) {
                @Override protected int sizeOf(String key, Bitmap bitmap) {
                    return bitmap.getByteCount();
                }
            };
    private volatile boolean closed;

    public void close() {
        closed = true;
        requests.shutdownNow();
        images.shutdownNow();
        watchRequests.shutdownNow();
        watchCache.clear();
        posterCache.evictAll();
    }

    public void recent(String token, String mediaType, String chosenProvider,
                       Callback<List<Title>> callback) {
        if (token == null || token.trim().isEmpty()) {
            callback.error("Configure seu token TMDB para carregar filmes e séries.");
            return;
        }
        final String type = "tv".equals(mediaType) ? "tv" : "movie";
        final String key = type + ":" + chosenProvider;
        CachedResults cached = discoverCache.get(key);
        if (cached != null && System.currentTimeMillis() - cached.timestamp < 15 * 60 * 1000L) {
            callback.success(cached.results);
            return;
        }
        requests.execute(() -> {
            try {
                Map<String, Integer> providers = providersFor(token, type);
                String ids = buildProviderFilter(chosenProvider, providers);
                if (ids.isEmpty()) throw new IllegalStateException(
                        "Este serviço ainda não aparece na lista TMDB para o Brasil.");

                Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
                SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
                format.setTimeZone(TimeZone.getTimeZone("UTC"));
                String today = format.format(calendar.getTime());
                calendar.add(Calendar.YEAR, -2);
                String start = format.format(calendar.getTime());
                String dateKey = "tv".equals(type) ? "first_air_date" : "primary_release_date";

                String url = API + "/discover/" + type
                        + "?language=pt-BR&watch_region=BR"
                        + "&with_watch_monetization_types=flatrate"
                        + "&with_watch_providers=" + encode(ids)
                        + "&sort_by=" + dateKey + ".desc"
                        + "&" + dateKey + ".gte=" + start
                        + "&" + dateKey + ".lte=" + today
                        + "&include_adult=false&page=1";
                JSONObject data = request(url, token);
                List<Title> titles = parseResults(data.optJSONArray("results"), type, 12);
                discoverCache.put(key, new CachedResults(titles));
                postSuccess(callback, titles);
            } catch (Exception error) {
                postError(callback, friendlyError(error));
            }
        });
    }

    public void search(String token, String query, Callback<List<Title>> callback) {
        if (token == null || token.trim().isEmpty()) {
            callback.error("Configure seu token TMDB para usar a busca.");
            return;
        }
        if (query == null || query.trim().length() < 2) {
            callback.error("Digite pelo menos dois caracteres.");
            return;
        }
        final String text = query.trim();
        requests.execute(() -> {
            try {
                String url = API + "/search/multi?language=pt-BR"
                        + "&query=" + encode(text) + "&include_adult=false&page=1";
                List<Title> titles = parseResults(request(url, token).optJSONArray("results"), null, 16);
                postSuccess(callback, titles);
            } catch (Exception error) {
                postError(callback, friendlyError(error));
            }
        });
    }

    /**
     * Lista provedores de assinatura (flatrate) informados no Brasil.
     * Duas consultas simultâneas no máximo, cache de 30 min; não bloqueia a busca.
     */
    public void whereToWatch(String token, Title title, Callback<WhereToWatch> callback) {
        if (token == null || token.trim().isEmpty()) {
            postError(callback, "Configure o token TMDB.");
            return;
        }
        final String key = title.type + ":" + title.id;
        CachedWatch cached = watchCache.get(key);
        if (cached != null && System.currentTimeMillis() - cached.timestamp < WATCH_TTL_MS) {
            postSuccess(callback, cached.data);
            return;
        }
        watchRequests.execute(() -> {
            try {
                JSONObject response = request(API + "/" + title.type + "/" + title.id
                        + "/watch/providers", token);
                JSONObject brazil = response.optJSONObject("results");
                brazil = brazil == null ? null : brazil.optJSONObject("BR");
                List<String> names = new ArrayList<>();
                if (brazil != null) {
                    JSONArray streaming = brazil.optJSONArray("flatrate");
                    if (streaming != null) {
                        for (int i = 0; i < streaming.length(); i++) {
                            JSONObject provider = streaming.optJSONObject(i);
                            if (provider == null) continue;
                            String label = canonicalProvider(provider.optString("provider_name", ""));
                            if (!label.isEmpty() && !names.contains(label)) names.add(label);
                        }
                    }
                }
                String url = brazil == null ? "" : brazil.optString("link", "");
                WhereToWatch result = new WhereToWatch(names, url);
                if (watchCache.size() > 80) watchCache.clear();
                watchCache.put(key, new CachedWatch(result));
                postSuccess(callback, result);
            } catch (Exception error) {
                postError(callback, friendlyError(error));
            }
        });
    }

    /** Nomes mais curtos, deduplicando variantes como Netflix com anúncios. */
    static String canonicalProvider(String name) {
        String clean = name == null ? "" : name.trim();
        String n = clean.toLowerCase(Locale.ROOT);
        if (n.startsWith("netflix")) return "Netflix";
        if (n.equals("amazon prime video") || n.equals("prime video")) return "Prime Video";
        if (n.equals("disney plus") || n.equals("disney+")) return "Disney+";
        if (n.equals("apple tv+") || n.equals("apple tv plus")) return "Apple TV+";
        if (n.equals("hbo max") || n.equals("max")) return "HBO Max";
        if (n.startsWith("crunchyroll")) return "Crunchyroll";
        return clean;
    }

    private Map<String, Integer> providersFor(String token, String type) throws Exception {
        Map<String, Integer> saved = providerCache.get(type);
        if (saved != null) return saved;
        JSONObject data = request(API + "/watch/providers/" + type
                + "?language=pt-BR&watch_region=BR", token);
        JSONArray list = data.optJSONArray("results");
        Map<String, Integer> values = new LinkedHashMap<>();
        if (list != null) {
            for (int i = 0; i < list.length(); i++) {
                JSONObject p = list.optJSONObject(i);
                if (p == null) continue;
                String name = p.optString("provider_name", "").toLowerCase(Locale.ROOT);
                int id = p.optInt("provider_id", -1);
                if (id <= 0) continue;
                putProvider(values, name, id);
            }
        }
        providerCache.put(type, values);
        return values;
    }

    static void putProvider(Map<String, Integer> target, String name, int id) {
        // Correspondência estrita impede confundir aluguel Apple TV com Apple TV+.
        if (name.equals("netflix")) target.put("NETFLIX", id);
        else if (name.equals("amazon prime video") || name.equals("prime video"))
            target.put("PRIME", id);
        else if (name.equals("disney plus") || name.equals("disney+"))
            target.put("DISNEY", id);
        else if (name.equals("apple tv+") || name.equals("apple tv plus"))
            target.put("APPLE", id);
        else if (name.equals("hbo max") || name.equals("max"))
            target.put("HBO", id);
        else if (name.equals("crunchyroll")) target.put("CRUNCHYROLL", id);
    }

    static String buildProviderFilter(String choice, Map<String, Integer> ids) {
        if (!ALL.equals(choice)) {
            Integer found = ids.get(choice);
            return found == null ? "" : Integer.toString(found);
        }
        StringBuilder selected = new StringBuilder();
        for (String provider : PROVIDERS) {
            if (ALL.equals(provider)) continue;
            Integer id = ids.get(provider);
            if (id != null) {
                if (selected.length() > 0) selected.append('|');
                selected.append(id);
            }
        }
        return selected.toString();
    }

    static List<Title> parseResults(JSONArray results, String assumedType, int limit) {
        ArrayList<Title> titles = new ArrayList<>();
        if (results == null) return titles;
        for (int i = 0; i < results.length() && titles.size() < limit; i++) {
            JSONObject row = results.optJSONObject(i);
            if (row == null || row.optBoolean("adult", false)) continue;
            String type = assumedType == null ? row.optString("media_type", "") : assumedType;
            if (!"movie".equals(type) && !"tv".equals(type)) continue;
            int id = row.optInt("id", 0);
            if (id <= 0) continue;
            String title = row.optString("movie".equals(type) ? "title" : "name", "");
            if (title.isEmpty()) continue;
            titles.add(new Title(id, type, title,
                    row.optString("overview", ""),
                    row.optString("movie".equals(type) ? "release_date" : "first_air_date", ""),
                    row.optString("poster_path", ""),
                    row.optDouble("vote_average", 0),
                    row.optInt("vote_count", 0)));
        }
        return titles;
    }

    public void poster(String path, ImageView image) {
        if (path == null || path.isEmpty() || "null".equals(path)) {
            image.setImageDrawable(null);
            return;
        }
        image.setTag(path);
        Bitmap cached = posterCache.get(path);
        if (cached != null) {
            image.setImageBitmap(cached);
            return;
        }
        image.setImageDrawable(null);
        images.execute(() -> {
            HttpURLConnection conn = null;
            try {
                conn = (HttpURLConnection) new URL(IMAGE + path).openConnection();
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(7000);
                if (conn.getResponseCode() != 200) return;
                BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inPreferredConfig = Bitmap.Config.RGB_565;
                Bitmap bitmap;
                try (InputStream in = conn.getInputStream()) {
                    bitmap = BitmapFactory.decodeStream(in, null, opts);
                }
                if (bitmap == null || closed) return;
                posterCache.put(path, bitmap);
                ui.post(() -> {
                    if (!closed && path.equals(image.getTag())) image.setImageBitmap(bitmap);
                });
            } catch (Exception ignored) {
                // Falha de imagem não bloqueia a navegação nem o restante do catálogo.
            } finally {
                if (conn != null) conn.disconnect();
            }
        });
    }

    private JSONObject request(String url, String token) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        try {
            conn.setConnectTimeout(7000);
            conn.setReadTimeout(8000);
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("Authorization", "Bearer " + token);
            int code = conn.getResponseCode();
            if (code == 401 || code == 403) throw new IllegalStateException("TOKEN");
            if (code != 200) throw new IllegalStateException("HTTP " + code);
            try (InputStream in = new BufferedInputStream(conn.getInputStream());
                 ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
                byte[] buf = new byte[4096];
                int len;
                while ((len = in.read(buf)) != -1) {
                    if (bytes.size() > 1024 * 1024) throw new IllegalStateException("Resposta muito grande");
                    bytes.write(buf, 0, len);
                }
                return new JSONObject(bytes.toString("UTF-8"));
            }
        } finally {
            conn.disconnect();
        }
    }

    private static String encode(String value) throws Exception {
        return URLEncoder.encode(value, "UTF-8");
    }

    private static String friendlyError(Exception error) {
        String message = error.getMessage() == null ? "" : error.getMessage();
        if (message.contains("TOKEN")) return "Token TMDB inválido. Abra CONFIGURAR TMDB.";
        if (message.contains("429")) return "Limite de consultas TMDB. Tente novamente depois.";
        return "Não foi possível carregar o TMDB. Verifique a internet e tente novamente.";
    }

    private <T> void postSuccess(Callback<T> callback, T value) {
        ui.post(() -> { if (!closed) callback.success(value); });
    }

    private <T> void postError(Callback<T> callback, String message) {
        ui.post(() -> { if (!closed) callback.error(message); });
    }
}
