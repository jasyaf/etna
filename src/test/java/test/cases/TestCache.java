package test.cases;

import java.util.concurrent.TimeUnit;
import org.etnaframework.core.test.EtnaTestCase;
import org.etnaframework.core.test.annotation.TestLauncherClass;
import org.etnaframework.core.util.ThreadUtils;
import org.junit.Test;
import org.springframework.stereotype.Service;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.cache.RemovalListener;
import com.google.common.cache.RemovalNotification;
import test.TestEtnaLaunch;

/**
 * 测试guava自带的缓存类功能
 *
 * @author BlackCat
 * @since 2015-06-29
 */
@Service
@TestLauncherClass(TestEtnaLaunch.class)
public class TestCache extends EtnaTestCase {

    static class InnerData {

        public String name;
    }

    private LoadingCache<String, InnerData> cache = CacheBuilder.newBuilder().maximumSize(1).expireAfterWrite(10, TimeUnit.SECONDS).removalListener(new RemovalListener<String, InnerData>() {

        public void onRemoval(RemovalNotification<String, InnerData> notification) {
            System.out.println("onRemoval:" + notification.getKey());
        }
    }).build(new CacheLoader<String, InnerData>() {

        @Override
        public InnerData load(String key) throws Exception {
            System.out.println("load");
            return null;
        }
    });

    @Override
    protected void cleanup() throws Throwable {
    }

    @Test
    public void test001() throws Throwable {
        InnerData data = cache.getIfPresent("bobo");
        System.out.println(data);
        cache.put("caicai", new InnerData());
        data = cache.get("caicai");
        System.out.println(data);
        ThreadUtils.sleep(2000);
        data = cache.get("caicai");
        System.out.println(data);
        cache.put("caicai", new InnerData());
        cache.put("caicai2", new InnerData());
    }
}
