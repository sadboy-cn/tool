package net.polyv.common.v1.base;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;

import lombok.extern.slf4j.Slf4j;
import net.polyv.common.v1.base.DataParse;
import net.polyv.common.v1.base.HttpClientUtil;
import net.polyv.common.v1.constant.Constant;
import net.polyv.common.v1.exception.PloyvSdkException;
import net.polyv.common.v1.util.MapUtil;

/**
 * 保利威SDK发送HTTP请求工具类
 * 默认会加入SDK请求头信息
 * @author: sadboy
 **/
@Slf4j
public class HttpUtil {
    public static final String SOURCE = "source";
    public static final String VERSION = "version";
    public static final String USER_AGENT = "User-Agent";
    public static final String APP_ID_NAME = "java-sdk-app-id";
    public static final String USER_ID_NAME = "java-sdk-user-id";
    public static final String CURRENT_VERSION = "1.1.12";
    private static final String UTF8 = Constant.UTF8;
    
    private HttpUtil() {
    }
    
    /**
     * 向url发送get请求
     * @param url 请求目标地址
     * @return 请求返回的数据
     * @throws IOException 读写异常
     */
    public static String get(String url) throws IOException {
        return get(url, null,new HashMap<>());
    }
    
    /**
     * 向url发送get请求
     * @param url 请求url
     * @param paramMap 需要拼接的参数
     * @return 请求返回的数据
     * @throws IOException 读写异常
     */
    public static String get(String url, Map<String, String> paramMap, Map<String, String> headMap) throws IOException {
        return get(url, paramMap,headMap, UTF8);
    }
    
    /**
     * 向url发送get请求
     * @param url 请求url
     * @param paramMap 需要拼接的参数
     * @param encoding 编码
     * @return 请求返回的数据
     * @throws IOException 读写异常
     */
    public static String get(String url, Map<String, String> paramMap,Map<String, String> headMap, String encoding) throws IOException {
        encoding = encoding == null ? UTF8 : encoding;
        url = MapUtil.appendUrl(url, paramMap);
        return get(url,headMap, encoding, new DataParse<String>() {
            @Override
            public String parseData(HttpEntity httpEntity, String encoding) throws IOException {
                return EntityUtils.toString(httpEntity, encoding);
            }
        });
    }
    
    /**
     * 向url发送get请求
     * @param url 请求url
     * @param paramMap 需要拼接的参数
     * @param encoding 编码
     * @return 请求返回的字节数组，一般用于文件下载
     * @throws IOException 读写异常
     */
    public static byte[] getBinary(String url, Map<String, String> paramMap,Map<String, String> headMap, String encoding) throws IOException {
        encoding = encoding == null ? UTF8 : encoding;
        url = MapUtil.appendUrl(url, paramMap);
        return get(url,headMap, encoding, new DataParse<byte[]>() {
            @Override
            public byte[] parseData(HttpEntity httpEntity, String encoding) throws IOException {
                return EntityUtils.toByteArray(httpEntity);
            }
        });
    }
    
    
    /**
     * HTTP GET 内部公共请求处理逻辑
     * @param url 请求地址
     * @param encoding 编码字符集， 默认为 utf-8
     * @param dataParse 返回数据反序列化逻辑实现类
     * @return HTTP 返回的内容
     * @throws IOException 客户端和服务器读写通讯异常
     */
    private static <T> T get(String url,Map<String, String> headMap, String encoding, DataParse<T> dataParse) throws IOException {
        log.debug("http 请求 url: {}", url);
        T result = null;
        PloyvSdkException ployvSdkException = null;
        // 创建httpclient对象
        CloseableHttpClient httpClient = HttpClientUtil.getHttpClient();
        // 创建get方式请求对象
        HttpGet httpGet = new HttpGet(url);
        httpGet.addHeader(Constant.CONTENT_TYPE, Constant.APPLICATION_JSON);
        if(headMap != null){
            Set<Map.Entry<String, String>> entries = headMap.entrySet();
            Iterator<Map.Entry<String, String>> iterator = entries.iterator();
            while (iterator.hasNext()){
                Map.Entry<String, String> next = iterator.next();
                httpGet.addHeader(next.getKey(),next.getValue());
            }
        }
        // 通过请求对象获取响应对象
        CloseableHttpResponse response = sendRequestAndGetResult(url, httpClient, httpGet);
        // 获取结果实体
        if (null != response) {
            result = dataParse.parseData(response.getEntity(), encoding);
            if (!(result instanceof byte[])) {
                log.debug("http 请求结果: {}", result);
            }else{
                //返回文件时，参数验证错误处理逻辑
                Header[] headers = response.getHeaders(Constant.CONTENT_TYPE);
                for (Header responseHead : headers) {
                    String headStr = responseHead.getValue();
                    if (headStr.startsWith("application/json")) {
                        String json = new String((byte[]) result, Constant.UTF8);
                        JSONObject jsonObject = JSON.parseObject(json);
                        String message = jsonObject.getString("message");
                        Integer code = jsonObject.getInteger("code");
                        ployvSdkException =  new PloyvSdkException(code, message);
                    }
                }
            }
        }
        try {
            if (null != response) {
                response.close();
            }
        } catch (IOException ex) {
            log.error(ex.getMessage(), ex);
        }
        if( ployvSdkException != null){
            throw  ployvSdkException ;
        }
        return result;
    }
    
