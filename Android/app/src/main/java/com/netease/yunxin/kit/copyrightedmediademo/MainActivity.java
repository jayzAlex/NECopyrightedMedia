// Copyright (c) 2022 NetEase, Inc. All rights reserved.
// Use of this source code is governed by a MIT license that can be
// found in the LICENSE file.

package com.netease.yunxin.kit.copyrightedmediademo;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.app.ActivityCompat;
import com.netease.lava.nertc.sdk.NERtc;
import com.netease.lava.nertc.sdk.NERtcCallback;
import com.netease.lava.nertc.sdk.NERtcConstants;
import com.netease.lava.nertc.sdk.NERtcEx;
import com.netease.lava.nertc.sdk.NERtcParameters;
import com.netease.lava.nertc.sdk.audio.NERtcAudioStreamType;
import com.netease.lava.nertc.sdk.audio.NERtcCreateAudioEffectOption;
import com.netease.lava.nertc.sdk.video.NERtcRemoteVideoStreamType;
import com.netease.yunxin.kit.copyrightedmedia.api.NECopyrightedMedia;
import com.netease.yunxin.kit.copyrightedmedia.api.NEErrorCode;
import com.netease.yunxin.kit.copyrightedmedia.api.NESongPreloadCallback;
import com.netease.yunxin.kit.copyrightedmedia.api.SongResType;
import java.util.HashMap;
import java.util.List;
import java.util.Random;

public class MainActivity extends AppCompatActivity implements NERtcCallback {
  private static final String LOG_TAG = "SampleCode";

  private static final int REQUEST_CODE_PERMISSION = 10000;

  private boolean isAnchor;

