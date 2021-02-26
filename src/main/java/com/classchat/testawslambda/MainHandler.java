/*
 * Author: jianqing
 * Date: Feb 25, 2021
 * Description: This document is created for Lambda Handler on Amazon Web Service
 */
package com.classchat.testawslambda;

import cn.hutool.core.codec.Base32;
import cn.hutool.core.codec.Base62;
import cn.hutool.core.codec.Base64;
import cn.hutool.core.exceptions.ExceptionUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.IoUtil;

import cn.hutool.json.JSONArray;
//import cn.hutool.db.Session;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import cn.hutool.setting.Setting;
//import cn.hutool.json.JSONUtil;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
//import java.util.Base64;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.mail.BodyPart;
import javax.mail.MessagingException;
import javax.mail.Part;
import javax.mail.Session;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;

/**
 * This is the mail handler
 * @author Jianqing Gao
 * vip@jianqinggao.com
 */
public class MainHandler implements RequestHandler<JSONObject, String>
{

    /**
     * {@inheritDoc }
     * Accepts request from Amazon and handles it. The request is deserilized into JSONObject.
     * @param input The event which triggered this function
     * @param context The running env.
     * @return Nothing.
     */
    @Override
    public String handleRequest(JSONObject input, Context context)
    {
        //The JSON to write out.
        JSONObject outputJSON = JSONUtil.createObj();
        try
        {
            //Records is a json array that ususally only has 1 member
            //go to the head of the JSON array and see the event source, to see weather it comes from SNS or SES.

            JSONObject recordJSON = input.getJSONArray("Records").getJSONObject(0);
            String inputType = recordJSON.getStr("EventSource");

            ///see which resource trigger this function
            switch (inputType)
            {
                case "aws:sns":
                    {
                        //SES through SNS, message is embedded inside
                        JSONObject snsObject = recordJSON.getJSONObject("Sns");
                        String messageString = snsObject.getStr("Message");
                        JSONObject messageObject = JSONUtil.parseObj(messageString);//parse the message string into a readable JSONObject
                        JSONObject mailObject = messageObject.getJSONObject("mail");//get the mail object from root
                        JSONObject commonHeaders = mailObject.getJSONObject("commonHeaders");//All headers in this email in key-value format of JSONObjects
                        outputJSON.putOpt("headers", commonHeaders);
                        String mineMessage = messageObject.getStr("content");
                        //parse the MIME message, and get the raw content.
                        Properties prop = System.getProperties();
                        Session session = Session.getInstance(prop, null);
                        MimeMessage msg = new MimeMessage(session, new ByteArrayInputStream(mineMessage.getBytes()));
                        //call the recursion function, final object in string
                        outputJSON.putOpt("content", getPartsInObj(msg));
                        outputJSON.putOpt("code", 200);
                        outputJSON.putOpt("msg", "Success");
                        break;
                    }
                case "aws:ses":
                    {
                        //SES direct message, doesn't contain the header.
                        outputJSON.putOpt("code", 204);
                        outputJSON.putOpt("msg", "There is no content because this lambda is directly invoked through SES");
                        JSONObject sesObject = recordJSON.getJSONObject("ses");
                        JSONObject mailObject = sesObject.getJSONObject("mail");
                        JSONObject commonHeaders = mailObject.getJSONObject("commonHeaders");
                        outputJSON.putOpt("headers", commonHeaders);
                        break;
                    }
                default:
                    //Function trigger by unknown source.
                    outputJSON.putOpt("code", 403);
                    outputJSON.putOpt("msg", "Unknown event type. " + inputType);
                    break;
            
            }
        } catch (IOException | MessagingException ex)
        {
            outputJSON.putOpt("code", 500);
            outputJSON.putOpt("msg", ex.getMessage());
            outputJSON.putOpt("trace", ExceptionUtil.stacktraceToString(ex));
            LambdaLogger logger = context.getLogger();
            logger.log(ExceptionUtil.stacktraceToString(ex));
            Logger.getLogger(MainHandler.class.getName()).log(Level.SEVERE, null, ex);
            //return "It didn't work.";
        }
        
        //write output in file, then send the file over the network.
        File tempFile;
        try
        {
            tempFile = File.createTempFile("LambdaJSON", ".json");
            tempFile.deleteOnExit();
            //write the final result into the file
            try (BufferedWriter writer = FileUtil.getWriter(tempFile, "UTF-8", false))
            {
                outputJSON.write(writer,1,1);
            }
            //load cridentials
            Setting set = new Setting("oss.setting");
            //upload the file to OSS for this experiment.
            AliOSS.uploadFileAliyun(tempFile, set.get("location"));
            tempFile.delete();//delete the temp file created
        } catch (IOException ex)
        {
            //exception handling.
            Logger.getLogger(MainHandler.class.getName()).log(Level.SEVERE, null, ex);
            LambdaLogger logger = context.getLogger();
            logger.log(ExceptionUtil.stacktraceToString(ex));
        }

        //return the final parsed email.
        return outputJSON.toStringPretty();
    }

