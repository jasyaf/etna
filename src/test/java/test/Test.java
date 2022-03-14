package test;

import java.util.Date;
import org.etnaframework.core.util.DatetimeUtils.Datetime;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;

public class Test {

    public static void main(String[] args) {
        /*
         * List<Member> members = Lists.newArrayList(); for (int i = 0; i < 100; i++) { Member m = new Member(); m.member_id = UUID.randomUUID().toString(); members.add(m); }
         */

        // members.parallelStream().map(m -> m.member_id).collect(Collectors.toList()).forEach(System.out ::println);

        String s = "{ \"user_id\":\"CN0058_USER_2015052223021844388\"," + " \"token\":\"ed9ebcc433e5defc8ffb97f2d9aa6454\"," + "            \"country_code\":\"86\"," + "            \"tel\":\"13800138000\"," + "            \"email\":\"monitor@buga.cn\"," + "            \"nick_name\":\"邪教\"," + "            \"header_pic_url\":\"http://www.bugahome1.net/a0db412a0ba1324108d720cd2744a52f/1fb70e2afa79b8234d11084048f8f3ee.jpg\"," + "            \"birthday\":\"2015-06-30\"," + "            \"sex\":\"MALE\"," + "            \"home_host\":\"192.168.1.199\"," + "            \"http_port\":\"8200\"," + "            \"tcp_port\":\"22457\"," + "            \"image_url_prefix\":\"http://www.bugahome1.net/\"," + "            \"inviteNotify\":\"\"}";

        JSONObject object = JSON.parseObject(s);
        // 利用键值对的方式获取到值
        //System.out.println(object.toJSONString());

        String ss = "23232323";

        System.out.println(" ss {} {} " + ss.getClass().equals(String.class));
        System.out.println(" ss len {} {} " + (ss.length() < 7));

        Integer s1 = 921;
        int s2 = 23;

        System.out.println(" s1 s2 {} {} " + s1.getClass().equals(Integer.class));

        Short s3 = 21;
        short s4 = 33;

        System.out.println(" s3 {} {} " + s3.getClass().equals(Integer.class));
        System.out.println(" s3dd {} {} " + (s3.shortValue() < 33));

        Float f = 23.2f;
        float ff = 231.2f;

        Double d = 32.32;
        double dd = 322.43;

        char c = '2';
        Character cc = 'a';

        System.out.println(" cc {} {} " + cc.getClass().equals(Character.class));
        System.out.println(" cdd {} {} " + (cc.charValue() > 4) + "     " + (cc.charValue() < 4) + "  333 " + cc.charValue() + "    333666 " + ((cc + "").length() == 1));

        Long l = 2232323l;
        long ll = 2323;

        Byte b = 23;
        byte bb = 23;

        System.out.println(" b {} {} " + b.getClass().equals(Byte.class));
        System.out.println(" s3dd {} {} " + (b.byteValue() < 33));

        Boolean bl = true;
        boolean bll = true;

        Date ddd = new Date();
        Datetime dt = new Datetime();
    }
}
