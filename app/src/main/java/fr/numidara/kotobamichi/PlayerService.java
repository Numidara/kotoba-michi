package fr.numidara.kotobamichi;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.drawable.Icon;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaMetadata;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Lecture de la séance en service de premier plan : continue écran éteint,
 * se pilote depuis la notification, le volant ou un casque Bluetooth,
 * et se met en pause pendant les annonces du GPS.
 */
public final class PlayerService extends Service {
  private static final String TAG = "KotobaPlayer";
  public static final String ACTION_START = "km.START", ACTION_TOGGLE = "km.TOGGLE", ACTION_NEXT = "km.NEXT",
      ACTION_PREV = "km.PREV", ACTION_STOP = "km.STOP";
  private static final String CHANNEL = "lecture";
  private static final int NOTIF_ID = 7;

  public interface Listener { void onPlayerChanged(); }
  public static final List<Listener> listeners = new CopyOnWriteArrayList<>();
  public static volatile PlayerService current;

  // Séance à démarrer, déposée par l'activité avant startForegroundService
  static volatile Session pending;
  static volatile long pendingSeed;
  static volatile int pendingIndex, pendingDur;

  public volatile Session session;
  public volatile int index;
  public volatile boolean playing, finished, waitingVoice;
  public volatile String error;
  public volatile long gapStart;    // horloge de début du silence en cours (pour l'anneau)
  public volatile int gapMs;
  private long seed; private int dur;

  private final Object lock = new Object();
  private volatile int gen;
  private volatile boolean alive = true;
  private final Map<Integer, short[]> buffers = new ConcurrentHashMap<>();
  private Thread playThread, prefetchThread;
  private AudioTrack track;
  private int trackRate;
  private MediaSession media;
  private AudioManager audio;
  private AudioFocusRequest focusReq;
  private boolean resumeOnGain;
  private PowerManager.WakeLock wake;
  private Settings settings;
  private Voice voice;
  private int lastSavedIndex = -1;

  public static void start(Context c, Session s, long seed, int index, int dur) {
    pending = s; pendingSeed = seed; pendingIndex = index; pendingDur = dur;
    Intent i = new Intent(c, PlayerService.class).setAction(ACTION_START);
    c.startForegroundService(i);
  }

  public static void send(Context c, String action) {
    if (current == null) return;
    c.startService(new Intent(c, PlayerService.class).setAction(action));
  }

  @Override public IBinder onBind(Intent intent) { return null; }

  @Override public void onCreate() {
    super.onCreate();
    current = this;
    settings = new Settings(this);
    voice = Voice.get(this);
    audio = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
    NotificationManager nm = getSystemService(NotificationManager.class);
    NotificationChannel ch = new NotificationChannel(CHANNEL, "Lecture des leçons", NotificationManager.IMPORTANCE_LOW);
    ch.setShowBadge(false);
    nm.createNotificationChannel(ch);
    wake = ((PowerManager) getSystemService(Context.POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "kotobamichi:lecture");
    wake.setReferenceCounted(false);

    media = new MediaSession(this, "KotobaMichi");
    media.setCallback(new MediaSession.Callback() {
      @Override public void onPlay() { resume(); }
      @Override public void onPause() { pause(false); }
      @Override public void onSkipToNext() { jump(1); }
      @Override public void onSkipToPrevious() { jump(-1); }
      @Override public void onStop() { pause(false); }
    });
    media.setActive(true);

    AudioAttributes attrs = new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build();
    focusReq = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).setAudioAttributes(attrs)
        .setWillPauseWhenDucked(true).setOnAudioFocusChangeListener(this::onFocus).build();

    playThread = new Thread(this::playLoop, "km-play");
    prefetchThread = new Thread(this::prefetchLoop, "km-voice");
    prefetchThread.setPriority(Thread.NORM_PRIORITY - 1);
    playThread.start();
    prefetchThread.start();
  }

