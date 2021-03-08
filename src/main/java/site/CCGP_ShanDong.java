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
import java.util.*;

import static util.Download.getHttpBody;

public class CCGP_ShanDong extends WebGeneral {
    private static Logger logger = LoggerFactory.getLogger(CCGP_ShanDong.class);
    private static String relu = "{\"province\": {'2500': {'authorRelu': 'div.info midea:eq(1)', 'priceRelu': 'tbody tr td:eq(3)', 'price_unit': '万元'},\n" +
            "                   '0301': {'authorRelu': 'td:containsOwn(采购人)', 'priceRelu': 'tbody tr td:eq(4)', 'price_unit': '万元'},\n" +
            "                   '2102': {'authorRelu': 'td:containsOwn(采购人)', 'priceRelu': 'td:containsOwn(预算金额)'},\n" +
            "                   '0305': {'authorRelu': 'td:containsOwn(采购人)', 'priceRelu': ''},\n" +
            "                   '0302': {'authorRelu': 'td:containsOwn(采购人)', 'priceRelu': 'tbody tr td:eq(4)', 'price_unit': '元'},\n" +
            "                   '0306': {'authorRelu': 'td:containsOwn(采购人)', 'priceRelu': ''},\n" +
            "                   '2502': {'authorRelu': 'td:containsOwn(采购人)', 'priceRelu': 'td:containsOwn(合同金额)'}\n" +
            "                   },\n" +
            "      \"city\": {'2504': {'authorRelu': 'h1.title', 'priceRelu': 'tbody tr td:eq(3)|td:containsOwn(预算金额)', 'price_unit': '万元'},\n" +
            "               '0303': {'authorRelu': 'td:containsOwn(采购人)', 'priceRelu': 'tbody tr td:eq(4)', 'price_unit': '万元'},\n" +
            "               '2106': {'authorRelu': 'td:containsOwn(采购人)', 'priceRelu': 'td:containsOwn(预算金额)', 'price_unit': '万元'},\n" +
            "               '0305': {'authorRelu': 'td:containsOwn(采购人)', 'priceRelu': ''},\n" +
            "               '0304': {'authorRelu': 'td:containsOwn(采购人)', 'priceRelu': 'tbody tr td:eq(4)', 'price_unit': '元'},\n" +
            "               '0306': {'authorRelu': 'td:containsOwn(采购人)', 'priceRelu': ''},\n" +
            "               '2505': {'authorRelu': 'td:containsOwn(采购人)', 'priceRelu': 'td:containsOwn(合同金额)'}\n" +
            "               }}";
    public static JSONObject relus = JSONObject.parseObject(relu);

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
            this.baseUrl = url;
            startRun(retryTime, url, 0);
        }
    }

    // {'省意向公开': '2500', '省采购公告': '0301', '单一来源公示': '2102', '信息更正': '0305', '省结果公告': '0302', '废标公告': '0306', '省合同公开': '2502'}
    @Override
    public void run() {
        setValue();
        String url = "http://www.ccgp-shandong.gov.cn/sdgp2017/site/listnew.jsp";
        final String[] colcodes = {"2500", "0301", "2102", "0305", "0302", "0306", "2502"}, citys = {"2504", "0303", "2106", "0305", "0304", "0306", "2505"};
        Map<String, List<String>> test = new HashMap<String, List<String>>(){{put("province", Arrays.asList(colcodes));put("city", Arrays.asList(citys));}};
        List<String> urls = new ArrayList<String>();
        for (String key:test.keySet()) {
            for (String colcode:test.get(key)) {
                urls.add(url.concat("&#44subject=&pdate=".concat("&kindof=&unitname=&projectname=&projectcode=&colcode=".concat(colcode).concat("&curpage=1&grade=".concat(key).concat("&firstpage=1")))));
            }
        }
        this.main(urls.toArray(new String[urls.size()]));
        Bidding.cout.decrementAndGet();
    }

    @Override
    protected void setValue() {
        cityIdRelu = 9;
        nodeListRelu = "ul.news_list2 li";
        titleRelu = "h1.title";
        fullcontentRelu = "div#textarea";
        addTimeRelu = "div.info midea:eq(0)";
        addTimeParse = "yyyy年MM月dd日 HH时mm分ss秒";
    }

    @Override
    protected int getMaxPageSize(Document document) {
        return super.getMaxPageSize(document);
    }

    protected String getNextPageUrl(int currentPage, String url) {
        String nextPageUrl = url.replaceAll("curpage=(\\d+)", "curpage=" + (currentPage + 1));
        return nextPageUrl;
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
                    currentPage = Integer.parseInt(Util.match("curpage=(\\d+)&", url)[1]);
                } catch (Exception ignore) {
                }
                String nextPageUrl = getNextPageUrl(currentPage, url);
                if (nextPageUrl != null && (!"".equals(nextPageUrl))) {
                    startRun(retryTime, nextPageUrl, (currentPage + 1));
                }
            } catch (Exception e) {
                logger.error("下一页提取错误：" + e, e);
            }
        }
    }

    @Override
    protected int getCatId(Document parse) {
        try {
            return getCatIdByText(parse.text());
        } catch (Exception e) {
            return -1;
        }
    }

    protected String getDetail(Document parse) {
        try {
            return parse.select(fullcontentRelu).get(0).text();
        } catch (Exception e) {
            return "";
        }
    }

    @Override
    protected void extract(Document parse, StructData data, String pageSource) {
        logger.info("==================================");
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm");
        String start_str = "./listnew.jsp?colcode=", grade_str = "grade=";
        int begin_index = pageSource.indexOf(start_str) + start_str.length();
        String colcode = pageSource.substring(begin_index, begin_index + 4);
        begin_index = pageSource.indexOf(grade_str) + grade_str.length();
        String grade = pageSource.substring(begin_index, begin_index + 4);
        grade = relus.containsKey(grade)? grade: (String) relus.keySet().toArray()[0];
        Date date = getAddTime(parse);
        data.setAdd_time(date.getTime());
        String title = getTitle(parse);
        logger.info("title: " + title);
        data.setTitle(title);
        String description = data.getTitle();
        logger.info("description: " + description);
        data.setDescription(description);
        int catId = getCatId(parse);
        logger.info("catId: " + catId);
        data.setCat_id(catId);
        int cityId = cityIdRelu;
        logger.info("cityId: " + cityId);
        data.setCity_id(cityId);
        String author = getAuthor(parse, colcode, grade);
        logger.info("author: " + author);
        data.setAuthor(author);
        String price = getPrice(parse, colcode, grade);
        logger.info("price: " + price);
        data.setPrice(price);
        String detail = getDetail(parse);
        logger.info("detail: " + detail);
        data.setFullcontent(detail);
        String annex = getAnnex(parse);
        logger.info("annex: " + annex);
        data.setFjxxurl(annex);
        String add_time_name = format.format(date.getTime());
        data.setAdd_time_name(add_time_name);
    }

    protected String getPrice(Document parse, String colcode, String grade) {
        try {
            double price = 0;
            String price_str = null, break_where = "no";
            for (String price_relu:relus.getJSONObject(grade).getJSONObject(colcode).get("priceRelu").toString().split("\\|")) {
                Elements els = parse.select(price_relu);
                switch (els.size()){
                    case 0 :
                        continue;
                    case 1 :
                        price_str = els.get(0).text();
                        break_where = "yes";
                        break;
                    case 2 :
                        price_str = els.get(1).text();
                        break_where = "yes";
                        break;
                    default :
                        for (int i = 1; i < els.size(); i++) {
                            price = price + Double.parseDouble(els.get(i).text().replaceAll("/", ""));
                        }
                        price_str = String.valueOf(price);
                }
                if (break_where.equals("yes")) {
                    break;
                }
            }
            price_str = price_str.indexOf("万元") != -1 || price_str.indexOf("元") != -1? price_str: price_str + relus.getJSONObject(grade).getJSONObject(colcode).get("price_unit").toString();
            if (price_str.indexOf("：") !=-1){
                String[] arrs = price_str.split("：");
                price_str = arrs[arrs.length - 1];
            }
            return price_str;
        } catch (Exception e) {
            return "";
        }
    }

    protected String getAuthor(Document parse, String colcode, String grade) {
        try {
            Calendar date = Calendar.getInstance();
            String year = String.valueOf(date.get(Calendar.YEAR));
            String author = parse.select(relus.getJSONObject(grade).getJSONObject(colcode).get("authorRelu").toString()).get(0).text();
            author = author.indexOf("发布人") != -1? author.split("：")[1]: author;
            author = author.indexOf("采购人") != -1? author.split("：")[1]: author;
            author = author.indexOf("地址") != -1? author.split(" ")[0]: author;
            author = author.indexOf(year) != -1? author.split(year)[0]: author;
            author = parse.text().indexOf("采购单位") != -1? parse.select("td:containsOwn(采购单位)").get(0).text().split("：")[1]: author;
            return author;
        } catch (Exception e) {
            return "";
        }
    }

    @Override
    protected String getUrl(Element element) {
        String baseUrl = "http://www.ccgp-shandong.gov.cn";
        try {
            return baseUrl + element.select("a").get(0).attr("href");
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "";
    }

    protected Date getAddTime(Element element) {
        Date parse = null;
        try {
            String addTime = element.select(this.addTimeRelu).get(0).text();
            if (addTime.indexOf("发布时间") != -1) {
                addTime = addTime.split("：")[1];
            }
            SimpleDateFormat format = new SimpleDateFormat(this.addTimeParse);
            parse = format.parse(addTime);
        } catch (Exception e) {
            logger.error("获取日期错误：" + e, e);
        }
        return parse;
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
                // 获取发布时间
                String hits = element.select("span.hits").get(0).text();
                SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
                Date date = format.parse(hits);
                long addTime = date.getTime();
                logger.info("addTime: " + addTime);
                if (addTime - this.deadDate.getTime() < 0) {
                    logger.info("发布时间早于截止时间， 可以停止采集了");
                    break;
                }
                allResults.add(resultData);
            } catch (Exception e) {
                logger.error("提取链接错误：" + e, e);
            }
        }
        return allResults;
    }
}