    /**
     * 返回网址的状态码
     * @param url
     * @param <T>
     * @return
     * @throws IOException
     */
    public static <T> int getWebCode(String url) throws IOException {
        log.debug("http 请求 url: {}", url);
        T result = null;
        PloyvSdkException ployvSdkException = null;
        // 创建httpclient对象
        CloseableHttpClient httpClient = HttpClientUtil.getHttpClient();
        // 创建get方式请求对象
        HttpGet httpGet = new HttpGet(url);
        // 通过请求对象获取响应对象
        CloseableHttpResponse response = sendRequestAndGetResult(url, httpClient, httpGet);
        int statusCode = response.getStatusLine().getStatusCode();
        try {
            if (null != response) {
                response.close();
            }
        } catch (IOException ex) {
            log.error(ex.getMessage(), ex);
        }
        if( ployvSdkException != null){
            throw  ployvSdkException ;
        }
        return statusCode;
    }
    
    
    /**
     * 向url发送post请求
     * @param url 请求url
     * @param paramMap 需要拼接的参数
     * @return 请求返回的数据
     * @throws IOException 读写异常
     */
    public static String postFormBody(String url, Map<String, String> paramMap, Map<String, String> headMap) throws IOException {
        return postFormBody(url, paramMap,headMap, null);
    }
    
    /**
     * 向url发送post请求
     * @param url 请求url
     * @param paramMap 需要拼接的参数
     * @param encoding 编码
     * @return 请求返回的数据
     * @throws IOException 读写异常
     */
    public static String postFormBody(String url, Map<String, String> paramMap,Map<String, String> headMap, String encoding) throws IOException {
        return post(url, paramMap,headMap, encoding);
    }
    
    
    /**
     * 向url发送post请求表单提交数据
     * @param url 请求url
     * @param paramMap 表单数据
     * @param encoding 编码
     * @return 请求返回的数据
     * @throws IOException 读写异常
     */
    private static String post(String url, Map<String, String> paramMap,Map<String, String> headMap, String encoding) throws IOException {
        log.debug("http 请求 url: {} , 请求参数: {}", url, JSON.toJSONString(paramMap));
        encoding = encoding == null ? UTF8 : encoding;
        // 创建post方式请求对象
        HttpPost httpPost = new HttpPost(url);
        // 装填参数
        List<NameValuePair> nameValuePairs = new ArrayList<NameValuePair>();
        if (paramMap != null) {
            for (Map.Entry<String, String> entry : paramMap.entrySet()) {
                String value = entry.getValue();
                //去掉如下判断会造成String类型的value为null时
                if (value != null) {
                    nameValuePairs.add(new BasicNameValuePair(entry.getKey(), value));
                }
            }
        }
        // 设置参数到请求对象中
        httpPost.setEntity(new UrlEncodedFormEntity(nameValuePairs, encoding));
        // 设置header信息
        // 指定报文头【Content-type】、【User-Agent】
        httpPost.setHeader(Constant.CONTENT_TYPE, Constant.APPLICATION_FORM_URLENCODED);
        return post(url,headMap, httpPost, encoding, new DataParse<String>() {
            @Override
            public String parseData(HttpEntity httpEntity, String encoding) throws IOException {
                return EntityUtils.toString(httpEntity, encoding);
            }
        });
    }
    
