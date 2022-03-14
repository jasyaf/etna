package org.etnaframework.core.util;

import java.util.regex.Pattern;
import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.NumberParseException.ErrorType;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberFormat;
import com.google.i18n.phonenumbers.Phonenumber.PhoneNumber;

/**
 * 数据验证专用工具类
 *
 * @author BlackCat
 * @since 2010-11-22
 */
public class ValidateUtils {

    private static Pattern EMAIL = Pattern.compile("\\w+([-.]\\w+)*@\\w+([-]\\w+)*\\.(\\w+([-]\\w+)*\\.)*[a-z,A-Z]{2,3}");

    private static Pattern MD5_32 = Pattern.compile("^[0-9a-zA-Z]{32}$");

    private static Pattern MOBILE = Pattern.compile("\\d{1,11}");

    private static Pattern MAC = Pattern.compile("^([A-F0-9]{2}(-[A-F0-9]{2}){5})|([A-F0-9]{2}(:[A-F0-9]{2}){5})$");

    private static Pattern IP = Pattern.compile("^((\\d|[1-9]\\d|1\\d\\d|2[0-4]\\d|25[0-5]|[*])\\.){3}(\\d|[1-9]\\d|1\\d\\d|2[0-4]\\d|25[0-5]|[*])$");

    /**
     * 判断mac地址是否正确 00:00:00:00:00:00 或者是00-00-00-00-00-00
     */
    public static boolean isValidMAC(String mac) {
        if (StringTools.isNotEmpty(mac)) {
            return MAC.matcher(mac).matches();
        }
        return false;
    }

    /**
     * 判断IP地址是否正确
     */
    public static boolean isValidIP(String ip) {
        if (StringTools.isNotEmpty(ip)) {
            return IP.matcher(ip).matches();
        }
        return false;
    }

    /**
     * 判断是否是合法的32位md5值
     */
    public static boolean isValidMD5(String md5) {
        if (StringTools.isNotEmpty(md5)) {
            return MD5_32.matcher(md5).matches();
        }
        return false;
    }

    /**
     * 判断是否不是合法的32位md5值
     */
    public static boolean isNotValidMD5(String md5) {
        return !isValidMD5(md5);
    }

    /**
     * 判断是否是合法的email地址
     */
    public static boolean isValidEmail(String email) {
        if (StringTools.isNotEmpty(email)) {
            return EMAIL.matcher(email).matches();
        }
        return false;
    }

    /**
     * 判断是否不是合法的email地址
     */
    public static boolean isNotValidEmail(String email) {
        return !isValidEmail(email);
    }

    /**
     * 验证电话号码是否有效(根据国际电信E164标准，例：+8618620390821或18620390821)
     */
    public static boolean isMobilePhone(int country_code, long nationalNumber) {
        PhoneNumberUtil phoneUtil = PhoneNumberUtil.getInstance();
        return phoneUtil.isValidNumber(parsePhoneNumber(country_code, nationalNumber));
    }

    public static boolean isMobilePhone(int country_code, String phoneNumber) {
        Long num = StringTools.getLong(phoneNumber, null);
        if (null == num) { // 防止无法转为long（例如含有非法字符，数字过长等）导致报错
            return false;
        }
        return isMobilePhone(country_code, num);
    }

    /**
     * 验证电话号码是否有效(根据国际电信E164标准，例：+8618620390821或18620390821)
     */
    public static boolean isMobilePhone(String phoneNumber) {
        PhoneNumber phone;
        try {
            phone = parsePhoneNumber(phoneNumber);
            return isMobilePhone(phone.getCountryCode(), phone.getNationalNumber());
        } catch (NumberParseException e) {
            return false;
        }
    }

    /**
     * 验证电话号码是否有效(根据国际电信E164标准，例：+8618620390821或18620390821)与isMobilePhone方法相反
     */
    public static boolean isNotMobilePhone(String phoneNumber) {
        return !isMobilePhone(phoneNumber);
    }

