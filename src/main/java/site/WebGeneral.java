package site;

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

public class WebGeneral extends Thread {
    private static Logger logger = LoggerFactory.getLogger(WebGeneral.class);
    // 最大页码数
    protected int maxPageSize = 1;
    // 任务url
    protected String baseUrl = "";
    // 标题规则
    protected String titleRelu = "";
    // 描述、简介规则
    protected String descriptionRelu = "";
    // 采集类型id规则
    protected String catIdRelu;
    // 城市id规则
    protected int cityIdRelu;
    // 采购人、作者规则
    protected String authorRelu = "";
    // 价格规则
    protected String priceRelu = "";
    // 发布时间规则
    protected String addTimeRelu = "";
    // 发布时间匹配规则
    protected String addTimeParse = "yyyy-MM-dd";
    // 内容规则
    protected String fullcontentRelu = "";
    // 附件规则
    protected String fjxxurlRelu = "";
    // 列表url节点规则
    protected String nodeListRelu = "";
    // 设置截止时间
    protected Date deadDate = null;

    @Override
    public void run() {
        // 获取任务url
        setValue();
        String[] urls = Bidding.properties.getProperty("ccgp.beijing.url").split(",");
        this.main(urls);
        Bidding.cout.decrementAndGet();
    }

    protected void setValue() {
        //子类继承
    }

    /**
     * 主函数
     *
     * @param urls
     */
    protected void main(String[] urls) {
        int retryTime = 3;
        try {
            String retryTimes = Bidding.properties.getProperty("download_retry_times");
            retryTime = Integer.parseInt(retryTimes);
        } catch (Exception ignore) {
        }
        try {
            String deadDateParse = Bidding.properties.getProperty("dead.date");
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            Date parse = format.parse(deadDateParse);
            long time = parse.getTime();
            this.deadDate = new Date(time);
        } catch (Exception ignore) {
            this.deadDate = new Date(System.currentTimeMillis() - (3 * 30 * 24 * 60 * 60 * 1000L));
        }
        for (String url : urls) {
            logger.info("当前开始url： " + url);
            this.baseUrl = url;
            startRun(retryTime, url, 0);
        }
    }


    /**
     * 获取最大页码
     *
     * @param document
     * @return
     */
    protected int getMaxPageSize(Document document) {
        return maxPageSize;
    }

    /**
     * 获取下一页
     *
     * @param document
     * @param currentPage
     * @param httpBody
     * @param url
     * @return
     */
    protected String getNextPageUrl(Document document, int currentPage, String httpBody, String url) {
        String nextPageUrl = null;
        if (this.maxPageSize == 1) {
            this.maxPageSize = getMaxPageSize(document);
            logger.info("maxPageSize: " + this.maxPageSize);
        }
        if (((currentPage == maxPageSize) && (maxPageSize != 0)) || currentPage < this.maxPageSize) {

            nextPageUrl = url.substring(0, url.indexOf("index_") == -1 ? url.length() : url.indexOf("index_")) + "index_" + (currentPage + 1) + ".htm";
            logger.info("nextPageUrl: " + nextPageUrl);
        }
        return nextPageUrl;
    }

    /**
     * 递归函数， 递归获取下一页翻页采集
     *
     * @param retryTime
     * @param url
     * @param currentPage
     */
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

    /**
     * 提取字段
     *
     * @param parse
     * @param data
     */
    protected void extract(Document parse, StructData data, String pageSource) {
        logger.info("==================================");
        String title = getTitle(parse);
        logger.info("title: " + title);
        data.setTitle(title);
        String description = getDescription(parse);
        logger.info("description: " + description);
        data.setDescription(description);
        int catId = getCatId(parse);
        logger.info("catId: " + catId);
        data.setCat_id(catId);
        int cityId = cityIdRelu;
        logger.info("cityId: " + cityId);
        data.setCity_id(cityId);
        String author = getAuthor(parse);
        logger.info("author: " + author);
        data.setAuthor(author);
        String price = getPrice(parse);
        logger.info("price: " + price);
        data.setPrice(price);
        String detail = getDetail(parse);
        logger.info("detail: " + detail);
        data.setFullcontent(detail);
        String annex = getAnnex(parse);
        logger.info("annex: " + annex);
        data.setFjxxurl(annex);
        String add_time_name = data.getAdd_time_name();
        data.setAdd_time_name(add_time_name);
    }


    /**
     * 获取附件
     *
     * @param parse
     * @return
     */
    protected String getAnnex(Document parse) {
        List<String> pdfList = new ArrayList<String>();
        try {
            Elements pdfs = parse.select("a");
            for (Element pdf : pdfs) {
                String href = pdf.attr("href");
                if (href.contains(".pdf") || href.contains(".doc") || href.contains(".xlsx") || href.contains(".xls") || href.contains(".zip") || href.contains(".jpg")) {
                    if (href.startsWith("http")) {
                        pdfList.add(href);
                    }
                    if (href.startsWith("./")) {
                        href = href.replace("./", "");
                        String url = baseUrl.replaceAll("/index.*", "/") + href;
                        pdfList.add(url);
                    }
                }
            }
            if (pdfList.size() > 0) {
                return pdfList.toString();
            }
        } catch (Exception ignore) {
        }
        return null;
    }


