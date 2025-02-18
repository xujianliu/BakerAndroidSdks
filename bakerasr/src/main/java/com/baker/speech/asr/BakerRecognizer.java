package com.baker.speech.asr;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Build;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;

import androidx.core.content.ContextCompat;

import com.baker.sdk.basecomponent.BakerBaseConstants;
import com.baker.sdk.basecomponent.util.GsonConverter;
import com.baker.sdk.basecomponent.util.Util;
import com.baker.sdk.http.BakerTokenManager;
import com.baker.sdk.http.CallbackListener;
import com.baker.speech.asr.base.BakerAsrConstants;
import com.baker.speech.asr.base.BakerRecognizerCallback;
import com.baker.speech.asr.bean.AsrParams;
import com.baker.speech.asr.bean.BakerException;
import com.baker.speech.asr.bean.BakerResponse;
import com.baker.speech.asr.event.EventManager;
import com.baker.speech.asr.event.EventManagerMessagePool;
import com.baker.speech.asr.event.EventManagerMultiMic;
import com.baker.speech.asr.event.EventManagerMultiNet;

/**
 * @author hsj55
 * 2021/2/2
 */
public class BakerRecognizer implements EventManager {
    private EventManagerMultiMic mic;
    private EventManagerMultiNet net;
    private BakerRecognizerCallback mCallBack;
    private Context mContext;
    private String url = BakerPrivateConstants.baseUrl;

    private AudioManager mAudioManager;
    private TelephonyManager mTelephonyManager;
    private AudioFocusRequest mFocusRequest;
    private BakerTokenManager tokenManager;

    private String domain = "common";
    private String audioFormat = "pcm";
    private int sampleRate = 16000;
    private boolean addPct = true;

