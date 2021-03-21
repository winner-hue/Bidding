package site;

import com.alibaba.fastjson.JSONObject;
import org.apache.http.NameValuePair;
import org.apache.http.client.CookieStore;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.cookie.Cookie;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import po.StructData;
import start.Bidding;
import util.SqlPool;
import util.Util;

import java.nio.charset.Charset;
import java.sql.SQLException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

import static util.Download.getHttpBody;

public class CCGP_GanSu extends WebGeneral {
    private static Logger logger = LoggerFactory.getLogger(CCGP_HeiLongJiang.class);
    private static HashMap<String, String> heads = new HashMap<String, String>();
    private static String relu = "";
    public static JSONObject relus = JSONObject.parseObject(relu);
    public static String cookies = null;

    @Override
    public void run() {
        setValue();
        String start_time = Util.getLastMonth(null, 3), end_time = Util.getLastMonth(null, 0);
        String url = "http://www.ccgp-gansu.gov.cn/web/doSearchmxarticlels.action&#44articleSearchInfoVo.releasestarttime=" + start_time + "&articleSearchInfoVo.releaseendtime=" + end_time + "&articleSearchInfoVo.tflag=1&articleSearchInfoVo.dtype=&articleSearchInfoVo.days=&articleSearchInfoVo.releasestarttimeold=&articleSearchInfoVo.releaseendtimeold=&articleSearchInfoVo.title=&articleSearchInfoVo.agentname=&articleSearchInfoVo.bidcode=&articleSearchInfoVo.proj_name=&articleSearchInfoVo.buyername=&total=0&limit=20&current=1&sjm=7466";
        final String[] classnames = {"c1280501", "c1280502", "c1280101", "c1280103", "c1280104", "c1280102", "c12806", "c12802", "c12804", "c12803", "c12807", "c12820"}, urls = new String[classnames.length];
        for (int i = 0; i < classnames.length; i++) {
            urls[i] = url.concat("&articleSearchInfoVo.classname=").concat(classnames[i]);
        }
        this.main(urls);
        Bidding.cout.decrementAndGet();
    }

    @Override
    protected void setValue() {
        cityIdRelu = 7;
        nodeListRelu = "ul.Expand_SearchSLisi li";
        fullcontentRelu = "div.articleCon";
    }

    @Override
    protected void main(String[] urls) {
        int retryTime = 3;
        SimpleDateFormat format = null;
        try {
            String retryTimes = Bidding.properties.getProperty("download_retry_times");
            retryTime = Integer.parseInt(retryTimes);
        } catch (Exception ignore) {
        }
        try {
            String deadDateParse = Bidding.properties.getProperty("dead.date");
            format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            Date parse = format.parse(deadDateParse);
            long time = parse.getTime();
            this.deadDate = new Date(time);
        } catch (Exception ignore) {
            try {
                format = new SimpleDateFormat("yyyy-MM-dd");
                this.deadDate = format.parse(Util.getLastMonth(null, 3));
//                this.deadDate = format.parse("2021-03-11");
            } catch (ParseException e) {
                e.printStackTrace();
            }
        }
        String doSearchmxarticlels = "http://www.ccgp-gansu.gov.cn/web/doSearchmxarticlels.action?limit=20&start=0";
        for (String url : urls) {
            logger.info("当前开始url： " + url);
            this.baseUrl = url.split("/web")[0];
            cookies = result_cookie(url, "utf-8", 2);
            heads.put("Cookie", cookies);
            startRun(retryTime, doSearchmxarticlels, 0);
        }
    }

    @Override
    protected String getNextPageUrl(Document document, int currentPage, String httpBody, String url) {
        return url.replaceAll("start=(\\d+)", "start=" + (currentPage + 20));
    }