    /**
     * 向url发送post请求发送json
     * @param url 请求url
     * @param json json字符串
     * @param encoding 编码
     * @return 请求返回的数据
     * @throws IOException 读写异常
     */
    public static String postJsonBody(String url,Map<String, String> headMap, String json, String encoding) throws IOException {
        log.debug("http 请求 url: {} , 请求参数: {}", url, json);
        encoding = encoding == null ? UTF8 : encoding;
        // 创建post方式请求对象
        HttpPost httpPost = new HttpPost(url);
        // 设置参数到请求对象中
        StringEntity stringEntity = new StringEntity(json, ContentType.APPLICATION_JSON);
        //  Constant.UTF8
        stringEntity.setContentEncoding(encoding);
        httpPost.setEntity(stringEntity);
        String result = post(url, headMap,httpPost, encoding, new DataParse<String>() {
            @Override
            public String parseData(HttpEntity httpEntity, String encoding) throws IOException {
                return EntityUtils.toString(httpEntity, encoding);
            }
        });
        return result;
    }
    
    /**
     * 向url发送post请求
     * @param url 请求url
     * @param httpPost httpClient
     * @return 请求返回的数据
     * @throws IOException 读写异常
     */
    private static <T> T post(String url,Map<String, String> headMap, HttpPost httpPost, String encoding, DataParse<T> dataParse)
            throws IOException {
        T result = null;
        CloseableHttpResponse response = null;
        // 创建httpclient对象
        CloseableHttpClient httpClient = HttpClientUtil.getHttpClient();
        if(headMap != null){
            Set<Map.Entry<String, String>> entries = headMap.entrySet();
            Iterator<Map.Entry<String, String>> iterator = entries.iterator();
            while (iterator.hasNext()){
                Map.Entry<String, String> next = iterator.next();
                httpPost.addHeader(next.getKey(),next.getValue());
            }
        }
        // 执行请求操作，并拿到结果（同步阻塞）
        response = sendRequestAndGetResult(url, httpClient, httpPost);
        // 获取结果实体
        // 判断网络连接状态码是否正常(0--200都数正常)
        if (null != response) {
//            result = EntityUtils.toString(response.getEntity());
            result = dataParse.parseData(response.getEntity(), encoding);
            log.debug("http 请求结果: {}", result);
        }
        try {
            if (null != response) {
                response.close();
            }
        } catch (IOException ex) {
            log.error(ex.getMessage(), ex);
        }
        return result;
    }
    
    /**
     * 发送http请求，打印请求耗时、请求头信息
     * @param url 请求url
     * @param httpClient httpClient
     * @param httpUriRequest httpUriRequest
     * @return 请求返回的数据
     * @throws IOException 读写异常
     */
    private static CloseableHttpResponse sendRequestAndGetResult(String url, CloseableHttpClient httpClient,
            HttpUriRequest httpUriRequest) throws IOException {
        long startTime = System.currentTimeMillis();
        CloseableHttpResponse response = httpClient.execute(httpUriRequest);
        long endTime = System.currentTimeMillis();
        collectAPISpendTime(url, startTime, endTime,httpUriRequest);
        return response;
    }
    
    /**
     * 打印请求信息
     * @param url 请求url
     * @param startTime 请求开始时间
     * @param endTime 请求结束时间
     */
    private static void collectAPISpendTime(String url, long startTime, long endTime,HttpUriRequest httpUriRequest) {
        log.debug("HTTP请求耗时分析，请求URL: {} ， 请求头信息：{} ，   耗时: {} ms", url, JSON.toJSONString(httpUriRequest.getAllHeaders()), endTime - startTime);
        //save server
    }
    
