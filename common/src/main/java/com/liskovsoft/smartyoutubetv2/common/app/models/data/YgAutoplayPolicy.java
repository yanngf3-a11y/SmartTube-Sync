package com.liskovsoft.smartyoutubetv2.common.app.models.data;

import android.util.Log;

import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItem;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemMetadata;
import com.liskovsoft.mediaserviceinterfaces.data.SearchOptions;
import com.liskovsoft.youtubeapi.service.YouTubeServiceManager;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.regex.Pattern;

import io.reactivex.disposables.Disposable;

/**
 * YG SYNC - Autoplay por GÉNERO (mismo ritmo), sin mixes y sin repetir artista.
 *
 * Cuando una canción llega desde el control (YG Sync Control) y no hay nada
 * más programado, SmartTube normalmente sigue con el "siguiente" de YouTube,
 * que casi siempre es un mix o más canciones del mismo artista.
 *
 * Esta clase reemplaza ese "siguiente" por una canción:
 *  - del mismo género/ritmo que la canción que pediste (bachata, salsa, etc.),
 *  - de OTRO artista (no repite el artista de la canción inicial ni los
 *    últimos artistas reproducidos),
 *  - que no sea un mix, enganchado, playlist, álbum completo ni video de horas,
 *  - que no se haya reproducido ya en esta cadena.
 *
 * Solo actúa sobre la cadena que arranca con una orden de YG Sync
 * (open / prepare). Si el video actual viene de una playlist real o el
 * usuario navega por su cuenta en SmartTube, no interviene.
 */
public final class YgAutoplayPolicy {

    private static final String TAG = "YgAutoplayPolicy";

    /** Si pasan más horas que esto sin una orden de YG Sync, la política se apaga. */
    private static final long ACTIVE_WINDOW_MS = 8L * 60L * 60L * 1000L;

    private static final int MAX_PLAYED = 80;
    private static final int MAX_RECENT_AUTHORS = 4;
    private static final int MAX_DECISIONS = 20;

    /** Duración razonable de una canción (descarta shorts y compilaciones largas). */
    private static final long MIN_DURATION_MS = 90L * 1000L;
    private static final long MAX_DURATION_MS = 10L * 60L * 1000L;

    private static final Random RANDOM = new Random();

    private static final String[] SEARCH_SUFFIXES = {
            "canciones", "video oficial", "exitos"
    };

    /** Títulos que delatan mixes / compilaciones (se evalúa sobre texto normalizado). */
    private static final Pattern BAD_TITLE = Pattern.compile(
            "\\b(mix|megamix|enganchado|enganchados|nonstop|non stop|playlist|compilacion|"
                    + "compilation|full album|album completo|lo mejor de|grandes exitos|"
                    + "mejores canciones|top \\d+|\\d+ horas?|\\d+ hours?|horas|hours)\\b");

    // ------------------------------------------------------------------
    // Géneros: clave, palabras que lo identifican y consulta de búsqueda.
    // El ORDEN importa: los más específicos van primero.
    // ------------------------------------------------------------------
    private static final class Genre {
        final String key;
        final String[] words;
        final String query;

        Genre(String key, String query, String... words) {
            this.key = key;
            this.query = query;
            this.words = words;
        }
    }

    private static final Genre[] GENRES = {
            new Genre("bachata", "bachata", "bachata", "bachatas"),
            new Genre("salsa", "salsa", "salsa", "salsero", "salsera"),
            new Genre("merengue", "merengue", "merengue", "merengues"),
            new Genre("dembow", "dembow", "dembow"),
            new Genre("reggaeton", "reggaeton", "reggaeton", "perreo"),
            new Genre("trap", "trap latino", "trap"),
            new Genre("vallenato", "vallenato", "vallenato", "vallenatos"),
            new Genre("cumbia", "cumbia", "cumbia", "cumbias"),
            new Genre("regional", "regional mexicano", "norteno", "corrido", "corridos",
                    "mariachi", "ranchera", "rancheras", "banda sinaloense", "duranguense",
                    "regional mexicano"),
            new Genre("bolero", "boleros", "bolero", "boleros"),
            new Genre("balada", "baladas romanticas", "balada", "baladas"),
            new Genre("tango", "tango", "tango"),
            new Genre("kpop", "k pop", "k pop", "kpop"),
            new Genre("metal", "heavy metal", "metal", "heavy metal"),
            new Genre("rock", "rock", "rock"),
            new Genre("hiphop", "hip hop", "hip hop", "hiphop", "rap"),
            new Genre("rnb", "r&b soul", "r b", "rnb", "soul"),
            new Genre("electronica", "musica electronica", "edm", "techno", "trance",
                    "electronica", "electronic", "dubstep", "deep house", "house music"),
            new Genre("reggae", "reggae", "reggae"),
            new Genre("country", "country", "country"),
            new Genre("jazz", "jazz", "jazz"),
            new Genre("clasica", "musica clasica", "clasica", "classical", "symphony", "orquesta"),
            new Genre("cristiana", "musica cristiana", "cristiana", "gospel", "worship",
                    "alabanza", "adoracion"),
            new Genre("pop", "pop", "pop"),
    };

