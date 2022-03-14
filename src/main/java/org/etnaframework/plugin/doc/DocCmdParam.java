package org.etnaframework.plugin.doc;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import org.etnaframework.core.util.StringTools;
import org.etnaframework.core.web.annotation.CmdParam;
import org.etnaframework.core.web.annotation.CmdParam.ParamScope;
import org.etnaframework.core.web.annotation.CmdReqParam;
import static org.etnaframework.core.util.StringTools.isEmpty;

/**
 * 用于生成DOC的CmdParam转换
 *
 * @author YuanHaoliang
 * @see org.etnaframework.core.web.annotation.CmdParam
 * @see org.etnaframework.core.web.annotation.CmdReqParam
 * @since 2014-8-2
 */
public class DocCmdParam {

    /** 参数名称，如username */
    public String field = "";

    /** 参数名称对应的说明文字，如username就对应“用户名” */
    public String name = "";

    /** 取值范例，用于将文档给使用者调试接口时传入 */
    public String sample = "";

    /** 参数类型 */
    public String type = "";

    /** 该参数是否是必须要传的 */
    public String required = "";

    /** 参数的默认值 */
    public String defaultValue = "";

    /** 参数获取来源 */
    public String scope = "";

    /** 参数描述，用于详细说明参数格式 */
    public String desc = "";

    /**
     * 通过@CmdParam生成参数
     */
    public DocCmdParam(CmdParam cp) {
        this.field = cp.field();
        this.name = cp.name();
        this.sample = cp.sample();
        this.type = cp.type().toString();
        this.required = cp.required() ? "必需" : "不必需";
        this.defaultValue = isEmpty(cp.defaultValue()) ? "" : "默认值:" + cp.defaultValue();

        // 参数获取来源，多个来源的话需要拼接
        if (cp.scope().length == 1) {
            this.scope = cp.scope()[0].toString();
        } else {
            for (ParamScope ps : cp.scope()) {
                this.scope += ps.toString() + ",";
            }
            this.scope = this.scope.substring(0, this.scope.length() - 1);
        }

        // 参数描述，多个自动换行。
        if (cp.desc().length == 1) {
            this.desc = cp.desc()[0];
        } else {
            for (String d : cp.desc()) {
                this.desc += d + "\n";
            }
        }
    }

    /**
     * 通过bean的@CmdReqParam生成参数
     */
    public DocCmdParam(String fieldName, CmdReqParam crp) {
        this.field = isEmpty(crp.alias())?fieldName:crp.alias();
        this.name = isEmpty(crp.name())?(isEmpty(crp.alias())?fieldName:crp.alias()):crp.name();
        this.sample = crp.sample();
        this.type = crp.type().toString();
        this.required = crp.required() ? "必需" : "不必需";
        this.defaultValue = isEmpty(crp.defaultValue()) ? "" : "默认值:" + crp.defaultValue();

        // 参数获取来源，多个来源的话需要拼接
        if (crp.scope().length == 1) {
            this.scope = crp.scope()[0].toString();
        } else {
            for (ParamScope ps : crp.scope()) {
                this.scope += ps.toString() + ",";
            }
            this.scope = this.scope.substring(0, this.scope.length() - 1);
        }

        // 参数描述，多个自动换行。
        if (crp.desc().length == 1) {
            this.desc = crp.desc()[0];
        } else {
            for (String d : crp.desc()) {
                this.desc += d + "\n";
            }
        }

        if (!Object.class.equals(crp.typeEnum())) {
            // 如果有指定范围
            StringBuilder sb = new StringBuilder();
            sb.append("(指定范围：");

            for (Field _field : crp.typeEnum().getFields()) {
                int mod = _field.getModifiers();
                if (Modifier.isFinal(mod) && Modifier.isStatic(mod)) {
                    // 如果是enum或static final的常量，则获取显示

                    try {
                        _field.setAccessible(true);
                        sb.append(_field.get(null)).append("/");
                    } catch (IllegalAccessException ignore) {
                    }
                }
            }

            if (sb.length() > 0) {
                sb.deleteCharAt(sb.length() - 1);
            }
            sb.append(")");
            if(isEmpty( this.desc )){
                this.desc=sb.toString();
            }else{
                this.desc+="\n"+sb.toString();
            }
        }
    }
}
