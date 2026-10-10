package com.liskovsoft.smartyoutubetv2.common.app.models.data;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItem;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemMetadata;
import com.liskovsoft.mediaserviceinterfaces.data.SearchOptions;
import com.liskovsoft.youtubeapi.service.YouTubeServiceManager;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
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

import org.json.JSONArray;
import org.json.JSONObject;

import io.reactivex.disposables.Disposable;

/**
 * YG SYNC - Autoplay por GÉNERO (mismo ritmo), SOLO MÚSICA, sin mixes y sin repetir artista.
 *
 * Cuando una canción llega desde el control (YG Sync Control) y no hay nada
 * más programado, SmartTube normalmente sigue con el "siguiente" de YouTube,
 * que casi siempre es un mix o más canciones del mismo artista.
 *
 * Esta clase reemplaza ese "siguiente" por una canción:
 *  - del mismo género/ritmo que la canción que pediste (bachata, salsa, etc.),
 *  - de OTRO artista (nunca el de la canción que programaste, y evita los
 *    últimos artistas reproducidos),
 *  - que NO sea un mix, enganchado, playlist, álbum completo ni video de horas,
 *  - que sea MÚSICA de verdad: nunca programas de TV, series, películas, capítulos,
 *    entrevistas, noticias, etc. (ver isMusic / NOT_MUSIC_TITLE / TV_AUTHOR),
 *  - que no se haya reproducido ya en esta cadena.
 *
 * REGLA DE ORO: jamás se devuelve el "siguiente" original de YouTube (el mix).
 * Si no se encuentra nada válido, la política sigue buscando (relaja filtros
 * suaves y prueba varias búsquedas por género) pero nunca cae a un mix.
 *
 * Solo actúa sobre la cadena que arranca con una orden de YG Sync
 * (open / prepare). Si el video actual viene de una playlist real o el
 * usuario navega por su cuenta en SmartTube, no interviene.
 */
public final class YgAutoplayPolicy {

    private static final String TAG = "YgAutoplayPolicy";

    /** Si pasan más horas que esto SIN actividad, la política se apaga. */
    private static final long ACTIVE_WINDOW_MS = 8L * 60L * 60L * 1000L;

    private static final int MAX_PLAYED = 120;
    private static final int MAX_RECENT_ARTISTS = 8;
    private static final int MAX_DECISIONS = 20;
    private static final int MAX_SEARCH_ATTEMPTS = 6;
    private static final int PICK_POOL = 6;

    /** Cuántas canciones próximas se planean (y se muestran en el controller). */
    private static final int UPCOMING_SIZE = 5;

    /** Duración razonable de una canción (descarta shorts y compilaciones largas). */
    private static final long MIN_DURATION_MS = 90L * 1000L;
    private static final long MAX_DURATION_MS = 12L * 60L * 1000L;

    private static final Random RANDOM = new Random();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private static final String[] SEARCH_SUFFIXES = {
            "video oficial", "audio oficial", "cancion", "letra", "lyric video", "musica", "cancion romantica"
    };

    /** Títulos que delatan mixes / compilaciones (se evalúa sobre texto normalizado). */
    private static final Pattern BAD_TITLE = Pattern.compile(
            "\\b(mix|mixtape|megamix|enganchado|enganchados|medley|nonstop|non stop|playlist|"
                    + "compilacion|compilation|full album|album completo|lo mejor de|grandes exitos|"
                    + "mejores canciones|las mejores|los mejores|mas escuchadas|mas escuchados|exitos|"
                    + "pegaditas|pegadas|karaoke|dj set|top \\d+|"
                    + "\\d+ (exitos|canciones|temas|hits|songs|clasicos|musicas)|"
                    + "(\\d+|una|un|dos|tres|cuatro|cinco|seis|siete|ocho|one|two|three|four|five|six) "
                    + "(horas?|hours?|hrs?))\\b");

    /** Canales que son colecciones/radios, no artistas. */
    private static final Pattern BAD_AUTHOR = Pattern.compile(
            "\\b(mix|playlist|radio|compilacion|compilation|compilations)\\b");

    /** Etiqueta (badge) que YouTube pone a mixes, playlists y álbumes. */
    private static final Pattern BAD_BADGE = Pattern.compile(
            "\\b(mix|playlist|lista|album|videos?)\\b");

