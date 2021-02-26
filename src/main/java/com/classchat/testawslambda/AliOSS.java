/*
 * Author: jianqing
 * Date: Dec 16, 2020
 * Description: This document is created for OSS Access
 */
package com.classchat.testawslambda;

import cn.hutool.core.lang.UUID;
import cn.hutool.crypto.digest.HMac;
import cn.hutool.crypto.digest.HmacAlgorithm;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpUtil;
import cn.hutool.http.Method;
import cn.hutool.setting.Setting;
import java.io.File;
import java.io.UnsupportedEncodingException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Base64;

/**
 *
 * @author jianqing gao
 */
public class AliOSS
{

    public static String getDateGMT()
    {
        return getDateGMT(-8);
    }

    public static String getDateGMT(int adjust)
    {
        return (LocalDateTime.now().plusHours(adjust).atOffset(ZoneOffset.UTC).format(DateTimeFormatter.RFC_1123_DATE_TIME));
    }

    public static String generateOSSSignature(String secret, String method, String resource, String date)
    {
        String content = method + "\n"//HTTP METHOD
                + "\n"//Content-MD5
                + "\n"//Content-Type
                + date + "\n"//Date in GMT format
                + resource;//resource to access
        return generateOSSSignature(secret, content);
    }

    public static String generateOSSSignature(String secret, String content)
    {
        HMac encrypter = new HMac(HmacAlgorithm.HmacSHA1, secret.getBytes());
        return java.util.Base64.getEncoder().encodeToString(encrypter.digest(content));
    }

    public static String generateOSSAuthHeader(String accessKeyId, String secret, String method, String resource, String date)
    {
        return "OSS " + accessKeyId + ":" + generateOSSSignature(secret, method, resource, date);
    }

    public static String generateOSSAuthHeader(String accessKeyId, String secret, String method, String resource)
    {
        return generateOSSAuthHeader(accessKeyId, secret, method, resource, getDateGMT());
    }

    /**
     * Try to send aliyun OSS a request.
     */
    private static void trySendReqOSS()
    {
        Setting setting = new Setting("oss.setting");
        final String ACCESSKEY_ID = setting.get("id");
        final String ACCESSKEY_SECRET = setting.getStr("secret");
        String authorization = generateOSSAuthHeader(ACCESSKEY_ID, ACCESSKEY_SECRET, "GET", "/xeduo/index.html");
        String date = getDateGMT();
        HttpRequest request = HttpUtil.createRequest(Method.GET, setting.get("location"));
        request.header("Authorization", authorization);
        request.header("Date", date);
        HttpResponse response = request.execute();
        //System.out.println(request);
        //System.out.println(response);
    }

    /**
     * Try to send aliyun OSS a reques
     *
     * @param file.
     * @param aliyunAddress
     * @return
     */
    public static HttpResponse uploadFileAliyun(File file, String aliyunAddress)
    {
        Setting setting = new Setting("oss.setting");
        final String ACCESSKEY_ID = setting.get("id");
        final String ACCESSKEY_SECRET = setting.getStr("secret");
        String date = getDateGMT();
        HttpRequest request = HttpUtil.createPost(aliyunAddress);
        String policy = "{\"expiration\": \"2120-01-01T12:00:00.000Z\","
                + "\"conditions\": [{\"bucket\":\""+setting.get("name")+"\"}]}";
        String encodePolicy = new String(Base64.getEncoder().encode(policy.getBytes()));
        request.header("Date", date);
        //add form
        request.form("key", setting.get("dir") + file.getName());
        request.form("OSSAccessKeyId", ACCESSKEY_ID);
        request.form("Policy", encodePolicy);//needs to be encoded as well.
        request.form("Signature", generateOSSSignature(ACCESSKEY_SECRET, encodePolicy));
        request.form("Content-Disposition", "attachment; filename=" + file.getName());
        request.form("file", file);
        request.form("x-oss-meta-uuid", UUID.fastUUID());
        request.form("submit", "Upload to OSS");
        HttpResponse response = request.execute();
        System.out.println("A file has been submitted to Aliyun OSS");
        return response;
    }

    public static void main(String[] args) throws UnsupportedEncodingException
    {
        Setting setting = new Setting("oss.setting");
        uploadFileAliyun(new File("/Users/jianqing/Documents/unnamed.jpg"), setting.get("location"));
    }

}
