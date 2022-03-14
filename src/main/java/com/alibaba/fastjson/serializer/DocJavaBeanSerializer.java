package com.alibaba.fastjson.serializer;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import org.etnaframework.core.util.StringTools;
import org.etnaframework.core.web.annotation.CmdRespParam;
import com.alibaba.fastjson.JSONException;
import com.alibaba.fastjson.annotation.JSONField;
import com.alibaba.fastjson.util.FieldInfo;
/**
 * doc文档的json回包序列化
 * Created by yuanhaoliang on 2017-02-25.
 */
public class DocJavaBeanSerializer extends JavaBeanSerializer {

    public DocJavaBeanSerializer(SerializeBeanInfo beanInfo) {
        super(beanInfo);
    }

    public void write(JSONSerializer serializer, //
        Object object, //
        Object fieldName, //
        Type fieldType, //
        int features) throws IOException {
        SerializeWriter out = serializer.out;

        if (object == null) {
            out.writeNull();
            return;
        }

        if (writeReference(serializer, object, features)) {
            return;
        }

        final FieldSerializer[] getters;

        if (out.sortField) {
            getters = this.sortedGetters;
        } else {
            getters = this.getters;
        }

        SerialContext parent = serializer.context;
        serializer.setContext(parent, object, fieldName, this.beanInfo.features, features);

        final boolean writeAsArray = isWriteAsArray(serializer, features);

        try {
            final char startSeperator = writeAsArray ? '[' : '{';
            final char endSeperator = writeAsArray ? ']' : '}';
            out.append(startSeperator);

            if (getters.length > 0 && out.isEnabled(SerializerFeature.PrettyFormat)) {
                serializer.incrementIndent();
                serializer.println();
            }

            boolean commaFlag = false;

            if ((this.beanInfo.features & SerializerFeature.WriteClassName.mask) != 0 || serializer.isWriteClassName(
                fieldType, object)) {
                Class<?> objClass = object.getClass();
                if (objClass != fieldType) {
                    writeClassName(serializer, object);
                    commaFlag = true;
                }
            }

            char seperator = commaFlag ? ',' : '\0';

            final boolean directWritePrefix = out.quoteFieldNames && !out.useSingleQuotes;
            char newSeperator = this.writeBefore(serializer, object, seperator);
            commaFlag = newSeperator == ',';

            final boolean skipTransient = out.isEnabled(SerializerFeature.SkipTransientField);
            final boolean ignoreNonFieldGetter = out.isEnabled(SerializerFeature.IgnoreNonFieldGetter);

            for (int i = 0; i < getters.length; ++i) {
                FieldSerializer fieldSerializer = getters[i];

                Field field = fieldSerializer.fieldInfo.field;
                FieldInfo fieldInfo = fieldSerializer.fieldInfo;
                String fieldInfoName = fieldInfo.name;
                Class<?> fieldClass = fieldInfo.fieldClass;

                if (skipTransient) {
                    if (field != null) {
                        if (fieldInfo.fieldTransient) {
                            continue;
                        }
                    }
                }

                if (ignoreNonFieldGetter) {
                    if (field == null) {
                        continue;
                    }
                }

                if ((!this.applyName(serializer, object, fieldInfo.name)) //
                    || !this.applyLabel(serializer, fieldInfo.label)) {
                    continue;
                }

                Object propertyValue;

                try {
                    propertyValue = fieldSerializer.getPropertyValueDirect(object);
                } catch (InvocationTargetException ex) {
                    if (out.isEnabled(SerializerFeature.IgnoreErrorGetter)) {
                        propertyValue = null;
                    } else {
                        throw ex;
                    }
                }

                if (!this.apply(serializer, object, fieldInfoName, propertyValue)) {
                    continue;
                }

                // @CRACK BEGIN 注入示例值
                CmdRespParam cmdRespParam = field.getAnnotation(CmdRespParam.class);

                if (cmdRespParam != null && StringTools.isNotEmpty(cmdRespParam.sample()) && StringTools.isBasicType(
                    fieldClass)) {
                    // 注入sample值
                    propertyValue = StringTools.valueOf(cmdRespParam.sample(), null, fieldClass);
                }

                if (propertyValue == null && !StringTools.isBasicType(fieldClass)) {
                    // 如果没有默认值，尝试实例化
                    // TODO:支持容器实例化和单个例子bean
                    try {
                        propertyValue = field.getType().newInstance();
                    } catch (InstantiationException | IllegalAccessException ignore) {
                    }
                }
                // @CRACK END 注入示例值

                String key = fieldInfoName;
                key = this.processKey(serializer, object, key, propertyValue);

                Object originalValue = propertyValue;
                propertyValue = this.processValue(serializer, fieldSerializer.fieldContext, object, fieldInfoName,
                    propertyValue);

                if (propertyValue == null && !writeAsArray) {
                    if ((!fieldSerializer.writeNull) && (!out.isEnabled(SerializerFeature.WRITE_MAP_NULL_FEATURES))) {
                        continue;
                    }
                }

                if (propertyValue != null && out.notWriteDefaultValue) {
                    Class<?> fieldCLass = fieldInfo.fieldClass;
                    if (fieldCLass == byte.class && propertyValue instanceof Byte && ((Byte) propertyValue).byteValue() == 0) {
                        continue;
                    } else if (fieldCLass == short.class && propertyValue instanceof Short && ((Short) propertyValue).shortValue() == 0) {
                        continue;
                    } else if (fieldCLass == int.class && propertyValue instanceof Integer && ((Integer) propertyValue).intValue() == 0) {
                        continue;
                    } else if (fieldCLass == long.class && propertyValue instanceof Long && ((Long) propertyValue).longValue() == 0L) {
                        continue;
                    } else if (fieldCLass == float.class && propertyValue instanceof Float && ((Float) propertyValue).floatValue() == 0F) {
                        continue;
                    } else if (fieldCLass == double.class && propertyValue instanceof Double && ((Double) propertyValue)
                        .doubleValue() == 0D) {
                        continue;
                    } else if (fieldCLass == boolean.class && propertyValue instanceof Boolean && !((Boolean) propertyValue)
                        .booleanValue()) {
                        continue;
                    }
                }

                if (commaFlag) {
                    out.write(',');
                    if (out.isEnabled(SerializerFeature.PrettyFormat)) {

                        serializer.println();
                    }
                }
                if (key != fieldInfoName) {
                    if (!writeAsArray) {
                        out.writeFieldName(key, true);
                    }

                    serializer.write(propertyValue);
                } else if (originalValue != propertyValue) {
                    if (!writeAsArray) {
                        fieldSerializer.writePrefix(serializer);
                    }
                    serializer.write(propertyValue);
                } else {
                    if (!writeAsArray) {
                        if (directWritePrefix) {
                            out.write(fieldInfo.name_chars, 0, fieldInfo.name_chars.length);
                        } else {
                            fieldSerializer.writePrefix(serializer);
                        }
                    }

                    if (!writeAsArray) {
                        JSONField fieldAnnotation = fieldInfo.getAnnotation();
                        if (fieldClass == String.class && (fieldAnnotation == null || fieldAnnotation.serializeUsing() == Void.class)) {
                            if (propertyValue == null) {
                                if ((out.features & SerializerFeature.WriteNullStringAsEmpty.mask) != 0 || (fieldSerializer.features & SerializerFeature.WriteNullStringAsEmpty.mask) != 0) {
                                    out.writeString("");
                                } else {
                                    out.writeNull();
                                }
                            } else {
                                String propertyValueString = (String) propertyValue;

                                if (out.useSingleQuotes) {
                                    out.writeStringWithSingleQuote(propertyValueString);
                                } else {
                                    out.writeStringWithDoubleQuote(propertyValueString, (char) 0);
                                }
                            }
                        } else {
                            fieldSerializer.writeValue(serializer, propertyValue);
                        }
                    } else {
                        fieldSerializer.writeValue(serializer, propertyValue);
                    }

                    // @CRACK BEGIN 注入注释
                    // 注释会插入在字段结尾的逗号前，使用/***/包住注释的JSON在chrome是可以直接解析的。
                    if (cmdRespParam != null) {
                        String typeEnum = "";
                        if (!Object.class.equals(cmdRespParam.typeEnum())) {
                            // 如果有指定范围
                            StringBuilder sb = new StringBuilder();
                            sb.append("(指定范围：");

                            for (Field _field : cmdRespParam.typeEnum().getFields()) {
                                int mod = _field.getModifiers();
                                if (Modifier.isFinal(mod) && Modifier.isStatic(mod)) {
                                    // 如果是enum或static final的常量，则获取显示
                                    _field.setAccessible(true);
                                    sb.append(_field.get(null)).append("/");
                                }
                            }

                            if (sb.length() > 0) {
                                sb.deleteCharAt(sb.length() - 1);
                            }
                            sb.append(")");
                            typeEnum = sb.toString();
                        }
                        out.write("/* " + cmdRespParam.desc() + typeEnum + " */");
                    }
                    // @CRACK END 注入注释

                }

                commaFlag = true;
            }

            this.writeAfter(serializer, object, commaFlag ? ',' : '\0');

            if (getters.length > 0 && out.isEnabled(SerializerFeature.PrettyFormat)) {
                serializer.decrementIdent();
                serializer.println();
            }

            out.append(endSeperator);
        } catch (Exception e) {
            String errorMessage = "write javaBean error";
            if (object != null) {
                errorMessage += ", class " + object.getClass().getName();
            }
            if (fieldName != null) {
                errorMessage += ", fieldName : " + fieldName;
            }
            if (e.getMessage() != null) {
                errorMessage += (", " + e.getMessage());
            }

            throw new JSONException(errorMessage, e);
        } finally {
            serializer.context = parent;
        }
    }
}
