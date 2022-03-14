package org.etnaframework.plugin.doc;

import java.util.Collection;
import java.util.Comparator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import org.etnaframework.core.spring.annotation.OnContextInited;
import org.etnaframework.core.util.CollectionTools;
import org.etnaframework.core.util.StringTools;
import org.etnaframework.core.util.SystemInfo;
import org.etnaframework.core.web.HttpEvent;
import org.etnaframework.core.web.cmd.HttpCmd;
import org.etnaframework.core.web.constant.CmdCategory;
import org.etnaframework.core.web.mapper.CmdMappers;
import org.etnaframework.core.web.mapper.CmdMeta;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import com.google.common.collect.TreeMultimap;

/**
 * 在线DOC文档
 *
 * @author YuanHaoliang
 * @since 2014-8-2
 */
@Controller
public class DocCmd extends HttpCmd {

    @Autowired
    private CmdMappers mappers;

    /** 系统自带的接口的文档 */
    private Set<Entry<String, Collection<DocMeta>>> docTreeEntrySetSys;

    /** 业务代码的接口的文档 */
    private Set<Entry<String, Collection<DocMeta>>> docTreeEntrySet;

    @OnContextInited
    protected void init() {
        // 创建doc文档目录树，以主菜单按category排序，子菜单按uri排序
        Comparator<String> compString = new Comparator<String>() {

            @Override
            public int compare(String o1, String o2) {
                return o1.compareTo(o2);
            }
        };
        Comparator<DocMeta> compMeta = new Comparator<DocMeta>() {

            @Override
            public int compare(DocMeta o1, DocMeta o2) {
                return o1.cmdPath.compareTo(o2.cmdPath);
            }
        };
        TreeMultimap<String, DocMeta> doctreeSys = TreeMultimap.create(compString, compMeta);
        TreeMultimap<String, DocMeta> doctree = TreeMultimap.create(compString, compMeta);

        // cmdPath->cmdMeta
        Map<String, CmdMeta> mapper = mappers.getCmdMappers();
        for (Entry<String, CmdMeta> e : mapper.entrySet()) {
            DocMeta dm = new DocMeta(e.getKey(), e.getValue().getMethod());
            if (StringTools.isEmpty(dm.category) && (CollectionTools.isEmpty(dm.cmd))) {
                // 没有@Cmd的过滤掉
                continue;
            }
            if (CmdCategory.SYSTEM.equals(dm.category)) {
                doctreeSys.put(dm.category, dm);
            } else {
                doctree.put(dm.category, dm);
            }
        }
        docTreeEntrySetSys = doctreeSys.asMap().entrySet();
        docTreeEntrySet = doctree.asMap().entrySet();
    }

    @Override
    public void index(HttpEvent he) throws Throwable {
        auth(this, he);
        boolean sys = he.getBool("sys", false);
        String defaultDomain = (he.getHttpServletRequest().isSecure()?"https://":"http://") + he.getHost() + he.getContextPath();
        he.set("domain", defaultDomain);
        he.set("title", SystemInfo.COMMAND_SHORT + " Doc");
        if (sys) {
            he.set("doctree", docTreeEntrySetSys);
            he.setAccessLogContent("[DOC Of " + SystemInfo.COMMAND_SHORT + "(System)]");
        } else {
            he.set("doctree", docTreeEntrySet);
            he.setAccessLogContent("[DOC Of " + SystemInfo.COMMAND_SHORT + "]");
        }
        he.renderHtml("/etna/doc.html");
    }
}
