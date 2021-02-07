package po;

public class StructData {
    // 链接
    private String articleurl;
    // 链接md5
//    private String md5;
    // 标题
    private String title;
    // 描述 简介
    private String description;
    // 采购类型（招标， 邀标）
    private int cat_id;
    // 城市id
    private int city_id;
    // 采购人 作者
    private String author;
    // 采购价格
    private String price;
    // 发布时间
    private long add_time;
    // 内容 采集全部内容
    private String fullcontent;
    // 附件
    private String fjxxurl;

    public String getArticleurl() {
        return articleurl;
    }

    public void setArticleurl(String articleurl) {
        this.articleurl = articleurl;
    }

//    public String getMd5() {
//        return md5;
//    }
//
//    public void setMd5(String md5) {
//        this.md5 = md5;
//    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public int getCat_id() {
        return cat_id;
    }

    public void setCat_id(int cat_id) {
        this.cat_id = cat_id;
    }

    public int getCity_id() {
        return city_id;
    }

    public void setCity_id(int city_id) {
        this.city_id = city_id;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public String getPrice() {
        return price;
    }

    public void setPrice(String price) {
        this.price = price;
    }

    public long getAdd_time() {
        return add_time;
    }

    public void setAdd_time(long add_time) {
        this.add_time = add_time / 1000;
    }

    public String getFullcontent() {
        return fullcontent;
    }

    public void setFullcontent(String fullcontent) {
        this.fullcontent = fullcontent;
    }

    public String getFjxxurl() {
        return fjxxurl;
    }

    public void setFjxxurl(String fjxxurl) {
        this.fjxxurl = fjxxurl;
    }
}
