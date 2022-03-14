package org.etnaframework.core.util;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

/**
 * 重试辅助工具类，处理方式类似spring-retry组件，但不引入AOP机制，使用更灵活
 *
 * @author BlackCat
 * @since 2017-05-10
 */
public class RetryTools {

    /**
     * 构建重试任务，参数为总尝试执行次数
     */
    public static RetryBuilder newTask(int maxAttempts) {
        return new RetryBuilder(maxAttempts);
    }

    /**
     * 抛出异常，内部自动判断是否需要包装
     */
    private static void _throwing(Throwable ex) {
        if (RuntimeException.class.isAssignableFrom(ex.getClass())) {
            throw (RuntimeException) ex;
        }
        if (Error.class.isAssignableFrom(ex.getClass())) {
            throw (Error) ex;
        }
        throw new RuntimeException(ex);
    }

    /**
     * 重试任务的构造器
     */
    public static class RetryBuilder {

        private int maxAttempts;

        private List<Class<? extends Throwable>> include;

        private List<Class<? extends Throwable>> exclude;

        private long retryDelayMs = 0L;

        private RetryBuilder(int maxAttempts) {
            this.maxAttempts = maxAttempts;
        }

        /**
         * 设置【会】触发重试机制的异常（包括其继承类），不在其中的不会触发重试
         */
        public RetryBuilder include(Class<? extends Throwable> throwable) {
            if (null != exclude) {
                throw new IllegalArgumentException("已经设置了exclude项，不能再设置include");
            }
            if (null == include) {
                include = new ArrayList<>();
            }
            include.add(throwable);
            return this;
        }

        /**
         * 设置【不会】触发重试机制的异常（包括其继承类），不在其中的会触发重试
         */
        public RetryBuilder exclude(Class<? extends Throwable> throwable) {
            if (null != include) {
                throw new IllegalArgumentException("已经设置了include项，不能再设置exclude");
            }
            if (null == exclude) {
                exclude = new ArrayList<>();
            }
            exclude.add(throwable);
            return this;
        }

        /**
         * 设置重试的时间间隔，如果不设置默认是0表示立即重试
         */
        public RetryBuilder retryDelay(int delay, TimeUnit unit) {
            this.retryDelayMs = unit.toMillis(delay);
            return this;
        }

        /**
         * 提交执行任务，无返回值的
         */
        public void process(Runnable task) {
            if (maxAttempts < 1) {
                throw new IllegalArgumentException("最大尝试次数maxAttempts必须>=1");
            }
            if (null == include && null == exclude) {
                throw new IllegalArgumentException("未设置任何include/exclude异常重试规则，如果希望任何异常都触发重试，请设置include(Throwable.class)");
            }
            for (int i = 1; i <= maxAttempts; i++) {
                try {
                    task.run();
                    return;
                } catch (Throwable ex) {
                    if (i == maxAttempts) { // 如果重试到最后一次，还是抛出异常走到了这里，就直接把异常抛出去了
                        _throwing(ex);
                    }
                    if (null != include) { // 不在include列表中的异常，直接抛出不重试
                        boolean throwing = true;
                        for (Class<? extends Throwable> c : include) {
                            if (c.isAssignableFrom(ex.getClass())) {
                                throwing = false;
                                break;
                            }
                        }
                        if (throwing) {
                            _throwing(ex);
                        }
                    }
                    if (null != exclude) { // 在exclude列表中的异常，直接抛出不重试
                        for (Class<? extends Throwable> c : exclude) {
                            if (c.isAssignableFrom(ex.getClass())) {
                                _throwing(ex);
                            }
                        }
                    }
                    if (retryDelayMs > 0) { // 有配置重试间隔的，就在间隔之后再发起下一次重试
                        try {
                            Thread.sleep(retryDelayMs);
                        } catch (Throwable ignore) {
                        }
                    }
                }
            }
        }

        /**
         * 提交执行任务，获取返回值
         */
        public <T> T process(Callable<T> task) {
            if (maxAttempts < 1) {
                throw new IllegalArgumentException("最大尝试次数maxAttempts必须>=1");
            }
            if (null == include && null == exclude) {
                throw new IllegalArgumentException("未设置任何include/exclude异常重试规则，如果希望任何异常都触发重试，请设置include(Throwable.class)");
            }
            for (int i = 1; i <= maxAttempts; i++) {
                try {
                    return task.call();
                } catch (Throwable ex) {
                    if (i == maxAttempts) { // 如果重试到最后一次，还是抛出异常走到了这里，就直接把异常抛出去了
                        _throwing(ex);
                    }
                    if (null != include) { // 不在include列表中的异常，直接抛出不重试
                        boolean throwing = true;
                        for (Class<? extends Throwable> c : include) {
                            if (c.isAssignableFrom(ex.getClass())) {
                                throwing = false;
                                break;
                            }
                        }
                        if (throwing) {
                            _throwing(ex);
                        }
                    }
                    if (null != exclude) { // 在exclude列表中的异常，直接抛出不重试
                        for (Class<? extends Throwable> c : exclude) {
                            if (c.isAssignableFrom(ex.getClass())) {
                                _throwing(ex);
                            }
                        }
                    }
                    if (retryDelayMs > 0) { // 有配置重试间隔的，就在间隔之后再发起下一次重试
                        try {
                            Thread.sleep(retryDelayMs);
                        } catch (Throwable ignore) {
                        }
                    }
                }
            }
            return null;
        }
    }
}
