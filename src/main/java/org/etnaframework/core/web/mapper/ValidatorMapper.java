package org.etnaframework.core.web.mapper;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.etnaframework.core.util.BeanTools;
import org.etnaframework.core.util.CollectionTools;
import org.etnaframework.core.util.DatetimeUtils;
import org.etnaframework.core.util.EmojiCharacterUtils;
import org.etnaframework.core.util.JsonObjectUtils;
import org.etnaframework.core.util.ReflectionTools;
import org.etnaframework.core.util.ReflectionTools.BeanFieldValueGetter;
import org.etnaframework.core.util.ReflectionTools.BeanFieldValueSetter;
import org.etnaframework.core.util.StringTools;
import org.etnaframework.core.web.HttpEvent;
import org.etnaframework.core.web.annotation.CmdReqParam;
import org.etnaframework.jdbc.exception.BeanProcessException;

/**
 * 根据bean的字段生成JavaBean包装辅助类{@link ValidatorMapper}
 *
 * @author dragon
 * @since 2015.07.14 14:11
 */
abstract class ValidatorMapper<T> {

    /** 目标bean对应的class */
    protected Class<T> clazz;


}

