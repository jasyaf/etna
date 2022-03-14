package generator.out.vo;

import org.etnaframework.core.util.DatetimeUtils.Datetime;

/**
 * 渠道统计记录表
 *
 * @author hujiachao
 * @since 2015-08-17
 */
public class Tbl_dp_channelstat {

    /** 渠道统计记录表 */
    public Long serial;

    /** 渠道名，例如 广点通/磨盘 */
    public String channel_id;

    /** 客户端唯一标识，如iOS为idfa */
    public String client_id;

    /** 激活状态 CLICK表示用户从广告平台点击，广告平台报过来 ACTIVE表示用户打开了APP，需要给广告平台上报 */
    public String status;

    /** 创建时间 */
    public Datetime create_time;

    /** 更新时间 */
    public Datetime update_time;
}
