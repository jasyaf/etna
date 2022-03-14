package org.etnaframework.core.web.mapper;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.regex.Pattern;
import org.etnaframework.core.logging.Log;
import org.etnaframework.core.spring.SpringContext;
import org.etnaframework.core.spring.SpringContext.ByNameFrameworkPriorComparator;
import org.etnaframework.core.spring.annotation.OnContextInited;
import org.etnaframework.core.util.ReflectionTools;
import org.etnaframework.core.util.StringTools;
import org.etnaframework.core.util.TimeSpanStat;
import org.etnaframework.core.web.HttpEvent;
import org.etnaframework.core.web.annotation.CmdPath;
import org.etnaframework.core.web.cmd.HttpCmd;
import org.etnaframework.plugin.websocket.WebsocketCmd;
import org.slf4j.Logger;
import org.springframework.aop.framework.Advised;
import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.stereotype.Service;
import javassist.CtClass;
import javassist.CtMethod;

/**
 * <pre>
 * 根据cmd的类名和public方法名构造单个的请求URL->cmdName.methodName的映射关系
 * 本类为映射关系的集合，通过传入URL来实现鉴权和调用对应的方法等
 * </pre>
 *
 * @author BlackCat
 * @since 2014-7-23
 */
@Service
public class CmdMappers {

    protected Logger log = Log.getLogger();

    /** URL->对应方法的直接映射 */
    private static Map<String, CmdMeta> directMappers = Collections.emptyMap();

    /** 正则表达式匹配规则的映射 */
    private static List<CmdMeta> reMappers = Collections.emptyList();

    /**
     * 放置URL->cmdName.methodName关系的映射
     */
    @OnContextInited
    public void initMappers() throws Throwable {
        directMappers = new LinkedHashMap<String, CmdMeta>();
        reMappers = new ArrayList<CmdMeta>();
        List<? extends HttpCmd> beans = SpringContext.getBeansOfTypeAsList(HttpCmd.class);
        Collections.sort(beans, ByNameFrameworkPriorComparator.INSTANCE);
        CtClass heClass = ReflectionTools.getCtClass(HttpEvent.class);
        for (HttpCmd bean : beans) {
            Class<? extends HttpCmd> beanClass = bean.getClass();

            if (null != beanClass.getAnnotation(Deprecated.class)) { // 如果加了@Deprecated就不需要加入处理了
                continue;
            }

            String className = beanClass.getSimpleName();
            String cmdName = (className.endsWith(HttpCmd.DEFAULT_NAME_SUFFIX) ? className.substring(0, className.lastIndexOf(HttpCmd.DEFAULT_NAME_SUFFIX)) : className);
            cmdName = StringTools.headLetterToLowerCase(StringTools.isEmpty(cmdName) ? className : cmdName); // cmd名称要首字母小写

            // 1、获取cmd类本身对应的映射关系
            List<String> prefix = new ArrayList<>();
            CmdPath upc = beanClass.getAnnotation(CmdPath.class);
            boolean cmdOverride = false;
            if (null != upc) { // 如果类上有配置的URL信息，就读取之
                for (String pattern : upc.value()) {
                    if (StringTools.isEmpty(pattern)) {
                        throw new IllegalArgumentException("类" + beanClass.getName() + "上的@" + CmdPath.class.getSimpleName() + "映射规则不能为空");
                    }
                    if (pattern.charAt(0) != '/') {
                        throw new IllegalArgumentException("类" + beanClass.getName() + "上的@" + CmdPath.class.getSimpleName() + "映射规则必须要以/开头");
                    }
                    if (pattern.length() > 1 && pattern.charAt(pattern.length() - 1) == '/') {
                        throw new IllegalArgumentException("类" + beanClass.getName() + "上的@" + CmdPath.class.getSimpleName() + "映射规则不能以/结尾，除非是单个的/");
                    }
                    prefix.add(pattern);
                }
                cmdOverride = upc.override();
            }
            if (!cmdOverride) {
                prefix.add("/" + cmdName);
            }

            // 2、获取cmd类下的public方法对应的映射关系并添加
            CtClass cc = null;
            // 如果是AOP生成的类，需要剥离出原始的对象
            if (bean instanceof Advised) {
                Advised adv = (Advised) bean;
                try {
                    cc = ReflectionTools.getCtClass(adv.getTargetSource().getTargetClass());
                } catch (RuntimeException e) {
                    throw e;
                } catch (Exception e) {
                    throw new BeanInitializationException("从" + bean.getClass().getName() + "获取被代理的对象失败", e);
                }
            } else {
                cc = ReflectionTools.getCtClass(beanClass);
            }
            /** 枚举cmd中的所有public方法，找出参数为{@link HttpEvent}且无返回值的 */
            CtMethod[] declaredMethods = cc.getDeclaredMethods();

            if(cc.subclassOf(ReflectionTools.getCtClass(WebsocketCmd.class))){
                // 如果此类是websocketCmd的实例,获取包含父类的方法,以获取到WebsocketCmd.index
                declaredMethods=cc.getMethods();
            }

            for (CtMethod m : declaredMethods ) {
                CtClass[] types = m.getParameterTypes();
                if (Modifier.isPublic(m.getModifiers()) //
                    && types.length == 1 && types[0].equals(heClass) && m.getReturnType().equals(CtClass.voidType) && null == m.getAnnotation(Deprecated.class) // 如果加了@Deprecated就不需要加入处理了
                    ) {
                    Set<String> pathInfo = new LinkedHashSet<String>(); // 当前方法将由这些URL映射而来
                    Set<Pattern> rePattern = new LinkedHashSet<Pattern>(); // 通过正则表达式匹配的规则
                    CmdPath upm = (CmdPath) m.getAnnotation(CmdPath.class);
                    boolean methodOverride = false;
                    if (null != upm) { // 如果类上有配置的URL信息，就读取之
                        for (String pattern : upm.value()) {
                            if (StringTools.isEmpty(pattern)) {
                                throw new IllegalArgumentException("方法" + m.getLongName() + "上的@" + CmdPath.class.getSimpleName() + "不能使用空字符串");
                            }
                            if (pattern.length() > 1 && pattern.charAt(pattern.length() - 1) == '/') {
                                throw new IllegalArgumentException("方法" + m.getLongName() + "上的@" + CmdPath.class.getSimpleName() + "不能以/结尾，除非是单个的/");
                            }
                            if (pattern.charAt(0) == '/') { // 此情况下为直接匹配，无视cmd类本身的规则
                                if (pattern.endsWith("$")) {
                                    rePattern.add(compile(cc, m, "^" + pattern));
                                } else {
                                    pathInfo.add(pattern);
                                }
                            } else {
                                for (String p : prefix) {
                                    if (pattern.endsWith("$")) {
                                        rePattern.add(compile(cc, m, "^" + p + "/" + pattern));
                                    } else {
                                        pathInfo.add(p + "/" + pattern);
                                    }
                                }
                            }
                        }
                        methodOverride = upm.override();
                    }
                    if (!methodOverride) {
                        String methodName = m.getName();
                        if (HttpCmd.DEFAULT_METHOD_NAME.equals(methodName)) {
                            for (String p : prefix) {
                                pathInfo.add(p);
                            }
                        } else {
                            for (String p : prefix) {
                                pathInfo.add(p + "/" + methodName);
                            }
                        }
                    }

                    // 添加到映射关系中去
                    CmdMeta cm = CmdMeta.create(new ArrayList<String>(pathInfo), new ArrayList<Pattern>(rePattern), bean, m);
                    for (String url : pathInfo) {
                        CmdMeta old = directMappers.get(url);
                        if (null != old) {
                            throw new IllegalArgumentException(url + "已经被映射到了" + old.getName() + "，请避免冲突");
                        }
                        directMappers.put(url, cm);
                    }
                    if (!rePattern.isEmpty()) {
                        reMappers.add(cm);
                    }
                }
            }
        }
    }

