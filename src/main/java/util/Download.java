package util;

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
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import po.Proxy;
import start.Bidding;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

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
            if (httpBody == null) {
                logger.info("当前重试下载次数为：" + i + " " + url);
                continue;
            }
            break;
        }
        return httpBody;
    }

    public static String download(String url, String charSet) {
        if (url.contains("&#44")) {
            return downPost(url, charSet);
        } else {
            return downGet(url, charSet);
        }
    }

    public static String downPost(String url, String charSet) {
        String[] split = url.split("&#44");
        CloseableHttpClient httpClient = HttpClients.createDefault();
        HttpPost httpPost = new HttpPost(split[0]);
        String[] params = split[1].split("&");
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
        try {
            httpPost.setEntity(new UrlEncodedFormEntity(postParams));
        } catch (Exception ignore) {
        }
        httpPost.addHeader("User-Agent", userAgent[new Random().nextInt(userAgent.length)]);
        if (proxy.getHost() != null) {
            HttpHost host = new HttpHost(proxy.getHost(), proxy.getPort());
            RequestConfig config = RequestConfig.custom().setProxy(host).setConnectionRequestTimeout(5000).setConnectTimeout(5000).setSocketTimeout(5000).build();
            httpPost.setConfig(config);
            if (proxy.getUser() != null) {
                CredentialsProvider provider = new BasicCredentialsProvider();
                provider.setCredentials(new AuthScope(host), new UsernamePasswordCredentials("username", "password"));
                httpClient = HttpClients.custom().setDefaultCredentialsProvider(provider).build();
            }
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
            RequestConfig config = RequestConfig.custom().setProxy(host).setConnectionRequestTimeout(5000).setConnectTimeout(5000).setSocketTimeout(5000).build();
            httpGet.setConfig(config);
            if (proxy.getUser() != null) {
                CredentialsProvider provider = new BasicCredentialsProvider();
                provider.setCredentials(new AuthScope(host), new UsernamePasswordCredentials("username", "password"));
                httpClient = HttpClients.custom().setDefaultCredentialsProvider(provider).build();
            }
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

    public static void main(String[] args) {
        System.out.println(downPost("http://www.ccgp-liaoning.gov.cn/portalindex.do?method=getPubInfoList&t_k=null&tk=0.33597649210232605&#44current=1&rowCount=10&searchPhrase=&infoTypeCode=1001&privateOrCity=1", "utf-8"));
    }
}