    // ------------------------------------------------------------------
    // Artistas conocidos -> género (por si el título/descripción no dice el género).
    // Se pueden agregar más líneas sin tocar nada más. Nombres sin tildes.
    // ------------------------------------------------------------------
    private static final Map<String, String> ARTISTS = new HashMap<>();

    private static void artists(String genreKey, String... names) {
        for (String n : names) {
            ARTISTS.put(n, genreKey);
        }
    }

    static {
        artists("bachata", "romeo santos", "aventura", "prince royce", "anthony santos",
                "antony santos", "frank reyes", "zacarias ferreira", "monchy y alexandra",
                "raulin rodriguez", "joe veras", "luis vargas", "yoskar sarante", "toby love",
                "elvis martinez", "leonardo paniagua", "kiko rodriguez", "teodoro reyes",
                "xtreme", "grupo extra", "dani j", "el torito", "hector acosta");
        artists("salsa", "marc anthony", "hector lavoe", "willie colon", "celia cruz",
                "gilberto santa rosa", "victor manuelle", "ruben blades", "grupo niche",
                "joe arroyo", "oscar d leon", "el gran combo", "la india", "tito nieves",
                "jerry rivera", "frankie ruiz", "eddie santiago", "lalo rodriguez",
                "tito puente", "fania all stars", "ismael rivera", "cheo feliciano",
                "ray barretto", "sonora poncena", "dark latin groove");
        artists("merengue", "elvis crespo", "olga tanon", "wilfrido vargas", "johnny ventura",
                "los hermanos rosario", "fernando villalona", "milly quezada", "sergio vargas",
                "eddy herrera", "jossie esteban", "ala jaza", "rubby perez", "grupo mania");
        artists("reggaeton", "daddy yankee", "don omar", "wisin y yandel", "wisin", "yandel",
                "bad bunny", "karol g", "j balvin", "ozuna", "anuel aa", "maluma", "nicky jam",
                "farruko", "rauw alejandro", "tego calderon", "arcangel", "de la ghetto",
                "zion y lennox", "plan b", "jhay cortez", "myke towers", "sech", "feid");
        artists("dembow", "el alfa", "rochy rd", "bulin 47", "tokischa", "yailin");
        artists("vallenato", "diomedes diaz", "binomio de oro", "carlos vives",
                "silvestre dangond", "jorge celedon", "los inquietos", "peter manjarres",
                "kaleth morales", "rafael orozco");
        artists("cumbia", "los angeles azules", "celso pina", "la sonora dinamita",
                "kumbia kings");
        artists("regional", "peso pluma", "grupo frontera", "fuerza regida", "natanael cano",
                "junior h", "eslabon armado", "luis r conriquez", "calibre 50",
                "los tigres del norte", "banda ms", "vicente fernandez", "alejandro fernandez",
                "pepe aguilar", "intocable", "gerardo ortiz", "christian nodal", "carin leon",
                "grupo firme");
        artists("balada", "ricardo arjona", "ricardo montaner", "jose jose", "camilo sesto",
                "franco de vita", "marco antonio solis", "cristian castro");
        artists("rock", "queen", "led zeppelin", "ac dc", "nirvana", "guns n roses");
        artists("metal", "metallica", "iron maiden");
    }

