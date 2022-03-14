package org.etnaframework.core.util;

import java.lang.Character.UnicodeBlock;
import java.util.HashSet;
import java.util.Set;

/**
 * 不可见字符过滤器(参考自方舟的文件名检测器)
 *
 * @author BlackCat/YuanHaoliang
 */
public class InvisibleCharacterFilter {

    /**
     * 允许用户提交的字符集列表（包括繁简中文、日文、韩文，另外取用了QQ拼音输入法的符号输入器所有能输入的符号，这些都能在Win7上正常显示）
     */
    private static Set<String> allowedCharacters;

    static {
        allowedCharacters = new HashSet<String>();
        allowedCharacters.add(UnicodeBlock.BASIC_LATIN.toString()); // 基本ASCII字符，包括控制字符、英文字母、数字、运算符等
        allowedCharacters.add(UnicodeBlock.GENERAL_PUNCTUATION.toString()); // 基本标点符号
        allowedCharacters.add(UnicodeBlock.CJK_SYMBOLS_AND_PUNCTUATION.toString()); // CJK符号
        allowedCharacters.add(UnicodeBlock.HALFWIDTH_AND_FULLWIDTH_FORMS.toString()); // 半角全角符号
        allowedCharacters.add(UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS.toString()); // CJK字符（中文）
        allowedCharacters.add(UnicodeBlock.HANGUL_SYLLABLES.toString()); // 韩语
        allowedCharacters.add(UnicodeBlock.HIRAGANA.toString()); // 日文平假名
        allowedCharacters.add(UnicodeBlock.KATAKANA.toString()); // 日文片假名
        allowedCharacters.add(UnicodeBlock.DINGBATS.toString());
        allowedCharacters.add(UnicodeBlock.SMALL_FORM_VARIANTS.toString());
        allowedCharacters.add(UnicodeBlock.CJK_COMPATIBILITY_FORMS.toString());
        allowedCharacters.add(UnicodeBlock.ENCLOSED_CJK_LETTERS_AND_MONTHS.toString());
        allowedCharacters.add(UnicodeBlock.NUMBER_FORMS.toString());
        allowedCharacters.add(UnicodeBlock.ENCLOSED_ALPHANUMERICS.toString());
        allowedCharacters.add(UnicodeBlock.MATHEMATICAL_OPERATORS.toString());
        allowedCharacters.add(UnicodeBlock.LATIN_1_SUPPLEMENT.toString());
        allowedCharacters.add(UnicodeBlock.GREEK.toString());
        allowedCharacters.add(UnicodeBlock.CJK_COMPATIBILITY.toString());
        allowedCharacters.add(UnicodeBlock.SPACING_MODIFIER_LETTERS.toString());
        allowedCharacters.add(UnicodeBlock.MISCELLANEOUS_TECHNICAL.toString());
        allowedCharacters.add(UnicodeBlock.SUPERSCRIPTS_AND_SUBSCRIPTS.toString());
        allowedCharacters.add(UnicodeBlock.LETTERLIKE_SYMBOLS.toString());
        allowedCharacters.add(UnicodeBlock.GEOMETRIC_SHAPES.toString());
        allowedCharacters.add(UnicodeBlock.CYRILLIC.toString());
        allowedCharacters.add(UnicodeBlock.LATIN_EXTENDED_A.toString());
        allowedCharacters.add(UnicodeBlock.LATIN_EXTENDED_B.toString());
        allowedCharacters.add(UnicodeBlock.BOPOMOFO.toString());
        allowedCharacters.add(UnicodeBlock.HANGUL_COMPATIBILITY_JAMO.toString());
        allowedCharacters.add(UnicodeBlock.IPA_EXTENSIONS.toString());
        allowedCharacters.add(UnicodeBlock.BOX_DRAWING.toString());
        allowedCharacters.add(UnicodeBlock.MISCELLANEOUS_SYMBOLS.toString());
        allowedCharacters.add(UnicodeBlock.BLOCK_ELEMENTS.toString());
        allowedCharacters.add(UnicodeBlock.ARABIC.toString());
        allowedCharacters.add(UnicodeBlock.THAI.toString());
        allowedCharacters.add(UnicodeBlock.ARROWS.toString());
    }

    /**
     * 过滤不可见字符
     */
    public static String filter(String src) {
        if (src == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder(src.length());
        for (int i = 0; i < src.length(); i++) {
            char ch = src.charAt(i);
            UnicodeBlock ub = Character.UnicodeBlock.of(ch);// 是一个二分查找定位
            if (null == ub || !allowedCharacters.contains(ub.toString())) {
                continue;
            }
            sb.append(ch);
        }

        return sb.toString();
    }
}
