package util;

import com.alibaba.fastjson.JSONObject;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.NameValuePair;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.ssl.SSLContexts;
import org.apache.http.ssl.TrustStrategy;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import po.Proxy;
import start.Bidding;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.nio.charset.Charset;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.*;

public class Download {
    private static Logger logger = LoggerFactory.getLogger(Download.class);
    private static Proxy proxy = new Proxy();
    private static String userAgent[] = {
            "Mozilla/5.0 (Windows NT 6.1; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/85.0.4183.102 Safari/537.36",
            "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/39.0.2171.95 Safari/537.36 OPR/26.0.1656.60",
            "Mozilla/5.0 (Windows NT 5.1; U; en; rv:1.8.1) Gecko/20061208 Firefox/2.0.0 Opera 9.50",
            "Mozilla/5.0 (Windows NT 6.1; WOW64; rv:34.0) Gecko/20100101 Firefox/34.0",
            "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/534.57.2 (KHTML, like Gecko) Version/5.1.7 Safari/534.57.2",
            "Mozilla/5.0 (Windows NT 6.1; WOW64; Trident/7.0; rv:11.0) like Gecko"
    };

    static {
        String host = Bidding.properties.getProperty("proxy.host");
        if (host != null) {
            int port = Integer.parseInt(Bidding.properties.getProperty("proxy.port", "8000"));
            proxy.setHost(host);
            proxy.setPort(port);
            String user = Bidding.properties.getProperty("proxy.user");
            if (user != null) {
                proxy.setUser(user);
                String pwd = Bidding.properties.getProperty("proxy.pwd");
                proxy.setPwd(pwd);
            }
        }
    }