    /**
     * 获取内容
     *
     * @param parse
     * @return
     */
    protected String getDetail(Document parse) {
        try {
            String content = "";
            for (String relu : this.fullcontentRelu.split("\\|")) {
                try {
                    content = content + parse.select(relu.trim()).outerHtml().replaceAll("\\'", "\"");
                } catch (Exception ignore) {
                }
            }
            return content;
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * 获取价格
     *
     * @param parse
     * @return
     */
    protected String getPrice(Document parse) {
        String price = "";
        try {
            String[] authorRelus = this.authorRelu.split("\\|");
            for (String authorRelu : authorRelus) {
                try {
                    price = parse.select(authorRelu).get(0).text();
                } catch (Exception e) {
                    logger.error(e.toString());
                }
                if (price.length() > 0 && !price.equals("")) {
                    break;
                }
            }
        } catch (Exception e) {
            logger.error(e.toString());
        }
        return price;
    }

    /**
     * 获取采购人
     *
     * @param parse
     * @return
     */
    protected String getAuthor(Document parse) {
        String author = "";
        try {
            String[] authorRelus = this.authorRelu.split("\\|");
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

    /**
     * 获取公告类型
     *
     * @param parse
     * @return
     */
    protected int getCatId(Document parse) {
        try {
            String text = parse.select(this.catIdRelu).get(0).text();
            return getCatIdByText(text);
        } catch (Exception e) {
            return -1;
        }
    }

    /**
     * 通过文本获取公告类型id
     *
     * @param text
     * @return
     */
    protected static int getCatIdByText(String text) {
        int catId = -1;

        if (text == null) {
            return catId;
        } else if (text.contains("招标公告") || text.contains("采购公告") || text.contains("公开招标") || text.contains("招标(采购)公告") || text.contains("采购需求")) {
            catId = 1;
        } else if (text.contains("询价")) {
            catId = 2;
        } else if (text.contains("竞争性谈判")) {
            catId = 3;
        } else if (text.contains("单一来源") || text.contains("单一公告")) {
            catId = 4;
        } else if (text.contains("资格预审")) {
            catId = 5;
        } else if (text.contains("邀请招标") || text.contains("邀请公告")) {
            catId = 6;
        } else if (text.contains("成交公告") || text.contains("采购结果") || text.contains("合同及验收") || text.contains("验收结果") || text.contains("结果公告")) {
            catId = 11;
        } else if (text.contains("中标") || text.contains("合同详情") || text.contains("合同公告")) {
            catId = 7;
        } else if (text.contains("招标更正") || text.contains("更正") || text.contains("政府采购意向变更") || text.contains("变更公告")) {
            catId = 8;
        } else if (text.contains("其他") || text.contains("废标")) {
            catId = 9;
        } else if (text.contains("竞争性磋商")) {
            catId = 10;
        } else if (text.contains("终止")) {
            catId = 12;
        } else if (text.contains("招标预告") || text.contains("采购需求征求意见") || text.contains("采购意向") || text.contains("需求公示") || text.contains("招标预公告")) {
            catId = 13;
        } else if (text.contains("竞价") || text.contains("网上询价") || text.contains("协议竞价")) {
            catId = 14;
        }

        return catId;
    }

    /**
     * 获取描述
     *
     * @param parse
     * @return
     */
    protected String getDescription(Document parse) {
        try {
            return parse.select(this.descriptionRelu).get(0).text();
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * 获取标题
     *
     * @param parse
     * @return
     */
    protected String getTitle(Document parse) {
        try {
            return parse.select(this.titleRelu).get(0).text();
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * 获取url
     *
     * @param element
     * @return url
     */
    protected String getUrl(Element element) {
        Element a = element.select("a").get(0);
        String href = a.attr("href");

        if (href == null || "".equals(href)) {
            return null;
        }
        if (href.startsWith("./")) {
            href = href.substring(2);
        }
        String url = null;
        if (!href.startsWith("http")) {
            url = this.baseUrl + href;
        } else {
            url = href;
        }
        return url;
    }

    /**
     * 获取发布时间
     *
     * @param element
     * @return
     */
    protected Date getAddTime(Element element) {
        Date parse = null;
        try {
            if (element.select(this.addTimeRelu).size() > 0) {
                String addTime = element.select(this.addTimeRelu).get(0).text();
                SimpleDateFormat format = new SimpleDateFormat(this.addTimeParse);
                parse = format.parse(addTime);
            } else {
                String addTime = element.select("span.docRelTime").get(0).text();
                SimpleDateFormat format = new SimpleDateFormat(this.addTimeParse);
                parse = format.parse(addTime);
            }

        } catch (Exception e) {
            logger.error("获取日期错误：" + e, e);
        }
        return parse;
    }

    /**
     * 获取列表中所有详文url
     *
     * @param parse
     * @return
     */
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
                // 获取发布时间
                long addTime = getAddTime(element).getTime();
                logger.info("addTime: " + addTime);
                if (addTime - this.deadDate.getTime() < 0) {
                    logger.info("发布时间早于截止时间， 不添加该任务url");
                    continue;
                }
                SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm");
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
