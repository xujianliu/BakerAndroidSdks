package com.baker.engrave.lib.net;

import com.baker.engrave.lib.BuildConfig;
import com.baker.engrave.lib.util.HLogger;
import com.google.gson.Gson;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Headers;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Create by hsj55
 * 2020/3/5
 */
public class BakerOkHttpClient {
    private static OkHttpClient mHttpClient;

    private BakerOkHttpClient() {
        if (mHttpClient == null) {

//            if (BuildConfig.DEBUG) {
//                HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor(new HttpLoggingInterceptor.Logger() {
//                    @Override
//                    public void log(@NotNull String s) {
//                        HLogger.v(s);
//                    }
//                });
//                loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BODY);
//                mHttpClient = new OkHttpClient().newBuilder()
//                        .retryOnConnectionFailure(true)
//                        .connectTimeout(120, TimeUnit.SECONDS)
//                        .callTimeout(120, TimeUnit.SECONDS)
//                        .readTimeout(120, TimeUnit.SECONDS)
//                        .addInterceptor(new TokenInterceptor())
//                        .addInterceptor(loggingInterceptor)
//                        .build();
//            } else {
                mHttpClient = new OkHttpClient().newBuilder()
                        .retryOnConnectionFailure(true)
                        .connectTimeout(120, TimeUnit.SECONDS)
                        .callTimeout(120, TimeUnit.SECONDS)
                        .readTimeout(120, TimeUnit.SECONDS)
                        .addInterceptor(new TokenInterceptor())
                        .build();
//            }
        }
    }

    private static final class HolderClass {
        private static final BakerOkHttpClient client = new BakerOkHttpClient();
    }

    public static BakerOkHttpClient getInstance() {
        return HolderClass.client;
    }


    public Request createGetRequest(String url) {
        //添加请求头
        return new Request.Builder().url(url)
                .get()
                .build();
    }

    //        添加请求体
    public Request createPostRequest(String url, ConcurrentHashMap<String, Object> params, ConcurrentHashMap<String, String> headers) {
        RequestBody requestBody;
        if (params != null) {
            requestBody = RequestBody.create(new Gson().toJson(params), MediaType.parse("application/json; charset=utf-8"));
        } else {
            requestBody = RequestBody.create("{}", MediaType.parse("application/json; charset=utf-8"));
        }
        //添加请求头
        Headers.Builder mHeaderBuild = new Headers.Builder();
        if (headers != null) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                mHeaderBuild.add(entry.getKey(), entry.getValue());
            }
        }
        return new Request.Builder().url(url).
                post(requestBody).
                headers(mHeaderBuild.build())
                .build();
    }

    public Request createMultiPostRequest(String url, ConcurrentHashMap<String, String> params, ConcurrentHashMap<String, String> headers, File file) {
        MultipartBody.Builder builder = new MultipartBody.Builder();
        builder.setType(MultipartBody.FORM);

        if (file != null) {
            RequestBody requestBody = RequestBody.create(file, MediaType.parse("audio/*"));
            builder.addFormDataPart("file", file.getName(), requestBody);
        }

        if (params != null) {
            for (Map.Entry<String, String> entry : params.entrySet()) {
                builder.addFormDataPart(entry.getKey(), entry.getValue());
            }
        }
        //添加请求头
        Headers.Builder mHeaderBuild = new Headers.Builder();
        if (headers != null) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                mHeaderBuild.add(entry.getKey(), entry.getValue());
            }
        }
        return new Request.Builder().url(url).
                post(builder.build()).
                headers(mHeaderBuild.build())
                .build();
    }


    public void enqueue(Request request, Callback callback) {
        Call call = mHttpClient.newCall(request);
        call.enqueue(callback);
    }

    public Response execute(Request request) throws IOException {
        Call call = mHttpClient.newCall(request);
        return call.execute();
    }
}
