/*
 * Author: jianqing
 * Date: Feb 27, 2021
 * Description: This document is created for forwarding somehting to my  bucket.
 */
package com.classchat.testawslambda;

import cn.hutool.core.exceptions.ExceptionUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.setting.Setting;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Forwarding files to oss.
 * @author Jianqing Gao
 */
public class Forwarder implements RequestHandler<JSONObject, String>
{

    @Override
    public String handleRequest(JSONObject i, Context cntxt)
    {
        
        File tempFile;
        try
        {
            //create a buffer
            tempFile = File.createTempFile("LambdaJSON-FWD", ".json");
            tempFile.deleteOnExit();
            try (BufferedWriter writer = FileUtil.getWriter(tempFile, "UTF-8", false))
            {
                i.write(writer,1,1);
            }
            //load secret reader
            Setting set = new Setting("oss.setting");
            AliOSS.uploadFileAliyun(tempFile, set.get("location"));
            tempFile.delete();
        } catch (IOException ex)
        {
            Logger.getLogger(MainHandler.class.getName()).log(Level.SEVERE, null, ex);
            LambdaLogger logger = cntxt.getLogger();
            logger.log(ExceptionUtil.stacktraceToString(ex));
        }

        return "";
    }
    public static void main(String[] args)
    {
        
    }
}
