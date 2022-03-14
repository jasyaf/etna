package org.etnaframework.plugin.doc;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import org.etnaframework.core.util.ReflectionTools;
import org.etnaframework.core.web.annotation.Cmd;
import org.etnaframework.core.web.annotation.CmdAuthor;
import org.etnaframework.core.web.annotation.CmdContentType;
import org.etnaframework.core.web.annotation.CmdContentType.CmdContentTypes;
import org.etnaframework.core.web.annotation.CmdParam;
import org.etnaframework.core.web.annotation.CmdParams;
import org.etnaframework.core.web.annotation.CmdReqParam;
import org.etnaframework.core.web.annotation.CmdReturn;
import org.etnaframework.core.web.annotation.CmdSession;
import org.etnaframework.core.web.bean.HttpRespBean;
import org.etnaframework.plugin.doc.json.DocJsonGenerator;
import javassist.CtClass;
import javassist.CtField;
import javassist.CtMethod;

/**
 * 用于生成DOC文档的接口DOCMETA
 *
 * @author YuanHaoliang
 * @since 2014-8-2
 */
public class DocMeta {

    /** cmdpath */
    public String cmdPath = "";

    /** cmd描述信息 */
    public String[] cmd;

    /** 分类 */
    public String category = "";

    /** 接口域名前缀，用于指定接口使用的域名和端口等信息，如果不填的话默认就按服务自己的URL来构造 */
    public String domain = "";

    /** 登录态要求 */
    public String session = "";

    /** 接口编写人 */
    public String author = "";

    /** 参数列表 */
    public List<DocCmdParam> params = new ArrayList<DocCmdParam>();

    /** 返回类型 */
    public String contentType = "";

    /** 回包示例 */
    public String returnDesc = "";

    /** 映射的方法名，用于做唯一性确定 */
    public String methodName = "";

    public DocMeta(String cmdPath, CtMethod method) {
        try {
            methodName = method.getLongName();

            // URI
            this.cmdPath = cmdPath;

            // 分类，CMD名，域名前缀
            Cmd c = (Cmd) method.getAnnotation(Cmd.class);
            if (c != null) {
                this.category = c.category();
                this.cmd = c.desc();
                this.domain = c.domain();
            }

            // 登录态要求
            CmdSession cs = (CmdSession) method.getAnnotation(CmdSession.class);
            if (cs != null) {
                this.session = cs.value().toString();
                if (cs.desc() != null && !cs.desc().isEmpty()) {
                    this.session += "\n(" + cs.desc() + ")";
                }
            }

            // 接口编写者
            CmdAuthor ca = (CmdAuthor) method.getAnnotation(CmdAuthor.class);
            if (ca != null) {
                if (ca.value().length == 1) {
                    this.author = ca.value()[0];
                } else {
                    for (String s : ca.value()) {
                        this.author += s + ",";
                    }
                    this.author = this.author.substring(0, this.author.length() - 1);
                }
            }
            // 参数列表
            CmdParam cp = (CmdParam) method.getAnnotation(CmdParam.class);
            CmdParams cps = (CmdParams) method.getAnnotation(CmdParams.class);
            if (cp != null) {
                this.params.add(new DocCmdParam(cp));
            }
            if (cps != null) {
                for (CmdParam ccp : cps.value()) {
                    this.params.add(new DocCmdParam(ccp));
                }

                if (!cps.reqClass().equals(Object.class)) {
                    // 如果有指定请求类，则根据请求类生成参数列表
                    CtClass clz = ReflectionTools.getCtClass(cps.reqClass());
                    // 获取所有非private的成员变量（包含父类）
                    CtField[] fields = clz.getFields();

                    for (CtField field : fields) {
                        CmdReqParam crp = (CmdReqParam) field.getAnnotation(CmdReqParam.class);
                        if (crp == null) {
                            continue;
                        }
                        this.params.add(new DocCmdParam(field.getName(), crp));
                    }
                }
            }

            // 回包类型
            CmdContentType cct = (CmdContentType) method.getAnnotation(CmdContentType.class);
            if (cct != null) {
                if (cct.value().length == 1) {
                    this.contentType = cct.value()[0].toString();
                } else {
                    for (CmdContentTypes ct : cct.value()) {
                        this.contentType += ct.toString() + ",";
                    }
                    this.contentType = this.contentType.substring(0, this.contentType.length() - 1);
                }
            }

            // 回包内容
            CmdReturn cr = (CmdReturn) method.getAnnotation(CmdReturn.class);
            if (cr != null) {
                StringBuilder sb = new StringBuilder();
                for (String s : cr.value()) {
                    sb.append(s).append("\n");
                }

                if (!Object.class.equals(cr.respClass()) && CmdContentTypes.JSON.toString().equals(this.contentType)) {
                    // 如果有设置respClass和以json输出，则生成json回包注释
                    Object data = cr.respClass().newInstance();

                    if (!HttpRespBean.class.equals(cr.shellClass())) {
                        // 如果有指定外层包装，则生成包装后的bean
                        HttpRespBean httpRespBean = cr.shellClass().newInstance();
                        Field dataField = cr.shellClass().getField(httpRespBean.getDataFieldName());
                        dataField.setAccessible(true);
                        dataField.set(httpRespBean,data);
                        data=httpRespBean;
                    }

                    sb.append(DocJsonGenerator.genRespDoc(data));
                }

                this.returnDesc = sb.toString();
            }
        } catch (Exception ignore) {
            // TODO:delete
            ignore.printStackTrace();
        }
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((methodName == null) ? 0 : methodName.hashCode());
        return result;
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
        DocMeta other = (DocMeta) obj;
        if (methodName == null) {
            if (other.methodName != null) {
                return false;
            }
        } else if (!methodName.equals(other.methodName)) {
            return false;
        }
        return true;
    }
}