    /**
     * 向url发送post请求上传单文件
     * @param url 请求url
     * @param paramMap 需要表单提交的参数
     * @param fileMap 需要上传的文件
     * @param encoding 编码
     * @return 请求返回的数据
     * @throws IOException 读写异常
     */
    public static String postFile(String url, Map<String, String> paramMap, Map<String, File> fileMap,Map<String, String> headMap, String encoding)
            throws IOException {
        if (fileMap != null) {
            Map<String, List<File>> fileListMap = new HashMap<String, List<File>>();
            for (Map.Entry<String, File> entry : fileMap.entrySet()) {
                File file = entry.getValue();
                List<File> fileList = new ArrayList<File>();
                fileList.add(file);
                fileListMap.put(entry.getKey(), fileList);
            }
            return postMultipleFile(url, paramMap, fileListMap,headMap, encoding);
        }
        return postMultipleFile(url, paramMap, null,headMap, encoding);
    }
    
    /**
     * 向url发送post请求上传多文件
     * 向url发送post请求上传单文件
     * @param url 请求url
     * @param paramMap 需要表单提交的参数
     * @param fileListMap 需要上传的文件
     * @param encoding 编码
     * @return 请求返回的数据
     * @throws IOException 读写异常
     */
    public static String postMultipleFile(String url, Map<String, String> paramMap, Map<String, List<File>> fileListMap,Map<String, String> headMap,
            String encoding) throws IOException {
        return postFileBody(url, paramMap, fileListMap,headMap, encoding, new DataParse<String>() {
            @Override
            public String parseData(HttpEntity httpEntity, String encoding) throws IOException {
                return EntityUtils.toString(httpEntity, encoding);
            }
        });
    }
    
    /**
     * 向url发送post请求上传多文件
     * 向url发送post请求上传单文件
     * @param url 请求url
     * @param paramMap 需要表单提交的参数
     * @param fileListMap 需要上传的文件
     * @param encoding 编码
     * @return 请求返回的数据
     * @throws IOException 读写异常
     */
    private static <T> T postFileBody(String url, Map<String, String> paramMap, Map<String, List<File>> fileListMap,Map<String, String> headMap,
            String encoding, DataParse<T> dataParse) throws IOException {
        log.debug("http 请求 url: {} , 请求参数: {}", url, JSON.toJSONString(paramMap));
        encoding = encoding == null ? UTF8 : encoding;
        T result = null;
        CloseableHttpClient httpClient = HttpClientUtil.getHttpClient();
        HttpPost httpPost = new HttpPost(url);
        MultipartEntityBuilder entityBuilder = MultipartEntityBuilder.create();
        
        ContentType contentType = ContentType.create("text/plain", Charset.forName(encoding));
        if (null != paramMap) {
            for (Map.Entry<String, String> entry : paramMap.entrySet()) {
                entityBuilder.addTextBody(entry.getKey(), entry.getValue(), contentType);
            }
        }
        
        if (null != fileListMap) {
            for (Map.Entry<String, List<File>> entry : fileListMap.entrySet()) {
                String key = entry.getKey();
                List<File> fileList = entry.getValue();
                for (File file : fileList) {
                    FileBody fileBody = new FileBody(file);
                    entityBuilder.addPart(key, fileBody);
                }
            }
        }
        
        HttpEntity entity = entityBuilder.build();
        httpPost.setEntity(entity);
        if(headMap != null){
            Set<Map.Entry<String, String>> entries = headMap.entrySet();
            Iterator<Map.Entry<String, String>> iterator = entries.iterator();
            while (iterator.hasNext()){
                Map.Entry<String, String> next = iterator.next();
                httpPost.addHeader(next.getKey(),next.getValue());
            }
        }
        CloseableHttpResponse response = sendRequestAndGetResult(url, httpClient, httpPost);
        if (null != response) {
            result = dataParse.parseData(response.getEntity(), encoding);
            log.debug("http 请求结果: {}", result);
        }
        try {
            if (null != response) {
                response.close();
            }
        } catch (IOException ex) {
            log.error(ex.getMessage(), ex);
        }
        return result;
    }
    
}
