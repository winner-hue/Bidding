package util;

import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class Download {
    private static Logger logger = LoggerFactory.getLogger(Download.class);

    public static String getHttpBody(int retryTime, String url) {
        String httpBody = null;
        for (int i = 0; i < retryTime; i++) {
            httpBody = download(url, "UTF-8");
            if (httpBody == null) {
                continue;
            }
            break;
        }
        return httpBody;
    }

    public static String download(String url, String charSet) {
        CloseableHttpClient httpClient = HttpClients.createDefault();
        HttpGet httpGet = new HttpGet(url);
        httpGet.addHeader("User-Agent", "Mozilla/5.0 (Windows NT 6.1; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/85.0.4183.102 Safari/537.36");
        try {
            CloseableHttpResponse response = httpClient.execute(httpGet);
            HttpEntity entity = response.getEntity();
            String httpBody = EntityUtils.toString(entity, charSet);
            return httpBody;
        } catch (IOException e) {
            logger.error(url + " 下载失败：" + e, e);
        }
        return null;
    }
}