  @Override public int onStartCommand(Intent intent, int flags, int startId) {
    String a = intent != null ? intent.getAction() : null;
    startForegroundCompat();
    if (ACTION_START.equals(a) && pending != null) {
      begin(pending, pendingSeed, pendingIndex, pendingDur);
      pending = null;
    } else if (ACTION_TOGGLE.equals(a)) { if (playing) pause(false); else resume(); }
    else if (ACTION_NEXT.equals(a)) jump(1);
    else if (ACTION_PREV.equals(a)) jump(-1);
    else if (ACTION_STOP.equals(a)) { stopAll(); }
    return START_NOT_STICKY;
  }

  private void startForegroundCompat() {
    Notification n = buildNotification();
    if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
    else startForeground(NOTIF_ID, n);
  }

  // ---------- commandes ----------
  private void begin(Session s, long seed, int idx, int dur) {
    synchronized (lock) {
      gen++;
      session = s; this.seed = seed; this.dur = dur;
      index = Math.max(0, Math.min(idx, s.steps.size() - 1));
      finished = false; error = null;
      buffers.clear();
      stopTrackAudio();
      lock.notifyAll();
    }
    MediaMetadata md = new MediaMetadata.Builder()
        .putString(MediaMetadata.METADATA_KEY_TITLE, s.lesson.title)
        .putString(MediaMetadata.METADATA_KEY_ARTIST, "Kotoba Michi · niveau " + s.lesson.level)
        .putLong(MediaMetadata.METADATA_KEY_DURATION, (long) (s.est * 1000)).build();
    media.setMetadata(md);
    resume();
  }

  public void resume() {
    if (session == null) return;
    if (finished) { begin(session, seed, 0, dur); return; }
    int r = audio.requestAudioFocus(focusReq);
    if (r == AudioManager.AUDIOFOCUS_REQUEST_FAILED) return;
    synchronized (lock) {
      playing = true;
      wake.acquire(3 * 60 * 60 * 1000L);
      lock.notifyAll();
    }
    changed();
  }

  public void pause(boolean transientLoss) {
    synchronized (lock) {
      if (!playing) return;
      playing = false;
      gen++;
      stopTrackAudio();
      resumeOnGain = transientLoss;
      lock.notifyAll();
    }
    if (!transientLoss) audio.abandonAudioFocusRequest(focusReq);
    if (wake.isHeld()) wake.release();
    saveResume(true);
    changed();
  }

  public void toggle() { if (playing) pause(false); else resume(); }

  /** dir -1 : rejoue l'exercice en cours (ou le précédent si on vient de le commencer). dir 1 : suivant. */
  public void jump(int dir) {
    Session s = session;
    if (s == null) return;
    synchronized (lock) {
      int cur = s.steps.get(Math.min(index, s.steps.size() - 1)).group;
      int target;
      if (dir < 0) target = (index - s.groups.get(cur).start > 2 || cur == 0) ? cur : cur - 1;
      else target = Math.min(s.groups.size() - 1, cur + 1);
      gen++;
      stopTrackAudio();
      index = s.groups.get(target).start;
      finished = false;
      lock.notifyAll();
    }
    if (!playing) resume(); else changed();
  }

  private void stopAll() {
    pause(false);
    saveResume(true);
    stopForeground(STOP_FOREGROUND_REMOVE);
    stopSelf();
  }

  private void onFocus(int change) {
    if (change == AudioManager.AUDIOFOCUS_GAIN) { if (resumeOnGain && !playing) { resumeOnGain = false; resume(); } }
    else if (change == AudioManager.AUDIOFOCUS_LOSS) pause(false);
    else if (change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) {
      if (playing) pause(true);
    }
  }

