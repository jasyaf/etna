package org.etnaframework.core.web.mapper;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.etnaframework.core.logging.Log;
import org.etnaframework.core.util.ReflectionTools;
import org.etnaframework.core.web.HttpEvent;
import org.etnaframework.core.web.cmd.HttpCmd;
import org.etnaframework.core.web.mapper.CmdMappers.StageTimeSpanStat;
import org.slf4j.Logger;
import com.alibaba.fastjson.annotation.JSONField;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtField;
import javassist.CtMethod;
import javassist.CtNewMethod;

/**
 * <pre>
 * 根据cmd的类名和public方法名构造单个的请求URL->cmdName.methodName的映射关系
 * 本类为单个映射关系的描述信息，用于通过URL映射关系调用对应的方法
 * 以及保存访问统计信息和权限控制信息等
 * </pre>
 *
 * @author BlackCat
 * @since 2014-7-23
 */
public abstract class CmdMeta {

    protected static final Logger log = Log.getLogger();

    @JSONField(serialize = false, deserialize = false)
    private static CtClass cmdMetaCtClass = ReflectionTools.getCtClass(CmdMeta.class);

    /**
     * <pre>
     * 经测试发现，在另一个classloader加载的类是无法处理其他classloader里面的类的，会报错frozen class (cannot edit)
     * 为了增加etna健壮性，当无法使用javassist方式生成类时，就使用传统的反射方式来实现
     * </pre>
     */
    private static boolean useReflect = false;

    /** 映射名称，为cmdName.methodName，用于区分展示 */
    private String name;

    /** 表明有哪些URL映射到了该方法 */
    private List<String> pathInfo;

    /** 通过正则表达式规则匹配的映射关系 */
    private List<Pattern> patterns = Collections.emptyList();

    /** 具体执行的方法 */
    @JSONField(serialize = false, deserialize = false)
    private CtMethod method;

    private StageTimeSpanStat stat;

    /**
     * 每次业务操作时间,单位秒, <0 表示此命令直接Disable,0表示不超时,>0 指具体超时秒数
     */
    private int timeout;

    /**
     * 执行URL对应的cmdName.methodName并处理结果
     */
    public abstract void invoke(HttpEvent he) throws Throwable;

    /**
     * 生成调用cmd中指定方法的CmdMeta
     */
    static CmdMeta create(List<String> pathInfo, List<Pattern> patterns, final HttpCmd cmd, CtMethod method) throws Throwable {
        if (!useReflect) {
            try {
                ClassPool pool = cmdMetaCtClass.getClassPool();
                String genClassName = cmd.getClass().getName() + "." + method.getName() + "." + cmdMetaCtClass.getSimpleName(); // 生成的class名称，使用cmd.method.CmdMeta来命名
                CtClass mc = pool.makeClass(genClassName);
                mc.setSuperclass(cmdMetaCtClass);
                // 增加一个对cmd的引用，方便在invoke中调用
                String src = "private " + cmd.getClass().getName() + " cmd;";
                mc.addField(CtField.make(src, mc));
                src = "public void invoke(" + HttpEvent.class.getName() + " he) throws Throwable {" + "return cmd." + method.getName() + "(he);" + "}";
                mc.addMethod(CtNewMethod.make(src, mc));
                // 实例化，然后通过反射将cmd引用传入进去
                CmdMeta cm = (CmdMeta) mc.toClass().newInstance();
                Field f = cm.getClass().getDeclaredField("cmd");
                f.setAccessible(true);
                f.set(cm, cmd);
                cm.name = cmd.getClass().getSimpleName() + "." + method.getName();
                cm.pathInfo = pathInfo;
                cm.patterns = patterns;
                cm.method = method;
                cm.resetCounter(); // 建创的时候重置计数器
                return cm;
            } catch (Throwable re) {
                log.error("javassist cannot create, use reflection instead");
                useReflect = true;
            }
        }
        final Method m = cmd.getClass().getMethod(method.getName(), HttpEvent.class);
        CmdMeta cm = new CmdMeta() {

            @Override
            public void invoke(HttpEvent he) throws Throwable {
                m.invoke(cmd, he);
            }
        };
        cm.name = cmd.getClass().getSimpleName() + "." + method.getName();
        cm.pathInfo = pathInfo;
        cm.patterns = patterns;
        cm.method = method;
        cm.resetCounter();
        return cm;
    }

    /**
     * 判断URL是否符合正则表达式定义，符合规定就自动解析并返回true，否则返回false
     */
    public boolean matches(HttpEvent he) {
        for (Pattern p : patterns) {
            Matcher m = p.matcher(he.getRequestURI());
            if (m.matches()) {
                he.initRESTfulMatcher(m);
                return true;
            }
        }
        return false;
    }

    /**
     * 返回全部的正则表达式URL匹配规则信息
     */
    public List<String> getAllPatterns() {
        if (patterns.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> list = new ArrayList<String>(patterns.size());
        for (Pattern p : patterns) {
            list.add(p.toString());
        }
        return list;
    }

    public boolean isDisable() {
        return timeout < 0;
    }

    public StageTimeSpanStat getStat() {
        return stat;
    }

    public void setStat(StageTimeSpanStat stat) {
        this.stat = stat;
    }

    public String getName() {
        return name;
    }

    public CtMethod getMethod() {
        return method;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((method == null) ? 0 : method.hashCode());
        return result;
    }

    public int getTimeout() {
        return timeout;
    }

    /**
     * 重置计数器
     */
    public void resetCounter() {
        stat = new StageTimeSpanStat(name);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        CmdMeta other = (CmdMeta) obj;
        if (method == null) {
            if (other.method != null) {
                return false;
            }
        } else if (!method.equals(other.method)) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "CmdMeta [name=" + name + ", pathInfo=" + pathInfo + ", patterns=" + patterns + ", method=" + method + ", stat=" + stat + ", timeout=" + timeout + "]";
    }
}