    /**
     * 验证电话号码是否有效(根据国际电信E164标准，例：+8618620390821或18620390821)与isMobilePhone方法相反
     */
    public static boolean isNotMobilePhone(int country_code, long nationalNumber) {
        return !isMobilePhone(country_code, nationalNumber);
    }

    /**
     * 通用电话号码（如果不自带国家码，默认为CN规则）
     */
    public static PhoneNumber parsePhoneNumber(String phoneNumber) throws NumberParseException {
        if (StringTools.isNotEmpty(phoneNumber)) {
            if (phoneNumber.startsWith("+") || MOBILE.matcher(phoneNumber).matches()) {
                PhoneNumberUtil phoneUtil = PhoneNumberUtil.getInstance();
                if (phoneNumber.startsWith("+")) {
                    return phoneUtil.parse(phoneNumber, "");
                }
                return phoneUtil.parse(phoneNumber, "CN");
            }
        }
        throw new NumberParseException(ErrorType.NOT_A_NUMBER, "格式不对");
    }

    public static PhoneNumber parsePhoneNumber(int country_code, long phoneNumber) {
        return new PhoneNumber().setCountryCode(country_code).setNationalNumber(phoneNumber);
    }

    /**
     * 将我们风格的国家编码转换成国际编码
     */
    public static int convertNationalCode(String country_code) {
        return Integer.parseInt(country_code.replaceAll("\\D", ""));
    }

    /**
     * 将国际编码转换成我们要输出的编码
     */
    public static String convertCountryCode(int nationalCode) {
        return "+" + nationalCode;
    }

    /**
     * 将国家码与电话号码格式化成E164标准电话号码
     **/
    public static String formatE164(int country_code, String phoneNumber) {
        try {
            PhoneNumberUtil phoneUtil = PhoneNumberUtil.getInstance();
            return phoneUtil.format(parsePhoneNumber(country_code, Long.parseLong(phoneNumber)), PhoneNumberFormat.E164);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /**
     * 将国家码与电话号码格式化成0086电话号码
     **/
    public static String formatNumber(int country_code, String phoneNumber) {
        try {
            PhoneNumberUtil phoneUtil = PhoneNumberUtil.getInstance();

            return phoneUtil.format(parsePhoneNumber(country_code, Long.parseLong(phoneNumber)), PhoneNumberFormat.E164).replace("+", "00");
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /**
     * 将国家码与电话号码格式化成INTERNATIONAL标准电话号码
     **/
    public static String formatInterNational(int country_code, String phoneNumber) {
        try {
            PhoneNumberUtil phoneUtil = PhoneNumberUtil.getInstance();
            return phoneUtil.format(parsePhoneNumber(country_code, Long.parseLong(phoneNumber)), PhoneNumberFormat.INTERNATIONAL);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /**
     * 将国家码与电话号码格式化成NATIONAL标准电话号码
     **/
    public static String formatNATIONAL(int country_code, String phoneNumber) {
        try {
            PhoneNumberUtil phoneUtil = PhoneNumberUtil.getInstance();
            return phoneUtil.format(parsePhoneNumber(country_code, Long.parseLong(phoneNumber)), PhoneNumberFormat.NATIONAL);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /**
     * 将国家码与电话号码格式化成RFC3966标准电话号码
     **/
    public static String formatInTerRFC3966(int country_code, String phoneNumber) {
        try {
            PhoneNumberUtil phoneUtil = PhoneNumberUtil.getInstance();
            return phoneUtil.format(parsePhoneNumber(country_code, Long.parseLong(phoneNumber)), PhoneNumberFormat.RFC3966);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    public static void main(String[] args) {

        System.err.println(isValidMAC("00:00:00:00:00:00"));
        System.err.println(isValidMAC("00-00-00-00-00-00"));
        System.err.println(isValidMAC("00:00:00:00:H0:00"));
        System.err.println(isValidMAC("00-00-00-k0-00-00"));
    }
}