    // ------------------------------------------------------------------
    // Estado (una sola cadena activa a la vez)
    // ------------------------------------------------------------------
    private static String sSeedVideoId;
    private static boolean sSeedPending;
    private static String sGenreKey;
    private static final Set<String> sSeedArtists = new HashSet<>();
    private static final Set<String> sChain = new HashSet<>();
    private static final LinkedList<String> sPlayed = new LinkedList<>();
    private static final LinkedList<String> sRecentAuthors = new LinkedList<>();
    private static final Map<String, MediaItem> sDecisions = new LinkedHashMap<>();
    private static String sCurrentId;
    private static String sLastChosenId;
    private static long sLastSeedTimeMs;
    private static Disposable sSearchAction;

    private YgAutoplayPolicy() {
    }

    // ------------------------------------------------------------------
    // API pública
    // ------------------------------------------------------------------

    /**
     * Se llama desde el puente de YG Sync justo antes de abrir un video
     * por orden del control (open / prepare). Inicia una cadena nueva,
     * salvo que sea el mismo video que ya está sonando por autoplay
     * (el mirror del control reenvía los cambios automáticos).
     */
    public static synchronized void onSyncOpen(String videoId) {
        if (videoId == null || videoId.trim().isEmpty()) {
            return;
        }

        String id = videoId.trim();
        long now = System.currentTimeMillis();

        boolean continuation = isActive(now)
                && (id.equals(sCurrentId) || id.equals(sLastChosenId));

        sLastSeedTimeMs = now;

        if (continuation) {
            return;
        }

        cancelSearch();
        sSeedVideoId = id;
        sSeedPending = true;
        sGenreKey = null;
        sSeedArtists.clear();
        sChain.clear();
        sPlayed.clear();
        sRecentAuthors.clear();
        sDecisions.clear();
        sCurrentId = null;
        sLastChosenId = null;

        sChain.add(id);
        markPlayed(id);
    }

    /** ¿Esta política debe decidir el siguiente video de "current"? */
    public static synchronized boolean appliesTo(Video current) {
        if (current == null || current.videoId == null) {
            return false;
        }

        if (!isActive(System.currentTimeMillis())) {
            return false;
        }

        if (!sChain.contains(current.videoId)) {
            return false;
        }

        // Playlist real del usuario (no mix): se respeta su orden normal.
        String pl = current.playlistId;
        return pl == null || pl.startsWith("RD");
    }

    /**
     * Devuelve el siguiente video según la política. Puede devolver null
     * si todavía se está buscando (la búsqueda asigna current.nextMediaItem
     * cuando termina, mucho antes de que acabe la canción).
     */
    public static synchronized MediaItem chooseNext(Video current, MediaItemMetadata metadata) {
        if (current == null || current.videoId == null || metadata == null) {
            return null;
        }

        final String id = current.videoId;

        MediaItem cached = sDecisions.get(id);
        if (cached != null) {
            return cached;
        }

        sCurrentId = id;

        String title = metadata.getTitle() != null ? metadata.getTitle() : current.title;
        String author = metadata.getAuthor() != null ? metadata.getAuthor() : current.author;

        if (sSeedPending && id.equals(sSeedVideoId)) {
            detectSeed(title, author, metadata.getDescription());
            sSeedPending = false;
        }

        markPlayed(id);
        noteAuthor(normArtist(author));

        MediaItem defaultNext = metadata.getNextVideo();

        List<MediaItem> candidates = new ArrayList<>();
        if (defaultNext != null) {
            candidates.add(defaultNext);
        }
        List<MediaGroup> groups = metadata.getSuggestions();
        if (groups != null) {
            for (MediaGroup group : groups) {
                if (group == null || group.getMediaItems() == null) {
                    continue;
                }
                candidates.addAll(group.getMediaItems());
            }
        }

        MediaItem pick = select(candidates, true);
        boolean genreHit = pick != null;

        if (pick == null) {
            pick = select(candidates, false);
        }

        if (!genreHit && sGenreKey != null) {
            // No hay nada claramente del mismo género en los relacionados:
            // se busca en YouTube por género. Mientras tanto queda provisional "pick".
            startSearch(current, id, defaultNext);
        }

        if (pick == null && sGenreKey == null) {
            // Género desconocido y nada filtrable: último recurso, el siguiente normal.
            Log.d(TAG, "Sin candidato filtrado y sin género: se usa el siguiente por defecto");
            return defaultNext;
        }

        if (pick != null) {
            remember(id, pick);
        }

        return pick;
    }

    // ------------------------------------------------------------------
    // Selección
    // ------------------------------------------------------------------

