package test.cases;

import org.etnaframework.core.test.EtnaTestCase;
import org.etnaframework.core.test.annotation.TestLauncherClass;
import org.etnaframework.core.util.StringTools;
import org.etnaframework.core.util.ValidateUtils;
import org.junit.Test;
import org.springframework.stereotype.Service;
import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber.PhoneNumber;
import test.TestEtnaLaunch;

/**
 * {@link StringTools}的测试
 *
 * @author BlackCat
 * @since 2015-03-16
 */
@Service
@TestLauncherClass(TestEtnaLaunch.class)
public class TestStringTools extends EtnaTestCase {

    @Override
    protected void cleanup() throws Throwable {
    }

    @Test
    public void test001() {
        String link = StringTools.getLinkHtml("测试", "http://www.baidu.com", "word", "名侦探狄仁杰");
        String except = "<a href=\"http://www.baidu.com?word=%E5%90%8D%E4%BE%A6%E6%8E%A2%E7%8B%84%E4%BB%81%E6%9D%B0\">测试</a>";
        assertEquals(except, link);
    }

    @Test
    public void test002() {

        try {
            PhoneNumber phone = ValidateUtils.parsePhoneNumber("13431568085");
            System.err.println(phone.getCountryCode());
            System.err.println(phone.getNationalNumber());
            PhoneNumberUtil phoneUtil = PhoneNumberUtil.getInstance();
            System.err.println(phoneUtil.isPossibleNumber(phone));
            System.err.println(ValidateUtils.isMobilePhone("13431568085"));
            System.err.println(ValidateUtils.formatE164(86, "13431568085"));
        } catch (NumberParseException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    @Test
    public void test003() {

        System.err.println(ValidateUtils.isMobilePhone(86, "13657312432"));
    }

    @Test
    public void test004() {
        System.err.println("+86 18620390821".replaceAll("\\D", ""));
    }
}
