package com.voxeo.prism.sample.multiparty;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import javax.media.mscontrol.Configuration;
import javax.media.mscontrol.MediaSession;
import javax.media.mscontrol.MsControlException;
import javax.media.mscontrol.MsControlFactory;
import javax.media.mscontrol.Parameter;
import javax.media.mscontrol.Parameters;
import javax.media.mscontrol.mixer.MediaMixer;

import com.voxeo.mscontrol.VoxeoParameter;
import com.voxeo.mscontrol.mixer.AdvancedMediaMixer;

public class MixerManager {
  static Map<MsControlFactory, MixerManager> _instances = new HashMap<MsControlFactory, MixerManager>();

  ConcurrentMap<String, AdvancedMediaMixer> _mixers;

  MsControlFactory _factory;

  public synchronized static MixerManager getInstance(MsControlFactory factory) {
    MixerManager mgr = _instances.get(factory);
    if (mgr == null) {
      mgr = new MixerManager(factory);
      _instances.put(factory, mgr);
    }
    return mgr;
  }

  MixerManager(MsControlFactory factory) {
    _mixers = new ConcurrentHashMap<String, AdvancedMediaMixer>();
    _factory = factory;
  }

  public MediaMixer getMixer(String key) {
    return _mixers.get(key);
  }

  public AdvancedMediaMixer createMixer(Configuration<MediaMixer> config, Parameters options, String key)
      throws MsControlException {
    AdvancedMediaMixer oldMixer = _mixers.get(key);
    if (oldMixer != null) {
      return oldMixer;
    }
    MediaSession session = _factory.createMediaSession();
    final Parameters params = session.createParameters();
    if (options != Parameters.NO_PARAMETER) {
      params.putAll(options);
    }
    params.put(VoxeoParameter.VOXEO_OBJ_KEY, key);
    AdvancedMediaMixer newMixer = (AdvancedMediaMixer) session.createMediaMixer(config, params);
    oldMixer = _mixers.putIfAbsent(key, newMixer);
    if (oldMixer != null && newMixer != oldMixer) {
      newMixer.release();
      session.release();
    }
    else {
      oldMixer = newMixer;
    }
    return oldMixer;
  }

  public void removeMixer(AdvancedMediaMixer mixer) throws MsControlException {
    String key = (String) mixer.getParameters(new Parameter[] {VoxeoParameter.VOXEO_OBJ_KEY}).get(
        VoxeoParameter.VOXEO_OBJ_KEY);
    _mixers.remove(key);
    if (mixer.getJoinees().length == 0) {
      if (mixer.isFocus()) {
        mixer.destroy(true);
      }
      else {
        mixer.release();
      }
      mixer.getMediaSession().release();
    }
  }
}