    private static MediaItem select(List<MediaItem> items, boolean requireGenre) {
        List<MediaItem> ok = new ArrayList<>();

        for (MediaItem item : items) {
            if (passes(item, requireGenre)) {
                ok.add(item);
                if (ok.size() >= 5) {
                    break;
                }
            }
        }

        if (ok.isEmpty()) {
            return null;
        }

        // Algo de variedad: al azar entre los primeros 3 válidos.
        return ok.get(RANDOM.nextInt(Math.min(3, ok.size())));
    }

    private static boolean passes(MediaItem item, boolean requireGenre) {
        if (item == null) {
            return false;
        }

        String vid = item.getVideoId();
        if (vid == null || vid.isEmpty() || sPlayed.contains(vid)) {
            return false;
        }

        if (item.isLive() || item.isUpcoming() || item.isShorts()) {
            return false;
        }

        String pl = item.getPlaylistId();
        if (pl != null && pl.startsWith("RD")) {
            return false; // viene de un mix
        }

        if (Video.from(item).isMix()) {
            return false;
        }

        long d = item.getDurationMs();
        if (d > 0 && (d < MIN_DURATION_MS || d > MAX_DURATION_MS)) {
            return false;
        }

        String title = norm(item.getTitle());
        if (BAD_TITLE.matcher(title).find()) {
            return false;
        }

        String author = normArtist(item.getAuthor());
        if (!author.isEmpty() && (sSeedArtists.contains(author) || sRecentAuthors.contains(author))) {
            return false;
        }

        for (String artist : sSeedArtists) {
            if (artist.length() >= 4 && title.contains(artist)) {
                return false;
            }
        }

        if (requireGenre && !matchesGenre(title + " " + author)) {
            return false;
        }

        return true;
    }

    private static boolean matchesGenre(String text) {
        if (sGenreKey == null) {
            return false;
        }

        String padded = " " + text + " ";

        Genre genre = genreByKey(sGenreKey);
        if (genre != null) {
            for (String w : genre.words) {
                if (padded.contains(" " + w + " ")) {
                    return true;
                }
            }
        }

        for (Map.Entry<String, String> e : ARTISTS.entrySet()) {
            if (sGenreKey.equals(e.getValue()) && padded.contains(" " + e.getKey() + " ")) {
                return true;
            }
        }

        return false;
    }

    // ------------------------------------------------------------------
    // Detección del género de la canción inicial
    // ------------------------------------------------------------------

    private static void detectSeed(String title, String author, String description) {
        String t = norm(title);
        String a = normArtist(author);
        String desc = norm(description);
        if (desc.length() > 1000) {
            desc = desc.substring(0, 1000);
        }

        String tPad = " " + t + " ";
        String aPad = " " + a + " ";
        String dPad = " " + desc + " ";

        if (!a.isEmpty()) {
            sSeedArtists.add(a);
        }

        Map<String, Integer> score = new HashMap<>();

        for (Genre g : GENRES) {
            int s = 0;
            for (String w : g.words) {
                String key = " " + w + " ";
                if (tPad.contains(key)) {
                    s += 3;
                }
                if (aPad.contains(key)) {
                    s += 2;
                }
                if (dPad.contains(key)) {
                    s += 1;
                }
            }
            if (s > 0) {
                score.put(g.key, s);
            }
        }

        for (Map.Entry<String, String> e : ARTISTS.entrySet()) {
            String name = e.getKey();
            String key = " " + name + " ";
            if (tPad.contains(key) || aPad.contains(key)) {
                sSeedArtists.add(name);
                Integer prev = score.get(e.getValue());
                score.put(e.getValue(), (prev == null ? 0 : prev) + 4);
            }
        }

        String best = null;
        int bestScore = 0;
        for (Genre g : GENRES) { // se recorre en orden para desempatar por especificidad
            Integer s = score.get(g.key);
            if (s != null && s > bestScore) {
                best = g.key;
                bestScore = s;
            }
        }

        sGenreKey = best;

        Log.d(TAG, "Semilla: titulo='" + title + "' autor='" + author + "' genero=" + best
                + " artistasExcluidos=" + sSeedArtists);
    }

    // ------------------------------------------------------------------
    // Búsqueda por género (cuando los relacionados no alcanzan)
    // ------------------------------------------------------------------