  // ---------- audio ----------
  private void ensureTrack(int rate) {
    if (track != null && trackRate == rate) return;
    if (track != null) { try { track.release(); } catch (Exception ignored) {} }
    int min = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
    track = new AudioTrack.Builder()
        .setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
        .setAudioFormat(new AudioFormat.Builder().setSampleRate(rate).setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
        .setBufferSizeInBytes(Math.max(min * 2, rate / 5 * 2))
        .setTransferMode(AudioTrack.MODE_STREAM).build();
    trackRate = rate;
  }

  private void stopTrackAudio() {
    AudioTrack t = track;
    if (t != null) { try { t.pause(); t.flush(); } catch (Exception ignored) {} }
  }

  /** Écrit des échantillons par blocs ; s'arrête si une commande a changé la génération. */
  private boolean write(short[] pcm, int myGen) {
    AudioTrack t = track;
    if (t.getPlayState() != AudioTrack.PLAYSTATE_PLAYING) t.play();
    int chunk = trackRate / 20;
    for (int off = 0; off < pcm.length; off += chunk) {
      if (myGen != gen || !alive) return false;
      int n = Math.min(chunk, pcm.length - off);
      int w = t.write(pcm, off, n, AudioTrack.WRITE_BLOCKING);
      if (w < 0) return false;
    }
    return myGen == gen;
  }

  private void playLoop() {
    while (alive) {
      int myGen, i;
      Session s;
      synchronized (lock) {
        while (alive && (!playing || session == null || finished)) {
          try { lock.wait(); } catch (InterruptedException e) { return; }
        }
        if (!alive) return;
        myGen = gen; i = index; s = session;
      }
      if (i >= s.steps.size()) { finish(s, myGen); continue; }
      Session.Step st = s.steps.get(i);
      boolean ok;
      try {
        if (!voice.awaitReady(1)) {
          waitingVoice = true; changed();
          if (!voice.awaitReady(120000)) { error = voice.error() != null ? voice.error() : "Moteur vocal indisponible"; pause(false); continue; }
        }
        ensureTrack(voice.sampleRate());
        if (st.type == Session.SAY) {
          short[] pcm = waitBuffer(i, myGen);
          if (pcm == null) continue;
          if (waitingVoice) { waitingVoice = false; }
          changed();
          ok = write(pcm, myGen);
          if (ok) ok = write(new short[trackRate / 8], myGen);
        } else {
          gapStart = SystemClock.elapsedRealtime(); gapMs = st.gapMs;
          changed();
          ok = write(new short[(int) ((long) trackRate * st.gapMs / 1000)], myGen);
          gapMs = 0;
        }
      } catch (Exception e) {
        Log.e(TAG, "lecture", e);
        error = e.getMessage(); ok = true;
      }
      if (ok && myGen == gen) {
        synchronized (lock) { if (myGen == gen) index = i + 1; }
        saveResume(false);
        if (index >= s.steps.size()) finish(s, myGen);
      }
    }
  }

  private short[] waitBuffer(int i, int myGen) {
    short[] pcm = buffers.get(i);
    if (pcm != null) return pcm;
    waitingVoice = true; changed();
    synchronized (lock) {
      while (alive && myGen == gen && (pcm = buffers.get(i)) == null) {
        try { lock.wait(250); } catch (InterruptedException e) { return null; }
      }
    }
    waitingVoice = false;
    return myGen == gen ? pcm : null;
  }

  private void finish(Session s, int myGen) {
    synchronized (lock) {
      if (myGen != gen || finished) return;
      finished = true; playing = false;
    }
    try { track.stop(); } catch (Exception ignored) {}
    Store st = Store.get(this);
    st.markDone(s);
    st.clearResume();
    audio.abandonAudioFocusRequest(focusReq);
    if (wake.isHeld()) wake.release();
    voice.pruneCache();
    changed();
  }

  /** Synthétise d'avance la suite de la séance (jusqu'à 40 énoncés devant la tête de lecture). */
  private void prefetchLoop() {
    while (alive) {
      Session s = session;
      int from = index;
      int target = -1, says = 0;
      if (s != null && !finished) {
        for (int k = from; k < s.steps.size() && says < 40; k++) {
          Session.Step st = s.steps.get(k);
          if (st.type != Session.SAY) continue;
          says++;
          if (!buffers.containsKey(k)) { target = k; break; }
        }
        for (Integer k : buffers.keySet()) if (k < from - 4 || k > from + 400) buffers.remove(k);
      }
      if (target < 0) { SystemClock.sleep(200); continue; }
      Session.Step st = s.steps.get(target);
      short[] pcm;
      try {
        float speed = st.lang.equals("fr") ? settings.frSpeed : settings.jaSpeed;
        pcm = voice.speak(st.speak, st.lang, settings.sidFor(st.role), speed, settings.quality);
      } catch (Exception e) {
        Log.e(TAG, "synthèse : " + st.speak, e);
        error = e.getMessage();
        pcm = new short[0];
      }
      if (s == session) {
        buffers.put(target, pcm);
        synchronized (lock) { lock.notifyAll(); }
      }
    }
  }

  private void saveResume(boolean force) {
    Session s = session;
    if (s == null || finished) return;
    if (!force && Math.abs(index - lastSavedIndex) < 8) return;
    lastSavedIndex = index;
    Store.get(this).saveResume(s.lesson.id, seed, index, dur, s.review);
  }

  // ---------- notification et état ----------
  private void changed() {
    Session s = session;
    if (s != null) {
      long pos = (long) (s.elapsedAt(index) * 1000);
      int state = finished ? PlaybackState.STATE_STOPPED : playing ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_PAUSED;
      media.setPlaybackState(new PlaybackState.Builder()
          .setActions(PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE | PlaybackState.ACTION_PLAY_PAUSE
              | PlaybackState.ACTION_SKIP_TO_NEXT | PlaybackState.ACTION_SKIP_TO_PREVIOUS | PlaybackState.ACTION_STOP)
          .setState(state, pos, playing ? 1f : 0f).build());
      getSystemService(NotificationManager.class).notify(NOTIF_ID, buildNotification());
    }
    for (Listener l : listeners) l.onPlayerChanged();
  }

  private PendingIntent action(String a, int code) {
    return PendingIntent.getService(this, code, new Intent(this, PlayerService.class).setAction(a),
        PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
  }

  private Notification buildNotification() {
    Session s = session;
    PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, PlayerActivity.class)
        .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    String text = s == null ? "Préparation…" : finished ? "Leçon terminée"
        : s.groups.get(s.steps.get(Math.min(index, s.steps.size() - 1)).group).label;
    Notification.Builder b = new Notification.Builder(this, CHANNEL)
        .setSmallIcon(R.drawable.ic_stat)
        .setContentTitle(s == null ? "Kotoba Michi" : s.lesson.title)
        .setContentText(text)
        .setContentIntent(open)
        .setOngoing(playing)
        .setVisibility(Notification.VISIBILITY_PUBLIC)
        .addAction(new Notification.Action.Builder(Icon.createWithResource(this, android.R.drawable.ic_media_previous), "Rejouer", action(ACTION_PREV, 1)).build())
        .addAction(new Notification.Action.Builder(Icon.createWithResource(this, playing ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play),
            playing ? "Pause" : "Lecture", action(ACTION_TOGGLE, 2)).build())
        .addAction(new Notification.Action.Builder(Icon.createWithResource(this, android.R.drawable.ic_media_next), "Suivant", action(ACTION_NEXT, 3)).build())
        .setStyle(new Notification.MediaStyle().setMediaSession(media.getSessionToken()).setShowActionsInCompactView(0, 1, 2));
    return b.build();
  }

  @Override public void onDestroy() {
    alive = false;
    synchronized (lock) { gen++; lock.notifyAll(); }
    try { if (track != null) track.release(); } catch (Exception ignored) {}
    media.release();
    if (wake.isHeld()) wake.release();
    current = null;
    for (Listener l : listeners) l.onPlayerChanged();
    super.onDestroy();
  }
}