    /**
     * Títulos que delatan que NO es música (series, programas de TV, películas,
     * capítulos, entrevistas, noticias, etc.). Se evalúa sobre texto normalizado.
     */
    private static final Pattern NOT_MUSIC_TITLE = Pattern.compile(
            "\\b(capitulo|capitulos|cap \\d+|episodio|episodios|episode|episodes|ep \\d+|"
                    + "temporada|temporadas|season|seasons|serie|series|pelicula|peliculas|movie|movies|"
                    + "film|full movie|trailer|teaser|avance|entrevista|interview|noticias|noticiero|"
                    + "news|reaccion|reaccionando|reaction|react|review|resena|gameplay|tutorial|"
                    + "podcast|programa|programas|telenovela|documental|documentary|stand up|comedia|"
                    + "sketch|escena|escenas|scene|scenes|resumen|recap|discurso|sermon|predica|"
                    + "audiolibro|audiobook|como hacer|how to|unboxing|vlog|parte \\d+|part \\d+)\\b");

    /** Canales de televisión / cine / streaming (no son artistas ni sellos musicales). */
    private static final Pattern TV_AUTHOR = Pattern.compile(
            "\\b(netflix|hbo|disney|televisa|telemundo|univision|caracol tv|rcn|cnn|espn|"
                    + "tv azteca|telecinco|antena 3|nickelodeon|cartoon network|discovery|"
                    + "prime video|paramount pictures|sony pictures|universal pictures|warner bros)\\b");

    /** Señales FUERTES de que es una canción (video oficial, audio oficial, letra, remix...). */
    private static final Pattern MUSIC_TITLE = Pattern.compile(
            "\\b(official music video|official video|video oficial|videoclip|video clip|"
                    + "official audio|audio oficial|lyric video|official lyric|lyrics|letra|"
                    + "visualizer|video musical|music video|remix|acustico|acoustic|unplugged)\\b");

    /** Palabras de relleno que se ignoran al comparar nombres de canciones. */
    private static final Pattern SONG_NOISE = Pattern.compile(
            "\\b(official|oficial|video|videoclip|clip|audio|lyric|lyrics|letra|hd|4k|hq|"
                    + "remastered|remasterizado|version|live|en vivo|ft|feat|featuring)\\b");