    public void initSdk(Context context, BakerRecognizerCallback callBack) {
        mContext = context;
        mCallBack = callBack;

        try {
            if (!Util.checkConnectNetwork(mContext)) {
                if (mCallBack != null) {
                    mCallBack.onError(new BakerException(BakerAsrConstants.ERROR_TYPE_NET_UNUSABLE, "网络连接不可用"));
                    return;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        mAudioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
        mTelephonyManager = (TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE);
    }

    public void initSdk(Context context, String clientId, String secret, BakerRecognizerCallback callBack) {
        initSdk(context, clientId, secret, callBack, false);
    }

    public void initSdk(Context context, String clientId, String secret) {
        initSdk(context, clientId, secret, null, false);
    }

    public void initSdk(Context context, String clientId, String secret, BakerRecognizerCallback callBack, boolean isDebug) {
        mContext = context;
        mCallBack = callBack;

        if (TextUtils.isEmpty(clientId)) {
            if (mCallBack != null) {
                mCallBack.onError(new BakerException(BakerAsrConstants.ERROR_CODE_INIT_FAILED_CLIENT_ID_FAULT, "缺少ClientId"));
            }
            return;
        }
        if (TextUtils.isEmpty(secret)) {
            if (mCallBack != null) {
                mCallBack.onError(new BakerException(BakerAsrConstants.ERROR_CODE_INIT_FAILED_CLIENT_SECRET_FAULT, "缺少Secret"));
            }
            return;
        }

        try {
            if (!Util.checkConnectNetwork(mContext)) {
                if (mCallBack != null) {
                    mCallBack.onError(new BakerException(BakerAsrConstants.ERROR_TYPE_NET_UNUSABLE, "网络连接不可用"));
                    return;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        mAudioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
        mTelephonyManager = (TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE);

        //初始化基础库
        BakerBaseConstants.setIsDebug(isDebug);

        BakerPrivateConstants.clientId = clientId;
        BakerPrivateConstants.clientSecret = secret;

        //调用授权
        tokenManager = new BakerTokenManager();
        tokenManager.authentication(clientId, secret, new CallbackListener<String>() {
            @Override
            public void onSuccess(String response) {
                BakerPrivateConstants.token = response;
            }

            @Override
            public void onFailure(Exception e) {
                Log.e("BakerRecognizer", "baker asr sdk init token error, " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }

    public void setAudioFormat(String audioFormat) {
        this.audioFormat = audioFormat;
    }

    public void setSampleRate(int sampleRate) {
        this.sampleRate = sampleRate;
    }

    public void setAddPct(boolean addPct) {
        this.addPct = addPct;
    }

    public void setUrl(String url) {
        if (!TextUtils.isEmpty(url)) {
            this.url = url;
        }
    }

    public void setCallBack(BakerRecognizerCallback callBack) {
        this.mCallBack = callBack;
    }

    public void startAsr() {
        if (PackageManager.PERMISSION_GRANTED != ContextCompat.checkSelfPermission(mContext, Manifest.permission.RECORD_AUDIO)) {
            if (mCallBack != null) {
                mCallBack.onError(new BakerException(BakerAsrConstants.ERROR_TYPE_RECORD_PERMISSION, "recording permission is forbidden."));
            }
            return;
        }

        try {
            if (!Util.checkConnectNetwork(mContext)) {
                if (mCallBack != null) {
                    mCallBack.onError(new BakerException(BakerAsrConstants.ERROR_TYPE_NET_UNUSABLE, "网络连接不可用"));
                    return;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        manageAudioFocus();

        if (mic == null) {
            mic = new EventManagerMultiMic();
        }
        if (net == null) {
            net = new EventManagerMultiNet();
        }

        mic.setmOwner(BakerRecognizer.this);
        mic.setNetManager(net);
        net.setmOwner(BakerRecognizer.this);
        net.setUrl(url);

        EventManagerMessagePool.offer(mic, "mic.start");
        //1=sdk麦克风录音 2=接收字节流
        EventManagerMessagePool.offer(net, "net.start", GsonConverter.toJson(new AsrParams(addPct, domain, 1)));
    }

    public void stopAsr() {
        EventManagerMessagePool.offer(mic, "mic.stop");
        reset();
    }

    /**
     * start()、send()、end()搭配使用，用于接收字节流接口
     */
    public void start(){
        manageAudioFocus();
        if (net == null) {
            net = new EventManagerMultiNet();
        }
        net.setmOwner(BakerRecognizer.this);
        if (!TextUtils.isEmpty(url)) {
            net.setUrl(url);
        }
        //1=sdk麦克风录音 2=接收字节流
        EventManagerMessagePool.offer(net, "net.start", GsonConverter.toJson(new AsrParams(audioFormat, sampleRate, addPct, domain, 2)));
    }

    /**
     * start()、send()、end()搭配使用，用于接收字节流接口
     */
    public void send(byte[] data){
        if (data == null) {
            if (mCallBack != null) {
                mCallBack.onError(new BakerException(BakerAsrConstants.ERROR_CODE_DATA_IS_NULL, "发送的数据为空."));
            }
            return;
        }
        if (net == null) {
            if (mCallBack != null) {
                mCallBack.onError(new BakerException(BakerAsrConstants.ERROR_CODE_NOT_START, "网络未准备好，没有调用start()方法."));
            }
            return;
        }
        BakerPrivateConstants.dataQueue.offer(data);
        EventManagerMessagePool.offer(net, "net.upload");
    }

    /**
     * start()、send()、end()搭配使用，用于接收字节流接口
     */
    public void end(){
        if (net != null) {
            EventManagerMessagePool.offer(net, "net.disconnect");
        }
        reset();
    }

    @Override
    public void send(String name, byte[] data, String params) {
        switch (name) {
            case "net.start-called-1":
                if (mCallBack != null) {
                    mCallBack.onReadyOfSpeech();
                }
                //获取声音数据
                EventManagerMessagePool.offer(mic, "mic.record");
                break;
            case "net.start-called-2":
                if (mCallBack != null) {
                    mCallBack.onReadyOfSpeech();
                }
                BakerPrivateConstants.dataQueue.clear();
                break;
            case "asr.partial":
                if (!TextUtils.isEmpty(params) && mCallBack != null) {
                    BakerResponse bakerResponse = GsonConverter.fromJson(params, BakerResponse.class);
                    mCallBack.onResult(bakerResponse.getNbest(), bakerResponse.getUncertain(), bakerResponse.getEnd_flag() == 1, bakerResponse.getTraceId());
                }
                break;
            case "asr.finish":
                EventManagerMessagePool.offer(mic, "mic.error");
                if (!TextUtils.isEmpty(params) && mCallBack != null) {
                    BakerResponse bakerResponse = GsonConverter.fromJson(params, BakerResponse.class);
                    mCallBack.onResult(bakerResponse.getNbest(), bakerResponse.getUncertain(), bakerResponse.getEnd_flag() == 1, bakerResponse.getTraceId());
                }
                break;
            case "net.error":
            case "mic.error":
                stopAsr();
                if (mCallBack != null && !TextUtils.isEmpty(params)) {
                    BakerException error = GsonConverter.fromJson(params, BakerException.class);
                    mCallBack.onError(error);
                }
                break;
            case "mic.volume":
                if (mCallBack != null && !TextUtils.isEmpty(params)) {
                    mCallBack.onVolumeChanged(Integer.valueOf(params));
                }
                break;
            default:
                break;
        }
    }

    private void manageAudioFocus() {
        try {
            if (!Util.checkConnectNetwork(mContext)) {
                if (mCallBack != null) {
                    mCallBack.onError(new BakerException(BakerAsrConstants.ERROR_TYPE_NET_UNUSABLE, "网络连接不可用"));
                }
                return;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        reset();

        if (mAudioManager != null) {
            //获取音频焦点
            try {
                //8.0版本以后
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    AudioAttributes mPlaybackAttributes = new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build();
                    mFocusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                            .setAudioAttributes(mPlaybackAttributes)
                            .setAcceptsDelayedFocusGain(true)
                            .setOnAudioFocusChangeListener(audioFocusChangeListener)
                            .setWillPauseWhenDucked(true)
                            .build();
                    mAudioManager.requestAudioFocus(mFocusRequest);

                } else {
                    //流媒体设置焦点参数
                    mAudioManager.requestAudioFocus(audioFocusChangeListener,
                            // Use the music stream.(默认音乐流)
                            AudioManager.USE_DEFAULT_STREAM_TYPE,
                            // Request permanent focus.(要求永久焦点)
                            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        if (mTelephonyManager != null) {
            //手动注册对PhoneStateListener中的listen_call_state状态进行监听
            mTelephonyManager.listen(mPhoneStateListener, PhoneStateListener.LISTEN_CALL_STATE);
        }
    }

    private void reset() {
        if (mAudioManager != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && mFocusRequest != null) {
                mAudioManager.abandonAudioFocusRequest(mFocusRequest);
            } else {
                mAudioManager.abandonAudioFocus(audioFocusChangeListener);
            }
        }
        if (mTelephonyManager != null) {
            mTelephonyManager.listen(mPhoneStateListener, PhoneStateListener.LISTEN_NONE);
        }
    }

    private PhoneStateListener mPhoneStateListener = new PhoneStateListener() {
        @Override
        public void onCallStateChanged(int state, String phoneNumber) {
            switch (state) {
                case TelephonyManager.CALL_STATE_OFFHOOK:
                    //电话通话的状态
                    break;
                case TelephonyManager.CALL_STATE_RINGING:
                    //电话响铃的状态
                    stopAsr();
                    if (mCallBack != null) {
                        mCallBack.onError(new BakerException(BakerAsrConstants.ERROR_TYPE_RECORD_INTERRUPTION, "audioRecord is been interrupt"));
                    }
                    break;
                default:
                    break;
            }
            super.onCallStateChanged(state, phoneNumber);
        }
    };

    private AudioManager.OnAudioFocusChangeListener audioFocusChangeListener = new AudioManager.OnAudioFocusChangeListener() {
        @Override
        public void onAudioFocusChange(int i) {

        }
    };

    public void release() {
        if (mic != null) {
            mic.release();
            mic = null;
        }

        if (net != null) {
            net.release();
            net = null;
        }

        mContext = null;
        mCallBack = null;
        if (tokenManager != null) {
            tokenManager.release();
            tokenManager = null;
        }
    }
}

