package site;

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
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static util.Download.getHttpBody;

public class CCGP_AnHui extends WebGeneral{
    private static Logger logger = LoggerFactory.getLogger(CCGP_AnHui.class);

    @Override
    public void run() {
        setValue();
        String url = "http://www.ccgp-anhui.gov.cn/cmsNewsController/getCgggNewsList.do?pageNum=1&numPerPage=20&title=&buyer_name=&agent_name=&proj_code=&bid_type=&dist_code=340000&pubDateStart=&pubDateEnd=&pProviceCode=340000&areacode_city=&areacode_dist=&channelCode=sjcg_cggg&three=on";
        final String[] types = {"101", "105", "103", "102", "104", "1031"}, urls = new String[types.length];
        for (int i = 0; i < types.length; i++) {
            urls[i] = url.concat("type=".concat(types[i]));
        }
        this.main(urls);
        Bidding.cout.decrementAndGet();
    }

    @Override
    protected void setValue() {
        cityIdRelu = 12;
        nodeListRelu = "div.zc_search_title + table tbody tr";
        fullcontentRelu = "div.frameNews";
        priceRelu = "td:containsOwn(成交总价) + td|td:containsOwn(项目预算) + td";
        authorRelu = "td:containsOwn(采购人名称) + td";
        catIdRelu = "td:containsOwn(公告类型) + td";
        titleRelu = "td:eq(0) a";
        addTimeRelu = "td:eq(1) a";
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
            } catch (ParseException e) {
                e.printStackTrace();
            }
        }
        for (String url : urls) {
            logger.info("当前开始url： " + url);
            this.baseUrl = url.split("/cmsNewsController")[0];
            startRun(retryTime, url, 0);
        }
    }

    @Override
    protected String getNextPageUrl(Document document, int currentPage, String httpBody, String url) {
        return url.replaceAll("pageNum=(\\d+)", "pageNum=" + (currentPage + 1));
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
                    currentPage = Integer.parseInt(Util.match("pageNum=(\\d+)", url)[1]);
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
        String author = parse.select(authorRelu).get(0).text();
        data.setAuthor(author);
        String price = getPrice(parse);
        logger.info("price: " + price);
        data.setPrice(price);
        String detail = getDetail(parse);
        logger.info("detail: " + detail);
        data.setFullcontent(detail);
        int catIdByText = -1;
        try {
            String cst = parse.select(catIdRelu).get(0).text();
            catIdByText = getCatIdByText(cst);
            logger.info("catId: " + catIdByText);
        } catch (Exception ignore) {
        }
        data.setCat_id(catIdByText);
        String annex = getAnnex(parse);
        logger.info("annex: " + annex);
        data.setFjxxurl(annex);
    }

    @Override
    protected String getPrice(Document parse) {
        String price_str = "";
        String[] priceRelus = priceRelu.split("\\|");
        try {
            for (String priceRelu : priceRelus) {
                try {
                    price_str = parse.select(priceRelu).get(0).text();
                } catch (Exception e) {
                    logger.error(e.toString());
                }
                if (price_str.length() > 0) {
                    break;
                }
            }
        } catch (Exception e) {
            logger.error(e.toString());
        }
        return price_str;
    }

    protected String getAuthor(Document parse, String query_sign) {
        String author = "";
        try {
            String[] authorRelus = authorRelu.split("\\|");
            for (String authorRelu : authorRelus) {
                try {
                    author = parse.select(authorRelu).get(0).text();
                } catch (Exception e) {
                    logger.error(e.toString());
                }
                if (author.length() > 0 && !author.equals("")) {
                    break;
                }
            }
        } catch (Exception e) {
            logger.error(e.toString());
        }
        return author;
    }

    @Override
    protected List<StructData> getAllResult(Document parse, String httpBody) {
        List<StructData> allResults = new ArrayList<StructData>();
        Elements cListBid = parse.select(this.nodeListRelu);
        for (Element element : cListBid) {
            logger.info("===========================================");
            StructData resultData = new StructData();
            try {
                String url = getUrl(element);
                if (url.equals("") || url == null){
                    continue;
                }
                logger.info("url: " + url);
                resultData.setArticleurl(url);
                String hits = element.select(addTimeRelu).get(0).text().trim().replace("[", "").replace("]", "");
                SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
                Date date = format.parse(hits);
                long addTime = date.getTime();
                logger.info("addTime: " + addTime);
                if (addTime - this.deadDate.getTime() < 0) {
                    logger.info("发布时间早于截止时间， 不添加该任务url");
                    allResults.removeAll(allResults);
                    return allResults;
                }
                String title = element.select(titleRelu).get(0).attr("title");
                resultData.setTitle(title);
                resultData.setDescription(title);
                format = new SimpleDateFormat("yyyy-MM-dd HH:mm");
                String add_time_name = format.format(addTime);
                resultData.setAdd_time(addTime);
                resultData.setAdd_time_name(add_time_name);
                resultData.setCity_id(this.cityIdRelu);
                allResults.add(resultData);
            } catch (Exception e) {
                logger.error("提取链接错误：" + e, e);
            }
        }
        return allResults;
    }
}