    private static void startSearch(final Video current, final String forVideoId,
                                    final MediaItem defaultNext) {
        Genre genre = genreByKey(sGenreKey);
        if (genre == null) {
            return;
        }

        String suffix = SEARCH_SUFFIXES[RANDOM.nextInt(SEARCH_SUFFIXES.length)];
        final String query = genre.query + " " + suffix;

        cancelSearch();

        try {
            Log.d(TAG, "Buscando siguiente por genero: '" + query + "'");

            sSearchAction = YouTubeServiceManager.instance().getContentService()
                    .getSearchObserve(query, SearchOptions.TYPE_VIDEO)
                    .subscribe(
                            groups -> onSearchResult(current, forVideoId, groups, defaultNext),
                            error -> {
                                Log.e(TAG, "Error buscando por genero: " + error.getMessage());
                                onSearchFailed(current, forVideoId, defaultNext);
                            }
                    );
        } catch (Exception e) {
            Log.e(TAG, "No se pudo iniciar la busqueda por genero", e);
            onSearchFailed(current, forVideoId, defaultNext);
        }
    }

    private static synchronized void onSearchResult(Video current, String forVideoId,
                                                    Iterable<MediaGroup> groups,
                                                    MediaItem defaultNext) {
        if (!forVideoId.equals(sCurrentId)) {
            return; // el usuario ya cambió de canción
        }

        List<MediaItem> items = new ArrayList<>();
        if (groups != null) {
            for (MediaGroup group : groups) {
                if (group == null || group.getMediaItems() == null) {
                    continue;
                }
                items.addAll(group.getMediaItems());
            }
        }

        // Los resultados ya vienen de la búsqueda del género: no se exige texto de género.
        MediaItem pick = select(items, false);

        if (pick != null) {
            remember(forVideoId, pick);
            current.nextMediaItem = pick;
            Log.d(TAG, "Siguiente por genero: " + pick.getTitle());
        } else {
            onSearchFailed(current, forVideoId, defaultNext);
        }
    }

    private static synchronized void onSearchFailed(Video current, String forVideoId,
                                                    MediaItem defaultNext) {
        if (!forVideoId.equals(sCurrentId)) {
            return;
        }

        // Último recurso para que la música no se corte.
        if (current.nextMediaItem == null && defaultNext != null) {
            current.nextMediaItem = defaultNext;
        }
    }

    private static void cancelSearch() {
        if (sSearchAction != null && !sSearchAction.isDisposed()) {
            sSearchAction.dispose();
        }
        sSearchAction = null;
    }

    // ------------------------------------------------------------------
    // Utilidades
    // ------------------------------------------------------------------

    private static boolean isActive(long now) {
        return sSeedVideoId != null && (now - sLastSeedTimeMs) < ACTIVE_WINDOW_MS;
    }

    private static void remember(String forVideoId, MediaItem pick) {
        sDecisions.put(forVideoId, pick);
        while (sDecisions.size() > MAX_DECISIONS) {
            String first = sDecisions.keySet().iterator().next();
            sDecisions.remove(first);
        }

        String pickId = pick.getVideoId();
        if (pickId != null) {
            sChain.add(pickId);
            sLastChosenId = pickId;
            markPlayed(pickId);
        }

        noteAuthor(normArtist(pick.getAuthor()));
    }

    private static void markPlayed(String videoId) {
        if (videoId == null || sPlayed.contains(videoId)) {
            return;
        }
        sPlayed.add(videoId);
        while (sPlayed.size() > MAX_PLAYED) {
            sPlayed.removeFirst();
        }
    }

    private static void noteAuthor(String author) {
        if (author == null || author.isEmpty()) {
            return;
        }
        sRecentAuthors.remove(author);
        sRecentAuthors.add(author);
        while (sRecentAuthors.size() > MAX_RECENT_AUTHORS) {
            sRecentAuthors.removeFirst();
        }
    }

    private static Genre genreByKey(String key) {
        if (key == null) {
            return null;
        }
        for (Genre g : GENRES) {
            if (g.key.equals(key)) {
                return g;
            }
        }
        return null;
    }

    private static String norm(String s) {
        if (s == null) {
            return "";
        }
        String n = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return n.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
    }

    private static String normArtist(String author) {
        String n = norm(author);
        n = n.replaceAll("\\b(topic|vevo|oficial|official|music|musica|records|channel)\\b", " ");
        return n.replaceAll("\\s+", " ").trim();
    }
}