    @Override
    protected void startRun(int retryTime, String url, int currentPage) {
        String httpBody = getHttpBody(retryTime, url, heads);
        if (httpBody == null) {
            logger.error("下载失败， 直接返回为空");
            return;
        }
        Document document = Jsoup.parse(httpBody);
        List<StructData> allResult = getAllResult(document, httpBody);
        for (StructData data : allResult) {
            String tempUrl = data.getArticleurl();
            String pageSource = null;
            try {
                pageSource = getHttpBody(retryTime, tempUrl);
                Document parse = Jsoup.parse(pageSource);
                extract(parse, data, pageSource);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        int count = 0;
        for (StructData resultData : allResult) {
            try {
                String sql;
                String tableName = Bidding.properties.getProperty("table.name");
                if (tableName == null) {
                    sql = Util.getInsertSql("fa_article", StructData.class, resultData);
                } else {
                    sql = Util.getInsertSql(tableName, StructData.class, resultData);
                }
                SqlPool.getInstance().getStatement().execute(sql);
                logger.info("当前插入第：" + count + " 条");
                ++count;
            } catch (SQLException e) {
                logger.error("插入数据错误：" + e, e);
            }
        }
        logger.info("插入成功：" + count + " 条");
        //添加下一页
        if ((allResult.size() - count <= allResult.size() - 1) && (allResult.size() > 0)) {
            try {
                try {
                    currentPage = Integer.parseInt(Util.match("start=(\\d+)", url)[1]);
                } catch (Exception ignore) {
                }
                String nextPageUrl = getNextPageUrl(document, currentPage, httpBody, url);
                if (nextPageUrl != null && (!"".equals(nextPageUrl))) {
                    startRun(retryTime, nextPageUrl, (currentPage + 1));
                }
            } catch (Exception e) {
                logger.error("下一页提取错误：" + e, e);
            }
        }
    }

    @Override
    protected void extract(Document parse, StructData data, String pageSource) {
        super.extract(parse, data, pageSource);
    }

    @Override
    protected String getAnnex(Document parse) {
        return super.getAnnex(parse);
    }

    @Override
    protected String getDetail(Document parse) {
        return super.getDetail(parse);
    }

    @Override
    protected String getPrice(Document parse) {
        return super.getPrice(parse);
    }

    @Override
    protected String getAuthor(Document parse) {
        return super.getAuthor(parse);
    }

    @Override
    protected int getCatId(Document parse) {
        return super.getCatId(parse);
    }

    @Override
    protected String getDescription(Document parse) {
        return super.getDescription(parse);
    }

    @Override
    protected String getTitle(Document parse) {
        return super.getTitle(parse);
    }

    @Override
    protected String getUrl(Element element) {
        return super.getUrl(element);
    }

    @Override
    protected Date getAddTime(Element element) {
        return super.getAddTime(element);
    }

    @Override
    protected List<StructData> getAllResult(Document parse, String httpBody) {
        List<StructData> allResults = new ArrayList<StructData>();
        Elements cListBid = parse.select(this.nodeListRelu);
        for (Element element : cListBid) {
            logger.info("===========================================");
            StructData resultData = new StructData();
            try {
                // 获取链接
                String url = getUrl(element);
                logger.info("url: " + url);
                resultData.setArticleurl(url);
                String[] infos = element.select("p:eq(1) span").get(0).text().trim().split("\\|");
                SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                Date date = format.parse(infos[1].split("：")[1]);
                long addTime = date.getTime();
                logger.info("addTime: " + addTime);
                if (addTime - this.deadDate.getTime() < 0) {
                    logger.info("发布时间早于截止时间， 不添加该任务url");
                    break;
                }
                String title = element.select("a").get(0).text();
                resultData.setTitle(title);
                resultData.setDescription(title);
                format = new SimpleDateFormat("yyyy-MM-dd HH:mm");
                String add_time_name = format.format(addTime);
                resultData.setAdd_time(addTime);
                resultData.setAdd_time_name(add_time_name);
                resultData.setCity_id(this.cityIdRelu);
                String author = infos[2].split("：")[1];
                resultData.setAuthor(author);
                int catIdByText = -1;
                try {
                    catIdByText = getCatIdByText(element.select("p:eq(2) span strong").get(0).text());
                    logger.info("catId: " + catIdByText);
                } catch (Exception ignore) {
                }
                resultData.setCat_id(catIdByText);
                allResults.add(resultData);
            } catch (Exception e) {
                logger.error("提取链接错误：" + e, e);
            }
        }
        return allResults;
    }

    public static String result_cookie(String url, String charSet, int content_type) {
        if (content_type == 1) {
            url = url.replaceAll("&application_jsons", "");
        }
        String[] split = url.split("&#44");
        CloseableHttpClient httpClient = null;
        CookieStore cookieStore = new BasicCookieStore();
        httpClient = HttpClients.custom().setDefaultCookieStore(cookieStore).build();
        HttpPost httpPost = new HttpPost(split[0]);
        String[] params = split[1].split("&");
        try {
            if (content_type == 1) {
                Map<String,Object> map = new HashMap<String,Object>();
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
        RequestConfig config = RequestConfig.custom().setConnectionRequestTimeout(10000).setConnectTimeout(10000).setSocketTimeout(10000).build();
        httpPost.setConfig(config);
        try {
            String result = "";
            CloseableHttpResponse response = httpClient.execute(httpPost);
            List<Cookie> cookies = cookieStore.getCookies();
            for (int i = 0; i < cookies.size(); i++) {
                result = result + cookies.get(i).getName() + "=" + cookies.get(i).getValue() + ";";
            }
            return result;
        } catch (Exception e) {
        }
        return null;
    }
}