    public static String getHttpBody(int retryTime, String url) {
        String httpBody = null;
        for (int i = 0; i < retryTime; i++) {
            httpBody = download(url, "UTF-8");
            try {
                String property = Bidding.properties.getProperty("download.sleep");
                if (property != null || "".equals(property)) {
                    Thread.sleep(Integer.parseInt(property) * 1000);
                }
            } catch (Exception ignore) {
            }
            if (httpBody == null || httpBody.contains("404 Not Found") || httpBody.contains("Failed to connect parent proxy")) {
                logger.info("当前重试下载次数为：" + i + " " + url);
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                }
                continue;
            }
            break;
        }
        return httpBody;
    }

    public static String getHttpBody(int retryTime, String url, Map<String, String> header) {
        String httpBody = null;
        for (int i = 0; i < retryTime; i++) {
            httpBody = download(url, "UTF-8", header);
            try {
                String property = Bidding.properties.getProperty("download.sleep");
                if (property != null || "".equals(property)) {
                    Thread.sleep(Integer.parseInt(property) * 1000);
                }
            } catch (Exception ignore) {
            }
            if (httpBody == null || httpBody.contains("404 Not Found")) {
                logger.info("当前重试下载次数为：" + i + " " + url);
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                }
                continue;
            }
            break;
        }
        return httpBody;
    }

    public static String download(String url, String charSet, Map<String, String> header) {
        if (url.contains("&#44")) {
            if (url.contains("&application_jsons")) {
                /*json数据*/
                return downPost(url, charSet, 1, header);
            }
            return downPost(url, charSet, 2, header);
        } else {
            return downGet(url, charSet, header);
        }
    }

    public static String download(String url, String charSet) {
        if (url.contains("&#44")) {
            if (url.contains("&application_jsons")) {
                /*json数据*/
                return downPost(url, charSet, 1);
            }
            return downPost(url, charSet, 2);
        } else {
            return downGet(url, charSet);
        }
    }

    public static String downPost(String url, String charSet, int content_type) {
        if (content_type == 1) {
            url = url.replaceAll("&application_jsons", "");
        }
        String[] split = url.split("&#44");
        CloseableHttpClient httpClient = HttpClients.createDefault();
        HttpPost httpPost = new HttpPost(split[0]);
        String[] params = split[1].split("&");
        try {
            if (content_type == 1) {
                Map<String, Object> map = new HashMap<String, Object>();
                for (String param : params) {
                    String[] keyValue = param.split("=");
                    String key = keyValue[0];
                    String value = "";
                    try {
                        value = keyValue[1];
                    } catch (Exception ignore) {
                    }
                    map.put(key, value);
                }
                JSONObject json = new JSONObject(map);
                httpPost.addHeader("Content-Type", "application/json");
                httpPost.setEntity(new StringEntity(json.toJSONString(), Charset.forName("UTF-8")));
            } else {
                List<NameValuePair> postParams = new ArrayList<NameValuePair>();
                for (String param : params) {
                    String[] keyValue = param.split("=");
                    String key = keyValue[0];
                    String value = "";
                    try {
                        value = keyValue[1];
                    } catch (Exception ignore) {
                    }
                    postParams.add(new BasicNameValuePair(key, value));
                }
                httpPost.setEntity(new UrlEncodedFormEntity(postParams));
            }
        } catch (Exception ignore) {
        }
        httpPost.addHeader("User-Agent", userAgent[new Random().nextInt(userAgent.length)]);
        if (proxy.getHost() != null) {
            HttpHost host = new HttpHost(proxy.getHost(), proxy.getPort());
            RequestConfig config = RequestConfig.custom().setProxy(host).setConnectionRequestTimeout(10000).setConnectTimeout(10000).setSocketTimeout(10000).build();
            httpPost.setConfig(config);
            if (proxy.getUser() != null) {
                CredentialsProvider provider = new BasicCredentialsProvider();
                provider.setCredentials(new AuthScope(host), new UsernamePasswordCredentials(proxy.getUser(), proxy.getPwd()));
                httpClient = HttpClients.custom().setDefaultCredentialsProvider(provider).setSSLSocketFactory(customSSLConnection()).build();
            }
        } else {
            RequestConfig config = RequestConfig.custom().setConnectionRequestTimeout(10000).setConnectTimeout(10000).setSocketTimeout(10000).build();
            httpPost.setConfig(config);
        }
        try {
            CloseableHttpResponse response = httpClient.execute(httpPost);
            HttpEntity entity = response.getEntity();
            String httpBody = EntityUtils.toString(entity, charSet);
            return httpBody;
        } catch (Exception e) {
            logger.error(url + " 下载失败：" + e, e);
        }
        return null;
    }

    public static String downPost(String url, String charSet, int content_type, Map<String, String> header) {
        if (content_type == 1) {
            url = url.replaceAll("&application_jsons", "");
        }
        String[] split = url.split("&#44");
        CloseableHttpClient httpClient = HttpClients.createDefault();
        HttpPost httpPost = new HttpPost(split[0]);
        String[] params = split[1].split("&");
        try {
            if (content_type == 1) {
                Map<String, Object> map = new HashMap<String, Object>();
                for (String param : params) {
                    String[] keyValue = param.split("=");
                    String key = keyValue[0];
                    String value = "";
                    try {
                        value = keyValue[1];
                    } catch (Exception ignore) {
                    }
                    map.put(key, value);
                }
                JSONObject json = new JSONObject(map);
                httpPost.addHeader("Content-Type", "application/json");
                httpPost.setEntity(new StringEntity(json.toJSONString(), Charset.forName("UTF-8")));
            } else {
                List<NameValuePair> postParams = new ArrayList<NameValuePair>();
                for (String param : params) {
                    String[] keyValue = param.split("=");
                    String key = keyValue[0];
                    String value = "";
                    try {
                        value = keyValue[1];
                    } catch (Exception ignore) {
                    }
                    postParams.add(new BasicNameValuePair(key, value));
                }
                httpPost.setEntity(new UrlEncodedFormEntity(postParams));
            }
        } catch (Exception ignore) {
        }
        httpPost.addHeader("User-Agent", userAgent[new Random().nextInt(userAgent.length)]);
        for (String key : header.keySet()) {
            String value = header.get(key);
            httpPost.addHeader(key, value);
        }
        if (proxy.getHost() != null) {
            HttpHost host = new HttpHost(proxy.getHost(), proxy.getPort());
            RequestConfig config = RequestConfig.custom().setProxy(host).setConnectionRequestTimeout(10000).setConnectTimeout(10000).setSocketTimeout(10000).build();
            httpPost.setConfig(config);
            if (proxy.getUser() != null) {
                CredentialsProvider provider = new BasicCredentialsProvider();
                provider.setCredentials(new AuthScope(host), new UsernamePasswordCredentials(proxy.getUser(), proxy.getPwd()));
                httpClient = HttpClients.custom().setDefaultCredentialsProvider(provider).setSSLSocketFactory(customSSLConnection()).build();
            }
        } else {
            RequestConfig config = RequestConfig.custom().setConnectionRequestTimeout(10000).setConnectTimeout(10000).setSocketTimeout(10000).build();
            httpPost.setConfig(config);
        }
        try {
            CloseableHttpResponse response = httpClient.execute(httpPost);
            HttpEntity entity = response.getEntity();
            String httpBody = EntityUtils.toString(entity, charSet);
            return httpBody;
        } catch (Exception e) {
            logger.error(url + " 下载失败：" + e, e);
        }
        return null;
    }

    public static String downGet(String url, String charSet) {

        CloseableHttpClient httpClient = HttpClients.createDefault();
        HttpGet httpGet = new HttpGet(url);
        httpGet.addHeader("User-Agent", userAgent[new Random().nextInt(userAgent.length)]);
        if (proxy.getHost() != null) {
            HttpHost host = new HttpHost(proxy.getHost(), proxy.getPort());
            RequestConfig config = RequestConfig.custom().setProxy(host).setConnectionRequestTimeout(1000).setConnectTimeout(1000).setSocketTimeout(1000).build();
            httpGet.setConfig(config);
            if (proxy.getUser() != null) {
                CredentialsProvider provider = new BasicCredentialsProvider();
                provider.setCredentials(new AuthScope(host), new UsernamePasswordCredentials(proxy.getUser(), proxy.getPwd()));
                httpClient = HttpClients.custom().setDefaultCredentialsProvider(provider).setSSLSocketFactory(customSSLConnection()).build();
            }
        } else {
            RequestConfig config = RequestConfig.custom().setConnectionRequestTimeout(10000).setConnectTimeout(10000).setSocketTimeout(10000).build();
            httpGet.setConfig(config);
        }
        try {
            CloseableHttpResponse response = httpClient.execute(httpGet);
            HttpEntity entity = response.getEntity();
            String httpBody = EntityUtils.toString(entity, charSet);
            return httpBody;
        } catch (Exception e) {
            logger.error(url + " 下载失败：" + e, e);
        }
        return null;
    }

    public static String downGet(String url, String charSet, Map<String, String> header) {

        CloseableHttpClient httpClient = HttpClients.createDefault();
        HttpGet httpGet = new HttpGet(url);
        httpGet.addHeader("User-Agent", userAgent[new Random().nextInt(userAgent.length)]);
        for (String key : header.keySet()) {
            String value = header.get(key);
            httpGet.addHeader(key, value);
        }
        if (proxy.getHost() != null) {
            HttpHost host = new HttpHost(proxy.getHost(), proxy.getPort());
            RequestConfig config = RequestConfig.custom().setProxy(host).setConnectionRequestTimeout(10000).setConnectTimeout(10000).setSocketTimeout(10000).build();
            httpGet.setConfig(config);
            if (proxy.getUser() != null) {
                CredentialsProvider provider = new BasicCredentialsProvider();
                provider.setCredentials(new AuthScope(host), new UsernamePasswordCredentials(proxy.getUser(), proxy.getPwd()));
                httpClient = HttpClients.custom().setDefaultCredentialsProvider(provider).setSSLSocketFactory(customSSLConnection()).build();
            }
        } else {
            RequestConfig config = RequestConfig.custom().setConnectionRequestTimeout(10000).setConnectTimeout(10000).setSocketTimeout(10000).build();
            httpGet.setConfig(config);
        }
        try {
            CloseableHttpResponse response = httpClient.execute(httpGet);
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode == 200) {
                HttpEntity entity = response.getEntity();
                String httpBody = EntityUtils.toString(entity, charSet);
                return httpBody;
            }
        } catch (Exception e) {
            logger.error(url + " 下载失败：" + e, e);
        }
        return null;
    }

    private static SSLConnectionSocketFactory customSSLConnection() {
        try {
            SSLContext sslcontext = SSLContexts.custom()
                    .loadTrustMaterial(new TrustStrategy() {
                        @Override
                        public boolean isTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {
                            return true;
                        }
                    })
                    .build();
            SSLConnectionSocketFactory sslConnectionSocketFactory = new SSLConnectionSocketFactory(sslcontext);
            return sslConnectionSocketFactory;
        } catch (Exception e) {
        }
        return null;
    }

    public static void main(String[] args) {
        System.out.println(downGet("https://www.ccgp-hainan.gov.cn/cgw/cgw_show_jzxcsgg.jsp?id=8393", "utf-8"));
    }
}
