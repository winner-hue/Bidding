package site;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
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

public class CCGP_ChongQing extends WebGeneral {
    private static Logger logger = LoggerFactory.getLogger(CCGP_ChongQing.class);
    private static JSONObject cats;

    @Override
    protected void setValue() {
        // 采集类型id规则
        catIdRelu = "h2#titlecandel";
        // 城市id规则
        cityIdRelu = 21;
        // 价格规则
        priceRelu = "p:matchesOwn(成交金额：)";
        // 发布时间匹配规则
        addTimeParse = "yyyy-MM-dd HH:mm:ss";
        // 内容规则
        fullcontentRelu = "div.wrap-post ng-scope";
    }

    @Override
    public void run() {
        // 获取任务url
        setValue();
        cats = JSONObject.parseObject(Bidding.properties_cat.getProperty("chongqing_cat"));
        String[] urls = Bidding.properties.getProperty("ccgp.chongqing.url").split(",");
        this.main(urls);
        String start_time = Util.getLastMonth(null, 3), end_time = Util.getLastMonth(null, 0);
        urls = new String[]{"https://www.ccgp-chongqing.gov.cn/gwebsite/api/v1/notices/stable/new?__platDomain__=www.ccgp-chongqing.gov.cn&isResult=1&pi=1&ps=20&type=100,200,201,202,203,204,205,206,207,309,400,401,402,3091,4001",
                "https://www.ccgp-chongqing.gov.cn/gwebsite/api/v1/notices/stable/new?__platDomain__=www.ccgp-chongqing.gov.cn&isResult=2&pi=1&ps=20&type=300,302,304,3041,305,306,307,308,309,400"};
        String[] fs = new String[2];
        for (int i = 0; i < 2; i++) {
            fs[i] = urls[i].concat("&endDate=".concat(end_time).concat("&startDate=".concat(start_time)));
        }
        this.main(fs);
        Bidding.cout.decrementAndGet();
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
            startRun(retryTime, url, 0);
        }
    }

    @Override
    protected void startRun(int retryTime, String url, int currentPage) {
        int types = 0;
        if (url.contains("isResult=1")) {
            types = 1;
        } else if (url.contains("type=1")) {
            types = 2;
        } else if (url.contains("type=2") && !url.contains("__platDomain__")) {
            types = 3;
        } else if (url.contains("type=2") && url.contains("__platDomain__")) {
            types = 4;
        } else if (url.contains("query.state")) {
            types = 5;
        } else {
            types = 6;
        }
        String httpBody = getHttpBody(retryTime, url);
        if (httpBody == null) {
            logger.error("下载失败， 直接返回为空");
            return;
        }
        Document document = Jsoup.parse(httpBody);
        List<StructData> allResult = getAllResult(document, httpBody, types);
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
        try {
            logger.info("==================================");
            JSONObject jo = JSON.parseObject(pageSource).getJSONObject("notice");
            String title = null;
            try {
                title = jo.getString("title");
            } catch (Exception ignore) {
            }
            logger.info("title: " + title);
            data.setTitle(title);
            int catId = -1;
            try {
                catId = getCatIdByText(jo.getString("projectPurchaseWayName"));
            } catch (Exception ignore) {
            }
            logger.info("catId: " + catId);
            data.setCat_id(catId);
            int cityId = cityIdRelu;
            logger.info("cityId: " + cityId);
            data.setCity_id(cityId);
            String purchaser = null;
            try {
                purchaser = jo.getString("buyerName");
            } catch (Exception ignore) {
            }
            logger.info("purchaser: " + purchaser);
            data.setAuthor(purchaser);
            String price = null;
            try {
                price = getPrice(Jsoup.parse(jo.getString("html")));
            } catch (Exception ignore) {
            }
            logger.info("price: " + price);
            data.setPrice(price);
            String detail = null;
            try {
                detail = Jsoup.parse(jo.getString("html")).html();
            } catch (Exception ignore) {
            }
            logger.info("detail: " + detail);
            data.setFullcontent(detail);
            List<String> fileList = new ArrayList<String>();
            try {
                JSONArray attachments = jo.getJSONArray("attachments");
                for (int i = 0; i < attachments.size(); i++) {
                    String value = attachments.getJSONObject(i).getString("value");
                    if (!value.startsWith("http")) {
                        value = "https://www.ccgp-chongqing.gov.cn/" + value;
                    }
                    fileList.add(value);
                }
            } catch (Exception ignore) {
            }
            logger.info("fjxxurl: " + fileList.toString());
            if (fileList.size() > 0) {
                data.setFjxxurl(fileList.toString());
            } else {
                data.setFjxxurl(null);
            }
            String add_time_name = data.getAdd_time_name();
            data.setAdd_time_name(add_time_name);
        } catch (Exception e) {
            logger.error("提取内容出错：" + e, e);
        }
    }

    @Override
    protected String getPrice(Document parse) {
        try {
            return parse.select("p:matchesOwn(成交金额：)").get(0).text().replaceAll("成交金额：", "");
        } catch (Exception e) {
            try {
                return parse.select("p:matchesOwn(中标金额：)").get(0).text().replaceAll("中标金额：", "");
            } catch (Exception ex) {
                return "";
            }
        }
    }

    protected List<StructData> getAllResult(Document parse, String httpBody, int type) {
        List<StructData> allResults = new ArrayList<StructData>();
        JSONArray notices = null;
        String types = null;
        try {
            String[] f = new String[]{"notices", "data", "datas", "contracts"};
            for (String ky:f) {
                try {
                    notices = JSON.parseObject(httpBody).getJSONArray(ky);
                    if (notices.size() > 0) {
                        types = ky;
                        break;
                    }
                } catch (Exception e) {
                }
            }
            for (int i = 0; i < notices.size(); i++) {
                JSONObject jo = notices.getJSONObject(i);
                logger.info("===========================================");
                StructData resultData = new StructData();
                // 获取链接
                String url = null, json_url = null;
                try {
                    String[] time_type = new String[]{"issueTime", "createTime", "time"};
                    Long addTime = null;
                    for (String ky:time_type) {
                        try {
                            addTime = jo.getLong(ky);
                            if (addTime > 0) {
                                break;
                            }
                        } catch (Exception e) {
                        }
                    }
                    logger.info("addTime: " + addTime);
                    if (addTime - this.deadDate.getTime() < 0) {
                        logger.info("发布时间早于截止时间， 不添加该任务url");
                        return allResults;
                    }
                    logger.info("addTime: " + addTime);
                    SimpleDateFormat formats = new SimpleDateFormat("yyyy-MM-dd HH:mm");
                    String add_time_name = formats.format(addTime);
                    resultData.setAdd_time(addTime);
                    resultData.setAdd_time_name(add_time_name);
                    String title = jo.getString("title");
                    logger.info("title: " + title);
                    resultData.setTitle(title);
                    resultData.setDescription(title);
                    String id = jo.getString("id");
                    switch (type) {
                        case 1:
                            url = "https://www.ccgp-chongqing.gov.cn/notices/detail/".concat(id).concat("?title=".concat(title));
                            json_url = "https://www.ccgp-chongqing.gov.cn/gwebsite/api/v1/notices/stable/".concat(id).concat("?__platDomain__=www.ccgp-chongqing.gov.cn");
                            break;
                        case 2:
                            url = "https://www.ccgp-chongqing.gov.cn/stock-resources-front/demandView?id=".concat(id);
                            json_url = "https://www.ccgp-chongqing.gov.cn/yw-gateway/demand/demand/".concat(id).concat("/front");
                            break;
                        case 3:
                            url = "https://www.ccgp-chongqing.gov.cn/stock-resources-front/intentionView?id=".concat(id);
                            json_url = "https://www.ccgp-chongqing.gov.cn/yw-gateway/demand/demand/".concat(id).concat("/front");
                            break;
                        case 4:
                            url = "https://www.ccgp-chongqing.gov.cn/disclosures/".concat(id).concat("?type=0&title=".concat(title));
                            json_url = "https://www.ccgp-chongqing.gov.cn/gwebsite/api/v1/singles/preview/".concat(id).concat("?__platDomain__=www.ccgp-chongqing.gov.cn");
                            break;
                        case 5:
                            url = "https://www.ccgp-chongqing.gov.cn/contractpost/manage_toContractViewPage.action?updateId=".concat(id);
                            json_url = "https://www.ccgp-chongqing.gov.cn/contractpost/manage_getContractById.action&#44updateId=".concat(id);
                            break;
                        case 6:
                            url = "https://www.ccgp-chongqing.gov.cn/notices/detail/".concat(id).concat("?title=".concat(title));
                            json_url = "https://www.ccgp-chongqing.gov.cn/gwebsite/api/v1/notices/stable/".concat(id).concat("?__platDomain__=www.ccgp-chongqing.gov.cn");
                            break;
                    }
                    if (type != 1) {
                        resultData.setCat_id(cats.getIntValue(String.valueOf(type)));
                    } else {
                        String pw = "{\"100\":\"公开招标\",\"200\":\"邀请招标\",\"300\":\"竞争性谈判\",\"400\":\"询价\",\"500\":\"单一来源\",\"800\":\"竞争性磋商\",\"6001\":\"协议竞价\"\n" +
                                ",\"6003\":\"网上询价\"}\n";
                        JSONObject projectPurchaseWay = JSONObject.parseObject(pw);
                        resultData.setCat_id(cats.getJSONObject("1").getIntValue(projectPurchaseWay.getString(jo.getString("projectPurchaseWay"))));
                    }
                    logger.info("url: " + url);
                    resultData.setArticleurl(url);
                } catch (Exception ignore) {
                    continue;
                }
                // 获取发布时间
                try {
                    String[] info_type = new String[]{"notice", "data", "singles", "contract"};
                    String pageSource = getHttpBody(5, json_url);
                    JSONObject data = JSONObject.parseObject(pageSource);
                    String htmls = null;
                    for (String ky:info_type) {
                        try {
                            htmls = data.getJSONObject(ky).getString("html");
                            break;
                        } catch (Exception e) {
                        }
                    }
                    if (htmls != null && !htmls.equals("")) {
                        resultData.setFullcontent(htmls);
                    } else {
                        resultData.setFullcontent(Jsoup.parse(data.toJSONString()).html());
                    }
                    String price = null;
                    if (jo.containsKey("money")) {
                        price = jo.getString("money");
                        if (type == 3) {
                            price = price + "万元";
                        } else {
                            price = price + "元";
                        }
                    } else if (jo.containsKey("projectBudget") && type != 6) {
                        price = jo.getString("projectBudget") + "元";
                    } else {
                        price = getPrice(Jsoup.parse(htmls));
                    }
                    resultData.setPrice(price);
                    String[] author_type = new String[]{"inputOrgName", "buyerName", "createOrgName"};
                    String author = null;
                    for (String ky:author_type) {
                        try {
                            author = jo.getString(ky);
                            if (author.equals("") && author != null) {
                                break;
                            }
                            break;
                        } catch (Exception e) {
                        }
                    }
                    resultData.setAuthor(author);
                } catch (Exception e) {
                    logger.error("获取时间错误：" + e, e);
                    continue;
                }
                resultData.setCity_id(this.cityIdRelu);
                allResults.add(resultData);
            }
        } catch (Exception e) {
            logger.error("提取连接错误：" + e, e);
        }
        return allResults;
    }

    @Override
    protected String getNextPageUrl(Document document, int currentPage, String httpBody, String url) {
        String nextPageUrl = "";
        try {
            if (url.contains("&pi=")) {
                String id = Util.match("&pi=(\\d+)", url)[1];
                nextPageUrl = url.replaceAll("&pi=\\d+", "&pi=" + (Integer.parseInt(id) + 1));
            } else if (url.contains("page=")) {
                String id = Util.match("page=(\\d+)", url)[1];
                nextPageUrl = url.replaceAll("page=\\d+", "page=" + (Integer.parseInt(id) + 1));
            } else {
                String id = Util.match("current=(\\d+)", url)[1];
                nextPageUrl = url.replaceAll("current=\\d+", "current=" + (Integer.parseInt(id) + 1));
            }
        } catch (Exception ignore) {
        }
        logger.info("nextPageUrl: " + nextPageUrl);
        return nextPageUrl;
    }
}