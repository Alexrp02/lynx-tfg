package com.lynx.explorer.video;

import android.content.Context;
import android.media.MediaPlayer;
import android.net.Uri;
import com.lynx.react.bridge.Callback;
import com.lynx.react.bridge.ReadableMap;
import com.lynx.tasm.behavior.LynxContext;
import com.lynx.tasm.behavior.LynxProp;
import com.lynx.tasm.behavior.LynxUIMethod;
import com.lynx.tasm.behavior.LynxUIMethodConstants;
import com.lynx.tasm.behavior.ui.LynxUI;
import com.lynx.tasm.event.LynxCustomEvent;
import java.util.HashMap;
import java.util.Map;

public class LynxExplorerVideo extends LynxUI<CustomVideoView> {

  public LynxExplorerVideo(LynxContext context) {
    super(context);
  }

  @Override
  protected CustomVideoView createView(Context context) {
    CustomVideoView videoView = new CustomVideoView(context);

    videoView.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
      @Override
      public void onPrepared(MediaPlayer mp) {
        emitEvent("ready", new HashMap<String, Object>() {{
          put("duration", mp.getDuration());
          put("videoWidth", mp.getVideoWidth());
          put("videoHeight", mp.getVideoHeight());
        }});

        if (videoView.isMuted()) {
          mp.setVideoScalingMode(MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT);
          mp.setVolume(0f, 0f);
        }

        if (videoView.isLoop()) {
          mp.setLooping(true);
        }

        if (videoView.isAutoplay()) {
          videoView.start();
          emitEvent("play", null);
        }
      }
    });

    videoView.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
      @Override
      public void onCompletion(MediaPlayer mp) {
        emitEvent("ended", null);
      }
    });

    videoView.setOnErrorListener(new MediaPlayer.OnErrorListener() {
      @Override
      public boolean onError(MediaPlayer mp, int what, int extra) {
        emitEvent("error", new HashMap<String, Object>() {{
          put("what", what);
          put("extra", extra);
        }});
        return true;
      }
    });

    videoView.setOnInfoListener(new MediaPlayer.OnInfoListener() {
      @Override
      public boolean onInfo(MediaPlayer mp, int what, int extra) {
        if (what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) {
          emitEvent("loadstart", null);
        }
        return false;
      }
    });

    return videoView;
  }

  @LynxProp(name = "src")
  public void setSrc(String src) {
    if (src != null && !src.isEmpty() && !src.equals(mView.getCurrentSrc())) {
      mView.setCurrentSrc(src);
      Uri uri = Uri.parse(src);
      mView.setVideoURI(uri);
      emitEvent("loadstart", null);
    }
  }

  @LynxProp(name = "autoplay")
  public void setAutoplay(boolean autoplay) {
    mView.setAutoplay(autoplay);
  }

  @LynxProp(name = "loop")
  public void setLoop(boolean loop) {
    mView.setLoop(loop);
  }

  @LynxProp(name = "muted")
  public void setMuted(boolean muted) {
    mView.setMuted(muted);
  }

  @LynxProp(name = "controls")
  public void setControls(boolean controls) {
    if (controls) {
      mView.setMediaController(new android.widget.MediaController(getLynxContext()));
    } else {
      mView.setMediaController(null);
    }
  }

  @LynxUIMethod
  public void play(ReadableMap params, Callback callback) {
    try {
      if (!mView.isPlaying()) {
        mView.start();
        emitEvent("play", null);
      }
      callback.invoke(LynxUIMethodConstants.SUCCESS);
    } catch (Exception e) {
      callback.invoke(LynxUIMethodConstants.UNKNOWN, "Failed to play video: " + e.getMessage());
    }
  }

  @LynxUIMethod
  public void pause(ReadableMap params, Callback callback) {
    try {
      if (mView.isPlaying()) {
        mView.pause();
        emitEvent("pause", null);
      }
      callback.invoke(LynxUIMethodConstants.SUCCESS);
    } catch (Exception e) {
      callback.invoke(LynxUIMethodConstants.UNKNOWN, "Failed to pause video: " + e.getMessage());
    }
  }

  @LynxUIMethod
  public void stop(ReadableMap params, Callback callback) {
    try {
      mView.stopPlayback();
      emitEvent("stop", null);
      callback.invoke(LynxUIMethodConstants.SUCCESS);
    } catch (Exception e) {
      callback.invoke(LynxUIMethodConstants.UNKNOWN, "Failed to stop video: " + e.getMessage());
    }
  }

  @LynxUIMethod
  public void seekTo(ReadableMap params, Callback callback) {
    try {
      int position = params.getInt("position");
      mView.seekTo(position);
      emitEvent("seeked", new HashMap<String, Object>() {{
        put("currentTime", position);
      }});
      callback.invoke(LynxUIMethodConstants.SUCCESS);
    } catch (Exception e) {
      callback.invoke(LynxUIMethodConstants.UNKNOWN, "Failed to seek: " + e.getMessage());
    }
  }

  @LynxUIMethod
  public void getCurrentTime(ReadableMap params, Callback callback) {
    try {
      int currentPosition = mView.getCurrentPosition();
      Map<String, Object> result = new HashMap<>();
      result.put("currentTime", currentPosition);
      callback.invoke(LynxUIMethodConstants.SUCCESS, result);
    } catch (Exception e) {
      callback.invoke(LynxUIMethodConstants.UNKNOWN, "Failed to get current time: " + e.getMessage());
    }
  }

  private void emitEvent(String name, Map<String, Object> value) {
    LynxCustomEvent detail = new LynxCustomEvent(getSign(), name);
    if (value != null) {
      for (Map.Entry<String, Object> entry : value.entrySet()) {
        detail.addDetail(entry.getKey(), entry.getValue());
      }
    }
    getLynxContext().getEventEmitter().sendCustomEvent(detail);
  }
}