  private Context context;

  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    context = this;
    setContentView(R.layout.activity_main);
    requestPermissionsIfNeeded();
    initViews();
    setupNERtc();
  }

  private void requestPermissionsIfNeeded() {
    final List<String> missedPermissions = NERtc.checkPermission(this);
    if (missedPermissions.size() > 0) {
      ActivityCompat.requestPermissions(
          this, missedPermissions.toArray(new String[0]), REQUEST_CODE_PERMISSION);
    }
  }

  private void initViews() {
    final EditText editTextUserId = findViewById(R.id.et_user_id);
    editTextUserId.setText(generateRandomUserID());
    final EditText editTextRoomId = findViewById(R.id.et_room_id);
    final SwitchCompat switchRole = findViewById(R.id.sh_role);
    findViewById(R.id.btn_join)
        .setOnClickListener(
            v -> {
              String userIdText =
                  editTextUserId.getText() != null ? editTextUserId.getText().toString() : "";
              if (TextUtils.isEmpty(userIdText)) {
                Toast.makeText(this, R.string.please_input_user_id, Toast.LENGTH_SHORT).show();
                return;
              }
              String roomId =
                  editTextRoomId.getText() != null ? editTextRoomId.getText().toString() : "";
              if (TextUtils.isEmpty(roomId)) {
                Toast.makeText(this, R.string.please_input_room_id, Toast.LENGTH_SHORT).show();
                return;
              }
              long userId;
              try {
                userId = Long.parseLong(userIdText);
              } catch (NumberFormatException e) {
                Toast.makeText(this, R.string.invalid_user_id, Toast.LENGTH_SHORT).show();
                return;
              }

              boolean isAnchor = switchRole.isChecked();
              joinRtcChannel(isAnchor, roomId, userId);
              hideSoftKeyboard();
            });
    findViewById(R.id.btn_start_play)
        .setOnClickListener(
            v -> {
              String user = ((EditText) findViewById(R.id.et_music_user)).getText().toString();
              String token = ((EditText) findViewById(R.id.et_music_token)).getText().toString();
              String songId = ((EditText) findViewById(R.id.et_song_id)).getText().toString();
              downloadSong(user, token, songId);
            });
  }

  private void downloadSong(String user, String token, String songId) {
    NECopyrightedMedia copyrightedMedia = NECopyrightedMedia.getInstance();
    HashMap<String, Object> extras = new HashMap<>();
    copyrightedMedia.initialize(this, AppConfig.getAppKey(), token, user, null);
    copyrightedMedia.preloadSong(
        songId,
        new NESongPreloadCallback() {
          @Override
          public void onPreloadStart(String songId) {
            Toast.makeText(
                    context, context.getString(R.string.start_load, songId), Toast.LENGTH_SHORT)
                .show();
          }

          @Override
          public void onPreloadProgress(String songId, float progress) {}

          @Override
          public void onPreloadComplete(String songId, int errorCode, String msg) {
            if (errorCode == NEErrorCode.OK) {
              Toast.makeText(
                      context,
                      context.getString(R.string.song_load_success, songId),
                      Toast.LENGTH_SHORT)
                  .show();
              String originPath = copyrightedMedia.getSongURI(songId, SongResType.TYPE_ORIGIN);
              String accompanyPath = copyrightedMedia.getSongURI(songId, SongResType.TYPE_ACCOMP);
              useNERtc(originPath, accompanyPath, 0);
            } else {
              Toast.makeText(
                      context,
                      context.getString(R.string.song_load_failed, errorCode, msg),
                      Toast.LENGTH_SHORT)
                  .show();
            }
          }
        });
  }

  /** 初始化NERtc */
  private void setupNERtc() {
    NERtcParameters parameters = new NERtcParameters();
    NERtcEx.getInstance().setParameters(parameters);
    try {
      NERtcEx.getInstance().init(getApplicationContext(), AppConfig.getAppKey(), this, null);
      NERtcEx.getInstance().enableLocalAudio(true);
      NERtcEx.getInstance().setClientRole(NERtcConstants.UserRole.CLIENT_ROLE_BROADCASTER);
    } catch (Exception e) {
      Toast.makeText(this, "nertc init failed", Toast.LENGTH_LONG).show();
      finish();
    }
  }

  /** 示例 使用NERtc 播放和发送声音 */
  private void useNERtc(String originPath, String accompanyPath, long startTimeStamp) {
    NERtcEx rtcController = NERtcEx.getInstance();
    long PROGRESS_INTERVAL = 100L; // 进度刷新频次
    int sendVolume = 50; // 伴音默认的发送音量
    int playbackVolume = 50; // 伴音默认本地播放的音量
    // 注意，上面的 songId 是曲库后台接口返回的字符串，用来区分存储在后台的音乐资源
    //      下面的 effectOriginId 和 effectAccompanyId 是 int 型格式，您可以自己设置，
    //      用于 nertc 的 BGM 播放接口区分不同的音乐使用，保证原唱和伴奏的 id 不同即可
    int effectOriginId = 1000; // 原唱音效 id
    int effectAccompanyId = 1001; // 伴音音效 id

    rtcController.setAudioProfile(
        NERtcConstants.AudioProfile.HIGH_QUALITY_STEREO, NERtcConstants.AudioScenario.MUSIC);

    //调用以下代码会播放并上行伴奏：
    rtcController.setEffectPlaybackVolume(effectAccompanyId, 100);
    rtcController.setEffectSendVolume(effectAccompanyId, sendVolume);

    //调用以下代码会播放并上行原唱：

    rtcController.setEffectPlaybackVolume(effectOriginId, 0);
    rtcController.setEffectSendVolume(effectOriginId, 0);

    // 播放伴奏
    NERtcCreateAudioEffectOption accompanyOption = new NERtcCreateAudioEffectOption();
    accompanyOption.path = accompanyPath;
    accompanyOption.loopCount = 1;
    accompanyOption.sendEnabled = true;
    accompanyOption.sendVolume = sendVolume;
    accompanyOption.playbackEnabled = true;
    accompanyOption.playbackVolume = playbackVolume;
    accompanyOption.startTimestamp = startTimeStamp;
    accompanyOption.progressInterval = PROGRESS_INTERVAL;
    accompanyOption.sendWithAudioType = NERtcAudioStreamType.kNERtcAudioStreamTypeMain;
    rtcController.playEffect(effectAccompanyId, accompanyOption);

    // 播放原唱
    NERtcCreateAudioEffectOption originOption = new NERtcCreateAudioEffectOption();
    originOption.path = originPath;
    originOption.loopCount = 1;
    originOption.sendEnabled = true;
    originOption.sendVolume = 0;
    originOption.playbackEnabled = true;
    originOption.playbackVolume = 0;
    originOption.startTimestamp = startTimeStamp;
    originOption.progressInterval = PROGRESS_INTERVAL;
    originOption.sendWithAudioType = NERtcAudioStreamType.kNERtcAudioStreamTypeMain;
    rtcController.playEffect(effectOriginId, originOption);
  }

  private void joinRtcChannel(boolean isAnchor, String roomId, long uid) {
    this.isAnchor = isAnchor;
    joinChannel(uid, roomId);
  }

  private void joinChannel(long userId, String roomId) {
    NERtcEx.getInstance().joinChannel("", roomId, userId);
  }

  private void hideSoftKeyboard() {
    if (getCurrentFocus() == null) return;
    InputMethodManager imm = (InputMethodManager) getSystemService(Activity.INPUT_METHOD_SERVICE);
    if (imm == null) return;
    imm.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), 0);
  }

  private String generateRandomUserID() {
    return String.valueOf(new Random().nextInt(100000));
  }

  @SuppressLint("UsingALog")
  @Override
  public void onJoinChannel(int code, long chenelId, long elapsed, long uid) {
    Log.i(LOG_TAG, "onJoinChannel:" + code);
    Toast.makeText(this, "onJoinChannel:" + code + " " + uid, Toast.LENGTH_SHORT).show();
  }

  @Override
  public void onLeaveChannel(int i) {
    NERtc.getInstance().release();
  }

  @Override
  public void onUserJoined(long uid) {}

  @Override
  public void onUserLeave(long l, int i) {}

  @Override
  public void onUserAudioStart(long l) {}

  @Override
  public void onUserAudioStop(long l) {}

  @Override
  public void onUserVideoStart(long uid, int i) {
    if (!isAnchor)
      NERtc.getInstance()
          .subscribeRemoteVideoStream(
              uid, NERtcRemoteVideoStreamType.kNERtcRemoteVideoStreamTypeHigh, true);
  }

  @Override
  public void onUserVideoStop(long l) {}

  @Override
  public void onDisconnect(int i) {}

  @Override
  public void onClientRoleChange(int i, int i1) {}

  @Override
  protected void onDestroy() {
    super.onDestroy();
    NERtcEx.getInstance().leaveChannel();
    NERtcEx.getInstance().release();
  }
}