    /**
     * This method dissects the multipart into A json array. JSON like [{"type":"text/plain","body":"Hello World!"},{...}]
     * @param p
     * @return
     * @throws MessagingException 
     */
    private static JSONObject getPartsInObj(Part mp) throws MessagingException, IOException
    {
        
        String contentType = mp.getContentType();
        JSONObject record = JSONUtil.createObj();
        //JSONArray records = JSONUtil.createArray();
        record.putOpt("type", contentType);
        if(contentType.contains("text"))
        {
            //method for reading a plain text
            record.putOpt("body", extractText(mp));
        }else if(contentType.contains("multipart"))
        {
            //multipart recurrsion
            record.putOpt("body",dealMultiPart((MimeMultipart)mp.getContent()));
        }else
        {
            //transferr file to Base64
            record.putOpt("body", Base64.encode(mp.getInputStream()));
        }
        //the JSONOBject for this level part
        return record;
    }
    
    private static JSONArray dealMultiPart(MimeMultipart mp) throws MessagingException, IOException
    {
        //go through all the parts in this multipart, if necessary start a loop inside (recurrsion)
        JSONArray a = JSONUtil.createArray();
        for (int i = 0; i < mp.getCount(); i++)
        {
            BodyPart p = mp.getBodyPart(i);
            JSONObject obj = getPartsInObj(p);
            a.add(obj);
        }
        return a;
    }
    
    
    /**
     * Automatically look for required encoding. Decode any BASE encoded message if needed.
     * @param part
     * @return
     * @throws IOException
     * @throws MessagingException 
     */
    private static String extractText(Part part) throws IOException, MessagingException
    {
        String contentType = part.getContentType();
        String[] contTypeParts = contentType.split(";");
        String charset = "UTF-8";
        String spEncoding = null;//something like BASE64
        for (String seg : contTypeParts)
        {
            if (seg.contains("charset"))
            {
                charset = seg.split("=")[1].trim().replace("\"", "").replace("\'", "");
            } else if (seg.contains("Content-Transfer-Encoding"))
            {
                spEncoding = seg.split(":")[1].trim();
            }
        }
        if(part.getContentType().contains("text"))
        {
            return spEncoding == null ? IoUtil.read(part.getInputStream(), charset) : codecDecode(spEncoding, IoUtil.read(part.getInputStream(), charset), charset);
        }else
        {
            return "Files are not supported!";
        }
    }

    private static String codecDecode(String encoding, String content, String charset)
    {
        if (encoding.equalsIgnoreCase("base64"))
        {
            //return Base64.getDecoder()
            return Base64.decodeStr(content, Charset.forName(charset));
        } else if (encoding.equalsIgnoreCase("base62"))
        {
            return Base62.decodeStr(content, Charset.forName(charset));
        } else if (encoding.equalsIgnoreCase("base32"))
        {
            return Base32.decodeStr(content, Charset.forName(charset));
        } else
        {
            return content;
        }
    }
}
