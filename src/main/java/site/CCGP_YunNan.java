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
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

import static util.Download.getHttpBody;

public class CCGP_YunNan extends WebGeneral {
    private static Logger logger = LoggerFactory.getLogger(CCGP_YunNan.class);


    @Override
    protected void setValue() {
        titleRelu = "div.div_hui+div";
        // 描述规则
        descriptionRelu = "";
        // 采集类型id规则
        catIdRelu = "div.div_hui";
        // 城市id规则
        cityIdRelu = 2;
        // 采购人规则
        authorRelu = "";
        // 价格规则
        priceRelu = "";
        // 发布时间规则
        addTimeRelu = "span.datetime";
        // 发布时间匹配规则
        addTimeParse = "yyyy-MM-dd";
        // 内容规则
        fullcontentRelu = "div.table|div.panel.panel-default|form#institutionForm5";
        // 附件规则
        fjxxurlRelu = "table#queryTable a";
    }

    @Override
    public void run() {
        setValue();
        String[] urls = Bidding.properties.getProperty("ccgp.yunnan.url").split(",");
        this.main(urls);
        Bidding.cout.decrementAndGet();
    }

    @Override
    protected List<StructData> getAllResult(Document parse, String httpBody) {
        List<StructData> datas = new ArrayList<StructData>();
        JSONObject jo = JSONObject.parseObject(httpBody);
        JSONArray rows = jo.getJSONArray("rows");
        for (int i = 0; i < rows.size(); i++) {
            StructData data = new StructData();
            JSONObject jsonObject = rows.getJSONObject(i);
            String finishday = jsonObject.getString("finishday");
            data.setAdd_time_name(finishday);
            String bulletintitle = jsonObject.getString("bulletintitle");
            data.setTitle(bulletintitle);
            String bulletinclasschina = jsonObject.getString("bulletinclasschina");
            int catIdByText = getCatIdByText(bulletinclasschina);
            data.setCat_id(catIdByText);
            data.setCity_id(this.cityIdRelu);
            String bulletin_id = jsonObject.getString("bulletin_id");
            String url = "http://www.yngp.com/bulletin_zz.do?method=shownotice&bulletin_id=" + bulletin_id;
            data.setArticleurl(url);
            datas.add(data);
            logger.info("title: " + data.getTitle());
            logger.info("catid: " + data.getCat_id());
        }
        return datas;
    }

    @Override
    protected void extract(Document parse, StructData data, String pageSource) {
        logger.info("==================================");
        String detail = getDetail(parse);
        logger.info("detail: " + detail);
        data.setFullcontent(detail);
    }


    @Override
    protected String getNextPageUrl(Document document, int currentPage, String httpBody, String url) {
        String nextPageUrl = "";
        try {
            String id = Util.match("current=(\\d+)", url)[1];
            nextPageUrl = url.replaceAll("current=\\d+", "current=" + (Integer.parseInt(id) + 1));

        } catch (Exception ignore) {
        }
        logger.info("nextPageUrl: " + nextPageUrl);
        return nextPageUrl;
    }
}