    private Pattern compile(CtClass cc, CtMethod m, String pattern) {
        try {
            return Pattern.compile(pattern);
        } catch (Exception ex) {
            throw new IllegalArgumentException("方法" + m.getLongName() + "上的@" + CmdPath.class.getSimpleName() + "正则表达式编译失败，请检查格式");
        }
    }

    public String getMapperInfo() {
        StringBuilder sb = new StringBuilder();
        List<CmdMeta> list = new ArrayList<>(directMappers.values());
        sb.append("Direct Mappers:\n");
        for (CmdMeta cm : list) {
            sb.append(cm).append("\n");
        }
        sb.append("\nRegular Expression Mappers:\n");
        for (CmdMeta cm : reMappers) {
            sb.append(cm).append("\n");
        }
        return sb.toString();
    }

    /**
     * 根据传入的path去匹配对应的调用方法，如果没有对应的方法的话就返回null
     */
    public CmdMeta getCmdMetaByPath(HttpEvent he) {
        CmdMeta cm = directMappers.get(he.getRequestURI());
        if (null == cm) {
            for (CmdMeta c : reMappers) {
                if (c.matches(he)) {
                    return c;
                }
            }
        }
        return cm;
    }

    /**
     * 返回Cmd的映射关系
     */
    public Map<String, CmdMeta> getCmdMappers() {
        HashMap<String, CmdMeta> map = new HashMap<String, CmdMeta>(directMappers);
        for (CmdMeta cm : reMappers) {
            for (String p : cm.getAllPatterns()) {
                map.put(p, cm);
            }
        }
        return map;
    }

    private Map<CmdMeta, List<String>> reverseCmdAllSortedMap;

    public Map<CmdMeta, List<String>> getReverseCmdAllSortedMap() {
        if (reverseCmdAllSortedMap == null) {
            Map<CmdMeta, List<String>> tmp = new LinkedHashMap<CmdMeta, List<String>>();
            _buildReverseCmdAllSortedMap(tmp, directMappers);
            reverseCmdAllSortedMap = tmp;
        }
        return reverseCmdAllSortedMap;
    }

    private void _buildReverseCmdAllSortedMap(Map<CmdMeta, List<String>> tmp, Map<String, CmdMeta> ori) {
        for (Entry<String, CmdMeta> e : ori.entrySet()) {
            CmdMeta meta = e.getValue();
            String url = e.getKey();
            List<String> list = tmp.get(meta);
            if (list == null) {
                list = new ArrayList<String>(1);
                tmp.put(meta, list);
            }
            list.add(url);
        }
    }

    public static class StageTimeSpanStat extends TimeSpanStat {

        public StageTimeSpanStat(String name) {
            super(name, 1000, false, null);
            this.initFormat(40, 1);
        }
    }
}
