package site;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
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

import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static util.Download.getHttpBody;

public class CCGP_ShangHai extends WebGeneral{
    private static Logger logger = LoggerFactory.getLogger(CCGP_ShangHai.class);

    @Override
    public void run() {
        setValue();
        int length_num = 6;
        String url_start = "http://www.zfcg.sh.gov.cn/front/search/category";
        String[] urls = new String[length_num];
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
        String startDate = format.format(new Date(System.currentTimeMillis() - 3 * 30 * 24 * 60 * 60 * 1000L));
        for (int i = 0; i < length_num; i++) {
            String code = "ZcyAnnouncement".concat(String.valueOf(i + 1));
            urls[i] = url_start.concat("&application_jsons&#44utm=sites_group_front.2ef5001f.0.0.f5992140395c11eb9a363987d6b81c96&categoryCode=".concat(code).concat("&pageSize=50&pageNo=1&publishDateBegin=".concat(startDate).concat("&application_jsons")));
        }
        this.main(urls);
        Bidding.cout.decrementAndGet();
    }

    @Override
    protected void setValue() {
        cityIdRelu = 5;
        titleRelu = "body.view + header + h1";
        descriptionRelu = titleRelu;
        authorRelu = "span:matchesOwn(采购单位：)";
        priceRelu = "";
        fullcontentRelu = "tbody tr";
    }

    @Override
    protected void main(String[] urls) {
        super.main(urls);
    }

    @Override
    protected int getMaxPageSize(Document document) {
        return super.getMaxPageSize(document);
    }

    @Override
    protected String getNextPageUrl(Document document, int currentPage, String httpBody, String url) {
        return super.getNextPageUrl(document, currentPage, httpBody, url);
    }

    @Override
    protected void startRun(int retryTime, String url, int currentPage) {
        String httpBody = getHttpBody(retryTime, url);
        if (httpBody == null) {
            logger.error("下载失败， 直接返回为空");
            return;
        }
        Document document = Jsoup.parse(httpBody);
        List<StructData> allResult = getAllResult(document, httpBody);
        for (StructData data : allResult) {
            String tempUrl = data.getArticleurl();
            String pageSource = getHttpBody(retryTime, tempUrl);
            Document parse = Jsoup.parse(pageSource);
            Element iframe = parse.select("iframe").first();
            String iframeSrc = iframe.attr("src");
            System.out.println(iframeSrc);
            parse = Jsoup.parse(parse.select("iframe").first().text());
            extract(parse, data, pageSource);
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
                    currentPage = Integer.parseInt(Util.match("index_(\\d+)", url)[1]);
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
        try {
            String content = "";
            try {
                Elements elements = parse.select(fullcontentRelu);
                StringBuilder builder = new StringBuilder();
                for (int i = 0; i < elements.size(); i++) {
                    if (i > 0) {
                        builder.append(elements.get(i).text());
                    }
                }
                content = builder.toString();
            } catch (Exception ignore) {
            }
            return content;
        } catch (Exception e) {
            return "";
        }
    }

    @Override
    protected String getPrice(Document parse) {
        return super.getPrice(parse);
    }

    @Override
    protected String getAuthor(Document parse) {
        try {
            return parse.select(this.authorRelu).get(0).text().split("：")[1];
        } catch (Exception e) {
            return "";
        }
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
    protected List<StructData> getAllResult(Document parse, String httpBody) {
        List<StructData> allResults = new ArrayList<StructData>();
        try {
            JSONArray rows = JSONObject.parseObject(httpBody).getJSONObject("hits").getJSONArray("hits");
            for (int i = 0; i < rows.size(); i++) {
                logger.info("===========================================");
                JSONObject jo = rows.getJSONObject(i);
                StructData resultData = new StructData();
                try {
                    String source_url = jo.getJSONObject("_source").getString("url");
                    String url = "http://www.zfcg.sh.gov.cn" + source_url;
                    logger.info("url: " + url);
                    resultData.setArticleurl(url);
                } catch (Exception e) {
                    continue;
                }
                try {
                    long newworkDateAll = (Long) jo.getJSONArray("sort").get(0);
                    logger.info("addTime: " + newworkDateAll);
                    if (newworkDateAll - this.deadDate.getTime() < 0) {
                        logger.info("发布时间早于截止时间， 不添加该任务url");
                        continue;
                    }
                    resultData.setAdd_time(newworkDateAll);
                } catch (Exception ignore) {
                    logger.error(ignore.toString());
                }
                int catIdByText = -1;
                try {
                    String catId = jo.getJSONObject("_source").getString("pathName");
                    catIdByText = getCatIdByText(catId);
                    logger.info("catId: " + catIdByText);
                } catch (Exception ignore) {
                }
                resultData.setCat_id(catIdByText);
                logger.info("cityId: " + this.cityIdRelu);
                resultData.setCity_id(this.cityIdRelu);
                allResults.add(resultData);
            }
        } catch (Exception e) {
            logger.error("获取json失败：" + e, e);
        }
        return allResults;
    }
}
