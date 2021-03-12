package site;

import com.alibaba.fastjson.JSONObject;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import po.StructData;
import start.Bidding;
import util.Util;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class CCGP_DaLian extends WebGeneral{
    private static Logger logger = LoggerFactory.getLogger(CCGP_GuiZhou.class);
    private static String relu = "";
    public static JSONObject relus = JSONObject.parseObject(relu);

    @Override
    public void run() {
        setValue();
//        bxmoreinfo.aspx?CategoryNum=003001001
//bxmoreinfo.aspx?CategoryNum=003002001
//bxmoreinfo.aspx?CategoryNum=003004001
//bxmoreinfo.aspx?CategoryNum=003005001
//bxmoreinfo.aspx?CategoryNum=003006001
//bxmoreinfo.aspx?CategoryNum=003001002
//bxmoreinfo.aspx?CategoryNum=003002002
//bxmoreinfo.aspx?CategoryNum=003004002
//bxmoreinfo.aspx?CategoryNum=003005002
//bxmoreinfo.aspx?CategoryNum=003006002
        final String[] CategoryNumS = {""},  urls = new String[CategoryNumS.length];
        this.main(urls);
        Bidding.cout.decrementAndGet();
    }

    @Override
    protected void setValue() {
        cityIdRelu = 17;
        nodeListRelu = "div.xnrx ul li";
        titleRelu = "h3";
        fullcontentRelu = "div[style=xnrx]";
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
        for (String url : urls) {
            logger.info("当前开始url： " + url);
            this.baseUrl = url.split("/dlweb")[0];
            startRun(retryTime, url, 0);
        }
    }

    @Override
    protected String getNextPageUrl(Document document, int currentPage, String httpBody, String url) {
        return super.getNextPageUrl(document, currentPage, httpBody, url);
    }

    @Override
    protected void startRun(int retryTime, String url, int currentPage) {
        super.startRun(retryTime, url, currentPage);
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
        return super.getAllResult(parse, httpBody);
    }
}
