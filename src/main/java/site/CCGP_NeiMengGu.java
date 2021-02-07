package site;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import po.StructData;
import start.Bidding;
import util.Util;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class CCGP_NeiMengGu extends WebGeneral {
    private static Logger logger = LoggerFactory.getLogger(CCGP_NeiMengGu.class);

    @Override
    protected void setValue() {
        titleRelu = "div.title-box p";
        // 描述规则
        descriptionRelu = "";
        // 采集类型id规则
        catIdRelu = "div.title-box p";
        // 内容规则
        fullcontentRelu = "div#content-box-1";
        // 列表url节点规则
        nodeListRelu = "div.index07_07_02 ul li";
        // 城市代码
        cityIdRelu = 15;

        addTimeRelu = "span em";
    }

    @Override
    public void run() {
        // 获取任务url
        setValue();
        String[] urls = Bidding.properties.getProperty("ccgp.neimenggu.url").split(",");
        this.main(urls);
        Bidding.cout.decrementAndGet();
    }

    @Override
    protected String getNextPageUrl(Document document, int currentPage, String httpBody, String url) {
        String nextPageUrl = "";
        try {
            String id = Util.match("byf_page=(\\d+)", url)[1];
            nextPageUrl = url.replaceAll("byf_page=\\d+", "byf_page=" + (Integer.parseInt(id) + 1));
        } catch (Exception ignore) {
        }
        logger.info("nextPageUrl: " + nextPageUrl);
        return nextPageUrl;
    }

    @Override
    protected List<StructData> getAllResult(Document parse, String httpBody) {
        List<StructData> allResults = new ArrayList<StructData>();
        try {
            JSONArray rows = JSONArray.parseArray(httpBody).getJSONArray(0);
            for (int i = 0; i < rows.size(); i++) {
                logger.info("===========================================");
                JSONObject jo = rows.getJSONObject(i);
                StructData resultData = new StructData();
                try {
                    String wpMarkId = jo.getString("wp_mark_id");
                    String ayTableTag = jo.getString("ay_table_tag");
                    String type = jo.getString("type");
                    String url = "http://www.nmgp.gov.cn/category/cggg?tb_id=" + ayTableTag + "&p_id=" + wpMarkId + "&type=" + type;
                    logger.info("url: " + url);
                    resultData.setArticleurl(url);
                    String md5 = Util.stringToMD5(url);
                    logger.info("md5: " + md5);
                    //resultData.setMd5(md5);
                    if (Util.isMatch("type_name=1", baseUrl)) {
                        logger.info("catId: " + 1);
                        resultData.setCat_id(1);
                    } else if (Util.isMatch("type_name=2", baseUrl)) {
                        logger.info("catId: " + 8);
                        resultData.setCat_id(8);
                    } else if (Util.isMatch("type_name=3", baseUrl)) {
                        logger.info("catId: " + 11);
                        resultData.setCat_id(11);
                    } else if (Util.isMatch("type_name=4", baseUrl)) {
                        logger.info("catId: " + 8);
                        resultData.setCat_id(8);
                    } else if (Util.isMatch("type_name=5", baseUrl)) {
                        logger.info("catId: " + 9);
                        resultData.setCat_id(9);
                    } else if (Util.isMatch("type_name=6", baseUrl)) {
                        logger.info("catId: " + 5);
                        resultData.setCat_id(5);
                    } else if (Util.isMatch("type_name=7", baseUrl)) {
                        logger.info("catId: " + 8);
                        resultData.setCat_id(8);
                    } else if (Util.isMatch("dyly", baseUrl)) {
                        logger.info("catId: " + 4);
                        resultData.setCat_id(4);
                    } else if (baseUrl.contains("tender-list")) {
                        logger.info("catID: " + 14);
                        resultData.setCat_id(14);
                    }

                } catch (Exception e) {
                    continue;
                }
                try {
                    String subdate = jo.getString("SUBDATE");
                    subdate = Util.match("\\d+-\\d+-\\d+", subdate)[0];
                    SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
                    long addTime = format.parse(subdate).getTime();
                    logger.info("addTime: " + addTime);
                    if (addTime - this.deadDate.getTime() < 0) {
                        logger.info("发布时间早于截止时间， 不添加该任务url");
                        continue;
                    }
                    resultData.setAdd_time(addTime);
                } catch (Exception ignore) {
                }

                logger.info("cityId: " + this.cityIdRelu);
                resultData.setCity_id(this.cityIdRelu);
                allResults.add(resultData);
            }
        } catch (Exception e) {
            logger.error("获取json失败：" + e, e);
        }
        return allResults;
    }

    @Override
    protected void extract(Document parse, StructData data, String pageSource) {
        logger.info("==================================");
        String title = getTitle(parse);
        logger.info("title: " + title);
        data.setTitle(title);
        String description = getDescription(parse);
        logger.info("description: " + description);
        data.setDescription(description);
        int cityId = cityIdRelu;
        logger.info("cityId: " + cityId);
        data.setCity_id(cityId);

        String price = getPrice(parse);
        logger.info("price: " + price);
        data.setPrice(price);
        String detail = getDetail(parse);
        logger.info("detail: " + detail);
        data.setFullcontent(detail);
        String annex = getAnnex(parse);
        logger.info("annex: " + annex);
        data.setFjxxurl(annex);
    }
}
