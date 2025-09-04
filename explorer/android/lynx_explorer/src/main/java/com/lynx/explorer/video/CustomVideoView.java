package com.lynx.explorer.video;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.VideoView;

public class CustomVideoView extends VideoView {
  private boolean mAutoplay = false;
  private boolean mLoop = false;
  private boolean mMuted = false;
  private String mCurrentSrc = "";

  public CustomVideoView(Context context) {
    super(context);
  }

  public CustomVideoView(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  public void setAutoplay(boolean autoplay) {
    mAutoplay = autoplay;
  }

  public void setLoop(boolean loop) {
    mLoop = loop;
  }

  public void setMuted(boolean muted) {
    mMuted = muted;
  }

  public boolean isAutoplay() {
    return mAutoplay;
  }

  public boolean isLoop() {
    return mLoop;
  }

  public boolean isMuted() {
    return mMuted;
  }

  public String getCurrentSrc() {
    return mCurrentSrc;
  }

  public void setCurrentSrc(String src) {
    mCurrentSrc = src;
  }
}