    /** Partes sueltas de un nombre que no sirven como "artista". */
    private static final Set<String> ARTIST_STOPWORDS = new HashSet<>(Arrays.asList(
            "los", "las", "del", "the", "sus", "con", "feat", "orquesta", "grupo", "banda",
            "trio", "conjunto", "sonora", "oficial", "official", "video", "audio"));

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
            new Genre("salsa", "salsa", "salsa", "salsero", "salsera", "timba", "guaguanco"),
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
                "ray barretto", "sonora poncena", "dark latin groove", "rafael ithier",
                "andy montanez", "willie rosario", "johnny pacheco", "adalberto santiago",
                "bobby valentin", "roberto roena", "pete el conde rodriguez", "tito rojas",
                "domingo quinones", "rey ruiz", "luis enrique", "n klabe", "los van van",
                "son de cali", "grupo gale", "orquesta guayacan", "fruko y sus tesos",
                "ismael miranda", "santitos colon", "pupy santiago", "la excelencia");
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
    /** Artistas de la canción que programaste: NUNCA se repiten en la cadena. */
    private static final Set<String> sSeedArtists = new HashSet<>();
    private static final Set<String> sChain = new HashSet<>();
    private static final LinkedList<String> sPlayed = new LinkedList<>();
    private static final LinkedList<String> sPlayedSongs = new LinkedList<>();
    private static final LinkedList<String> sRecentArtists = new LinkedList<>();
    private static final Map<String, MediaItem> sDecisions = new LinkedHashMap<>();
    private static List<MediaItem> sFallbackCandidates = new ArrayList<>();
    /** Lista planeada de próximas canciones (la primera es la que sigue). */
    private static final LinkedList<MediaItem> sUpcoming = new LinkedList<>();
    private static String sCurrentId;
    private static String sLastChosenId;
    private static String sSearchingForId;
    private static long sLastActivityMs;
    private static Disposable sSearchAction;
    private static int sSearchToken;

    private YgAutoplayPolicy() {
    }

    // ------------------------------------------------------------------
    // API pública (la misma de siempre: Video.java y SyncPlaybackBridge
    // no necesitan cambios)
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

        sLastActivityMs = now;

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
        sPlayedSongs.clear();
        sRecentArtists.clear();
        sDecisions.clear();
        sUpcoming.clear();
        sFallbackCandidates = new ArrayList<>();
        sCurrentId = null;
        sLastChosenId = null;

        sChain.add(id);
        markPlayed(id, null);
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
     *
     * NUNCA devuelve el siguiente por defecto de YouTube (el mix).
     */
    public static synchronized MediaItem chooseNext(Video current, MediaItemMetadata metadata) {
        if (current == null || current.videoId == null || metadata == null) {
            return null;
        }

        final String id = current.videoId;
        sLastActivityMs = System.currentTimeMillis();

        MediaItem cached = sDecisions.get(id);
        if (cached != null) {
            return cached;
        }

        if (id.equals(sSearchingForId) && sSearchAction != null && !sSearchAction.isDisposed()) {
            return null; // ya se está buscando para este video
        }

        sCurrentId = id;
        cancelSearch(); // una decisión nueva reemplaza cualquier búsqueda anterior

        String title = firstNonEmpty(metadata.getTitle(), current.title);
        String author = firstNonEmpty(metadata.getAuthor(), current.author);

        List<MediaItem> candidates = collectCandidates(metadata);
        sFallbackCandidates = candidates;

        if (sSeedPending && id.equals(sSeedVideoId)) {
            detectSeed(title, author, metadata.getDescription(), candidates);
            sSeedPending = false;
        } else if (sGenreKey == null) {
            // Si al inicio no se pudo saber el género, se reintenta con lo que
            // YouTube relaciona a esta canción.
            sGenreKey = inferGenreFromCandidates(candidates);
            if (sGenreKey != null) {
                Log.d(TAG, "Genero inferido de relacionados: " + sGenreKey);
            }
        }

        markPlayed(id, title);
        noteArtists(artistKeys(title, author));

        MediaItem pick = takeFromUpcoming(id);

        if (pick != null) {
            // Se respeta la lista ya planeada (la misma que ve el controller).
            Log.d(TAG, "Siguiente tomado de la lista planeada");
        } else if (sGenreKey != null) {
            // 1) Del género, de otro artista, sin repetir los últimos.
            pick = select(candidates, true, true, false);
            // 2) Del género, permitiendo repetir un artista reciente (nunca el semilla).
            if (pick == null) {
                pick = select(candidates, true, false, false);
            }

            if (pick == null) {
                // Nada claramente del género en los relacionados: se busca por género.
                boolean started = startSearch(current, id, RANDOM.nextInt(SEARCH_SUFFIXES.length), 0);

                // Provisional para que la música no se corte si la búsqueda tarda o falla.
                pick = select(candidates, false, true, false);
                if (pick == null) {
                    pick = select(candidates, false, false, false);
                }
                if (pick == null && !started) {
                    pick = select(candidates, false, false, true);
                }
            }
        } else {
            // Género desconocido: se filtra todo lo malo y se toma lo mejor disponible.
            pick = select(candidates, false, true, false);
            if (pick == null) {
                pick = select(candidates, false, false, false);
            }
            if (pick == null) {
                pick = select(candidates, false, false, true);
            }
        }

        if (pick != null) {
            pick = sanitize(pick);
            remember(id, pick);
            Log.d(TAG, "Siguiente elegido: " + pick.getTitle() + " [" + pick.getAuthor() + "]");
        } else {
            Log.d(TAG, "Sin candidato valido por ahora (genero=" + sGenreKey + ")");
        }

        // Se actualiza la lista de próximas canciones (el 1.º es el siguiente).
        rebuildUpcoming(pick, candidates);

        // Jamás se devuelve metadata.getNextVideo() (el mix de YouTube).
        return pick;
    }

    // ------------------------------------------------------------------
    // Lista de próximas canciones (para mostrar en el controller)
    // ------------------------------------------------------------------

    /**
     * JSON con las próximas canciones planeadas, para el "status" que el TV
     * le manda al controller. Vacío si la política no está activa para el
     * video que suena ahora. Nunca incluye el video que ya está sonando.
     */
    public static synchronized JSONArray getUpcomingJson(String currentVideoId) {
        JSONArray arr = new JSONArray();

        if (currentVideoId == null || currentVideoId.isEmpty()) {
            return arr;
        }

        if (!isActive(System.currentTimeMillis()) || !sChain.contains(currentVideoId)) {
            return arr;
        }

        for (MediaItem item : sUpcoming) {
            if (item == null) {
                continue;
            }

            String vid = item.getVideoId();
            if (vid == null || vid.isEmpty() || vid.equals(currentVideoId)) {
                continue;
            }

            try {
                String t = item.getTitle();
                String a = item.getAuthor();

                JSONObject o = new JSONObject();
                o.put("videoId", vid);
                o.put("title", t == null ? "" : t);
                o.put("author", a == null ? "" : a);
                o.put("durationMs", item.getDurationMs());
                arr.put(o);
            } catch (Exception e) {
                Log.e(TAG, "No se pudo armar la lista de proximas", e);
            }
        }

        return arr;
    }

    /**
     * Toma de la lista planeada el siguiente válido (descartando lo que ya
     * está sonando). Lo que queda en la lista se revalida al reconstruirla.
     */
    private static MediaItem takeFromUpcoming(String currentId) {
        List<MediaItem> old = new ArrayList<>(sUpcoming);
        sUpcoming.clear();

        MediaItem found = null;

        for (MediaItem item : old) {
            if (item == null) {
                continue;
            }

            String vid = item.getVideoId();
            if (vid == null || vid.equals(currentId)) {
                continue;
            }

            if (found == null) {
                if (passes(item, false, false, false)) {
                    found = item;
                }
            } else {
                sUpcoming.add(item);
            }
        }

        return found;
    }

    /**
     * Rearma la lista: primero "head" (lo que sigue), luego lo que ya estaba
     * planeado si sigue siendo válido, y se completa con los relacionados
     * (solo música, de otros artistas, sin repetir artista dentro de la lista).
     */
    private static void rebuildUpcoming(MediaItem head, List<MediaItem> candidates) {
        if (head == null) {
            sUpcoming.clear();
            return;
        }

        List<MediaItem> keep = new ArrayList<>();
        Set<String> usedIds = new HashSet<>();
        Set<String> usedArtists = new HashSet<>();

        keep.add(head);
        if (head.getVideoId() != null) {
            usedIds.add(head.getVideoId());
        }
        usedArtists.addAll(artistKeys(head.getTitle(), head.getAuthor()));

        // Lo que ya estaba planeado (ya se había validado).
        for (MediaItem old : new ArrayList<>(sUpcoming)) {
            if (keep.size() >= UPCOMING_SIZE) {
                break;
            }
            if (canQueue(old, false, usedIds, usedArtists)) {
                keep.add(old);
            }
        }

        // Se completa con los relacionados de la canción actual.
        if (candidates != null) {
            boolean requireGenre = sGenreKey != null;

            for (MediaItem item : candidates) {
                if (keep.size() >= UPCOMING_SIZE) {
                    break;
                }
                if (canQueue(item, requireGenre, usedIds, usedArtists)) {
                    keep.add(item);
                }
            }
        }

        sUpcoming.clear();
        sUpcoming.addAll(keep);
    }

    /** Valida un candidato para la lista y, si sirve, reserva su id y artistas. */
    private static boolean canQueue(MediaItem item, boolean requireGenre,
                                    Set<String> usedIds, Set<String> usedArtists) {
        if (item == null) {
            return false;
        }

        String vid = item.getVideoId();
        if (vid == null || vid.isEmpty() || usedIds.contains(vid)) {
            return false;
        }

        if (!passes(item, requireGenre, true, false)) {
            return false;
        }

        Set<String> keys = artistKeys(item.getTitle(), item.getAuthor());
        for (String key : keys) {
            if (usedArtists.contains(key)) {
                return false;
            }
        }

        usedIds.add(vid);
        usedArtists.addAll(keys);
        return true;
    }

    // ------------------------------------------------------------------
    // Selección
    // ------------------------------------------------------------------

    /**
     * @param requireGenre       exige que título/canal digan el género (o sea artista conocido)
     * @param checkRecent        evita los últimos artistas reproducidos
     * @param allowPlaylistItems acepta videos que traen una playlist (no mixes "RD")
     */
    private static MediaItem select(List<MediaItem> items, boolean requireGenre,
                                    boolean checkRecent, boolean allowPlaylistItems) {
        if (items == null || items.isEmpty()) {
            return null;
        }

        List<MediaItem> ok = new ArrayList<>();

        for (MediaItem item : items) {
            if (passes(item, requireGenre, checkRecent, allowPlaylistItems)) {
                ok.add(item);
                if (ok.size() >= PICK_POOL) {
                    break;
                }
            }
        }

        if (ok.isEmpty()) {
            return null;
        }

        // Algo de variedad: al azar entre los primeros válidos.
        return ok.get(RANDOM.nextInt(ok.size()));
    }

    private static boolean passes(MediaItem item, boolean requireGenre, boolean checkRecent,
                                  boolean allowPlaylistItems) {
        if (item == null) {
            return false;
        }

        String vid = item.getVideoId();
        if (vid == null || vid.isEmpty() || sPlayed.contains(vid)) {
            return false;
        }

        if (item.isLive() || item.isUpcoming() || item.isShorts() || item.isMovie()) {
            return false;
        }

        int type = item.getType();
        if (type == MediaItem.TYPE_PLAYLIST || type == MediaItem.TYPE_CHANNEL) {
            return false;
        }

        // Cualquier mix de YouTube ("RD...") queda fuera siempre.
        String pl = item.getPlaylistId();
        boolean hasPlaylist = pl != null && !pl.isEmpty();
        if (hasPlaylist && (pl.startsWith("RD") || !allowPlaylistItems)) {
            return false;
        }

        if (Video.from(item).isMix()) {
            return false;
        }

        String badge = norm(item.getBadgeText());
        if (!badge.isEmpty() && BAD_BADGE.matcher(badge).find()) {
            return false;
        }

        long d = item.getDurationMs();
        if (d > 0 && (d < MIN_DURATION_MS || d > MAX_DURATION_MS)) {
            return false;
        }

        String rawTitle = item.getTitle();
        String title = norm(rawTitle);
        if (title.isEmpty() || BAD_TITLE.matcher(title).find()) {
            return false;
        }

        if (BAD_AUTHOR.matcher(norm(item.getAuthor())).find()) {
            return false;
        }

        // SOLO MÚSICA: nada de programas de TV, series, películas, capítulos, etc.
        if (NOT_MUSIC_TITLE.matcher(title).find()) {
            return false;
        }

        String authorNorm = norm(item.getAuthor());
        if (TV_AUTHOR.matcher(authorNorm).find() && !authorNorm.contains("music")) {
            return false;
        }

        if (!isMusic(rawTitle, title, item.getAuthor(), d)) {
            return false;
        }

        // No repetir la misma canción (aunque venga de otro canal o subida).
        String song = songKey(rawTitle);
        if (song != null && sPlayedSongs.contains(song)) {
            return false;
        }

        // Nunca el artista de la canción programada.
        String paddedTitle = " " + title + " ";
        for (String seed : sSeedArtists) {
            if (seed.length() >= 4 && paddedTitle.contains(" " + seed + " ")) {
                return false;
            }
        }

        Set<String> keys = artistKeys(rawTitle, item.getAuthor());

        for (String key : keys) {
            if (sSeedArtists.contains(key)) {
                return false;
            }
        }

        if (checkRecent) {
            for (String key : keys) {
                if (sRecentArtists.contains(key)) {
                    return false;
                }
            }
        }

        if (requireGenre && !matchesGenre(title + " " + normArtist(item.getAuthor()))) {
            return false;
        }

        return true;
    }

    /**
     * ¿Parece una canción? Debe tener al menos una señal musical:
     *  - FUERTE: canal "- Topic" o VEVO, "video oficial", "audio oficial", letra, remix,
     *    o un artista conocido de la lista.
     *  - DÉBIL: formato "Artista - Canción" con duración conocida de canción, o una palabra
     *    del género de la cadena en el título/canal.
     * Si la duración es desconocida, solo se acepta con una señal FUERTE.
     */
    private static boolean isMusic(String rawTitle, String normTitle, String author, long durationMs) {
        String authorNorm = norm(author);
        String padded = " " + normTitle + " " + normArtist(author) + " ";

        boolean strong = authorNorm.endsWith(" topic")
                || authorNorm.contains("vevo")
                || MUSIC_TITLE.matcher(normTitle).find();

        if (!strong) {
            for (String known : ARTISTS.keySet()) {
                if (padded.contains(" " + known + " ")) {
                    strong = true;
                    break;
                }
            }
        }

        if (strong) {
            return true;
        }

        if (durationMs <= 0) {
            return false;
        }

        if (leftArtistRaw(rawTitle) != null && durationMs <= 9L * 60L * 1000L) {
            return true;
        }

        return sGenreKey != null && matchesGenre(normTitle + " " + normArtist(author));
    }

    private static boolean matchesGenre(String text) {
        if (sGenreKey == null) {
            return false;
        }

        String padded = " " + text + " ";

        Genre genre = genreByKey(sGenreKey);
        if (genre != null && textHasGenre(padded, genre)) {
            return true;
        }

        for (Map.Entry<String, String> e : ARTISTS.entrySet()) {
            if (sGenreKey.equals(e.getValue()) && padded.contains(" " + e.getKey() + " ")) {
                return true;
            }
        }

        return false;
    }

    private static boolean textHasGenre(String padded, Genre genre) {
        for (String w : genre.words) {
            if (padded.contains(" " + w + " ")) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------
    // Detección del género de la canción inicial
    // ------------------------------------------------------------------

    private static void detectSeed(String title, String author, String description,
                                   List<MediaItem> candidates) {
        sSeedArtists.clear();
        sSeedArtists.addAll(artistKeys(title, author));

        String tPad = " " + norm(title) + " ";
        String aPad = " " + normArtist(author) + " ";
        String desc = norm(description);
        if (desc.length() > 1000) {
            desc = desc.substring(0, 1000);
        }
        String dPad = " " + desc + " ";

        // Evidencia fuerte: título, canal y artistas conocidos.
        // Evidencia débil: solo la descripción (suele traer relleno con varios géneros).
        Map<String, Integer> strong = new HashMap<>();
        Map<String, Integer> weak = new HashMap<>();

        for (Genre g : GENRES) {
            int s = 0;
            int w = 0;
            for (String word : g.words) {
                String key = " " + word + " ";
                if (tPad.contains(key)) {
                    s += 3;
                }
                if (aPad.contains(key)) {
                    s += 2;
                }
                if (dPad.contains(key)) {
                    w += 1;
                }
            }
            if (s > 0) {
                strong.put(g.key, s);
            }
            if (w > 0) {
                weak.put(g.key, w);
            }
        }

        for (Map.Entry<String, String> e : ARTISTS.entrySet()) {
            String key = " " + e.getKey() + " ";
            if (tPad.contains(key) || aPad.contains(key)) {
                Integer prev = strong.get(e.getValue());
                strong.put(e.getValue(), (prev == null ? 0 : prev) + 4);
            }
        }

        String source = "titulo/artista";
        String best = bestGenre(strong, 3);

        if (best == null) {
            best = inferGenreFromCandidates(candidates);
            source = "relacionados";
        }

        if (best == null) {
            best = bestGenre(weak, 1);
            source = "descripcion";
        }

        sGenreKey = best;

        Log.d(TAG, "Semilla: titulo='" + title + "' autor='" + author + "' genero=" + best
                + " (por " + source + ") artistasExcluidos=" + sSeedArtists);
    }

    /** Género con más puntos (en caso de empate gana el más específico, por orden). */
    private static String bestGenre(Map<String, Integer> scores, int minScore) {
        String best = null;
        int bestScore = 0;

        for (Genre g : GENRES) {
            Integer s = scores.get(g.key);
            if (s != null && s >= minScore && s > bestScore) {
                best = g.key;
                bestScore = s;
            }
        }

        return best;
    }

    /**
     * Cuando el título no dice el género, se deduce de lo que YouTube relaciona:
     * si la mayoría de los relacionados son del mismo género, ese es.
     */
    private static String inferGenreFromCandidates(List<MediaItem> items) {
        if (items == null || items.isEmpty()) {
            return null;
        }

        Map<String, Integer> votes = new HashMap<>();

        for (MediaItem item : items) {
            if (item == null) {
                continue;
            }

            String text = " " + norm(item.getTitle()) + " " + normArtist(item.getAuthor()) + " ";
            Set<String> seen = new HashSet<>();

            for (Genre g : GENRES) {
                if (textHasGenre(text, g)) {
                    seen.add(g.key);
                }
            }

            for (Map.Entry<String, String> e : ARTISTS.entrySet()) {
                if (text.contains(" " + e.getKey() + " ")) {
                    seen.add(e.getValue());
                }
            }

            for (String key : seen) {
                Integer prev = votes.get(key);
                votes.put(key, (prev == null ? 0 : prev) + 1);
            }
        }

        String best = null;
        int bestVotes = 0;
        int second = 0;

        for (Genre g : GENRES) {
            Integer v = votes.get(g.key);
            if (v == null) {
                continue;
            }
            if (v > bestVotes) {
                second = bestVotes;
                bestVotes = v;
                best = g.key;
            } else if (v > second) {
                second = v;
            }
        }

        return (best != null && bestVotes >= 3 && bestVotes > second) ? best : null;
    }

    // ------------------------------------------------------------------
    // Búsqueda por género (cuando los relacionados no alcanzan)
    // ------------------------------------------------------------------

    private static boolean startSearch(final Video current, final String forVideoId,
                                       final int startOffset, final int attempt) {
        Genre genre = genreByKey(sGenreKey);
        if (genre == null) {
            return false;
        }

        cancelSearch();

        final int token = sSearchToken;
        final String query = genre.query + " "
                + SEARCH_SUFFIXES[(startOffset + attempt) % SEARCH_SUFFIXES.length];

        sSearchingForId = forVideoId;

        try {
            Log.d(TAG, "Buscando siguiente por genero (" + (attempt + 1) + "/"
                    + MAX_SEARCH_ATTEMPTS + "): '" + query + "'");

            sSearchAction = YouTubeServiceManager.instance().getContentService()
                    .getSearchObserve(query, SearchOptions.TYPE_VIDEO)
                    .subscribe(
                            groups -> onSearchResult(current, forVideoId, token, startOffset,
                                    attempt, groups),
                            error -> {
                                Log.e(TAG, "Error buscando por genero: " + error.getMessage());
                                onSearchEmpty(current, forVideoId, token, startOffset, attempt);
                            }
                    );

            return true;
        } catch (Exception e) {
            Log.e(TAG, "No se pudo iniciar la busqueda por genero", e);
            sSearchingForId = null;
            return false;
        }
    }

    private static synchronized void onSearchResult(Video current, String forVideoId, int token,
                                                    int startOffset, int attempt,
                                                    Iterable<MediaGroup> groups) {
        if (token != sSearchToken || !forVideoId.equals(sCurrentId)) {
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
        MediaItem pick = select(items, false, true, false);
        if (pick == null) {
            pick = select(items, false, false, false);
        }

        if (pick != null) {
            publish(current, forVideoId, pick);
        } else {
            onSearchEmpty(current, forVideoId, token, startOffset, attempt);
        }
    }

    private static synchronized void onSearchEmpty(Video current, String forVideoId, int token,
                                                   int startOffset, int attempt) {
        if (token != sSearchToken || !forVideoId.equals(sCurrentId)) {
            return;
        }

        if (attempt + 1 < MAX_SEARCH_ATTEMPTS) {
            // Otra consulta distinta (otro sufijo) para tener otros resultados.
            if (startSearch(current, forVideoId, startOffset, attempt + 1)) {
                return;
            }
        }

        sSearchingForId = null;

        if (sDecisions.containsKey(forVideoId)) {
            Log.d(TAG, "Busqueda sin resultados nuevos: se mantiene la opcion provisional");
            return;
        }

        // Último recurso: algo limpio de los relacionados aunque traiga playlist
        // (nunca un mix "RD", ni compilaciones, ni el artista semilla).
        MediaItem fallback = select(sFallbackCandidates, false, false, true);
        if (fallback != null) {
            publish(current, forVideoId, fallback);
        } else {
            Log.e(TAG, "Sin candidatos validos para despues de " + forVideoId);
        }
    }

    /** Fija el siguiente video elegido por la búsqueda (en el hilo principal). */
    private static void publish(final Video current, String forVideoId, MediaItem pick) {
        final MediaItem clean = sanitize(pick);

        remember(forVideoId, clean);
        sSearchingForId = null;

        sUpcoming.clear();
        rebuildUpcoming(clean, sFallbackCandidates);

        Log.d(TAG, "Siguiente por genero: " + clean.getTitle() + " [" + clean.getAuthor() + "]");

        MAIN.post(() -> current.nextMediaItem = clean);
    }

    private static void cancelSearch() {
        if (sSearchAction != null && !sSearchAction.isDisposed()) {
            sSearchAction.dispose();
        }
        sSearchAction = null;
        sSearchingForId = null;
        sSearchToken++; // invalida cualquier callback pendiente
    }

    // ------------------------------------------------------------------
    // Utilidades
    // ------------------------------------------------------------------

    private static boolean isActive(long now) {
        return sSeedVideoId != null && (now - sLastActivityMs) < ACTIVE_WINDOW_MS;
    }

    private static List<MediaItem> collectCandidates(MediaItemMetadata metadata) {
        List<MediaItem> out = new ArrayList<>();

        // Se incluye el "siguiente" de YouTube solo como un candidato más:
        // si es un mix, los filtros lo descartan.
        MediaItem defaultNext = metadata.getNextVideo();
        if (defaultNext != null) {
            out.add(defaultNext);
        }

        List<MediaGroup> groups = metadata.getSuggestions();
        if (groups != null) {
            for (MediaGroup group : groups) {
                if (group == null || group.getMediaItems() == null) {
                    continue;
                }
                out.addAll(group.getMediaItems());
            }
        }

        return out;
    }

    /**
     * Devuelve el video "limpio": sin ninguna playlist/mix asociada, para que
     * SmartTube lo abra como un video suelto y la cadena siga bajo esta política.
     */
    private static MediaItem sanitize(MediaItem item) {
        try {
            Video video = Video.from(item);

            if (video == null) {
                return item;
            }

            boolean clean = video.playlistId == null && video.playlistIndex <= 0
                    && video.playlistParams == null && video.reloadPageKey == null;

            if (clean) {
                return item;
            }

            video.playlistId = null;
            video.playlistIndex = -1;
            video.playlistParams = null;
            video.reloadPageKey = null;

            return SimpleMediaItem.from(video);
        } catch (Exception e) {
            Log.e(TAG, "No se pudo limpiar el video elegido", e);
            return item;
        }
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
            markPlayed(pickId, pick.getTitle());
        }
    }

    private static void markPlayed(String videoId, String title) {
        if (videoId != null && !sPlayed.contains(videoId)) {
            sPlayed.add(videoId);
            while (sPlayed.size() > MAX_PLAYED) {
                sPlayed.removeFirst();
            }
        }

        String song = songKey(title);
        if (song != null && !sPlayedSongs.contains(song)) {
            sPlayedSongs.add(song);
            while (sPlayedSongs.size() > MAX_PLAYED) {
                sPlayedSongs.removeFirst();
            }
        }
    }

    private static void noteArtists(Set<String> keys) {
        for (String key : keys) {
            sRecentArtists.remove(key);
            sRecentArtists.add(key);
        }
        while (sRecentArtists.size() > MAX_RECENT_ARTISTS) {
            sRecentArtists.removeFirst();
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

    // ------------------------------------------------------------------
    // Artistas
    // ------------------------------------------------------------------

    /**
     * Nombres con los que se identifica al artista de un video:
     * el de "Artista - Canción" (y sus colaboradores), el del canal si es
     * el propio artista, y los artistas conocidos que aparezcan.
     */
    private static Set<String> artistKeys(String rawTitle, String author) {
        Set<String> keys = new HashSet<>();

        String channel = normArtist(author);
        String left = leftArtistRaw(rawTitle);
        String leftNorm = left != null ? normArtist(left) : "";

        if (!leftNorm.isEmpty()) {
            keys.add(leftNorm);

            for (String piece : splitCollab(left)) {
                String p = normArtist(piece);
                if (p.length() >= 4 && !ARTIST_STOPWORDS.contains(p)) {
                    keys.add(p);
                }
            }
        }

        // El canal cuenta como artista si no hay "Artista - ..." o si coincide con él.
        // (Si es un sello, p. ej. "Fania Records", y el título nombra a otro artista, no.)
        if (!channel.isEmpty()
                && (leftNorm.isEmpty() || channel.contains(leftNorm) || leftNorm.contains(channel))) {
            keys.add(channel);
        }

        String padded = " " + norm(rawTitle) + " " + channel + " ";
        for (String known : ARTISTS.keySet()) {
            if (padded.contains(" " + known + " ")) {
                keys.add(known);
            }
        }

        return keys;
    }

    /** "Artista - Canción" -> "Artista" (o null si el título no tiene ese formato). */
    private static String leftArtistRaw(String rawTitle) {
        if (rawTitle == null) {
            return null;
        }

        String t = rawTitle.replaceAll("\\(.*?\\)|\\[.*?\\]", " ").trim();
        String[] parts = t.split("\\s+[-\u2013\u2014]\\s+", 2);

        if (parts.length < 2) {
            return null;
        }

        String left = parts[0].trim();
        String n = norm(left);

        if (n.isEmpty() || n.split(" ").length > 5) {
            return null;
        }

        return left;
    }

    /** "Wisin & Yandel feat. X" -> ["Wisin", "Yandel", "X"] */
    private static String[] splitCollab(String left) {
        return left.split("(?i)\\s*(,|&|\\+|\\bfeat\\.?|\\bft\\.?|\\bfeaturing\\b|\\bcon\\b|\\by\\b"
                + "|\\band\\b|\\bx\\b|\\bvs\\.?)\\s*");
    }

    /** Identificador de la canción (sin artista ni relleno) para no repetirla. */
    private static String songKey(String rawTitle) {
        if (rawTitle == null) {
            return null;
        }

        String t = rawTitle.replaceAll("\\(.*?\\)|\\[.*?\\]", " ").trim();
        String[] parts = t.split("\\s+[-\u2013\u2014]\\s+", 2);
        String song = parts.length > 1 ? parts[1] : parts[0];

        String n = SONG_NOISE.matcher(norm(song)).replaceAll(" ").replaceAll("\\s+", " ").trim();

        return n.length() >= 3 ? n : null;
    }

    private static String firstNonEmpty(String a, String b) {
        return (a != null && !a.trim().isEmpty()) ? a : b;
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
