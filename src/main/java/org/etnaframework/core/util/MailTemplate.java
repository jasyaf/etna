package org.etnaframework.core.util;

import java.io.File;
import javax.mail.internet.MimeMessage;
import org.etnaframework.core.logging.Log;
import org.etnaframework.core.logging.logback.LogFormatFactory;
import org.slf4j.Logger;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

/**
 * <pre>
 * 发送邮件的模板类，需要在初始化时注入一个JavaMailSender的实例
 *
 * 和MailTemplate的区别是返回值是一个结果bean
 * </pre>
 *
 * @author BlackCat
 * @since 2015-02-12
 */
public class MailTemplate {

    private static Logger log = Log.getLogger();

    private static final LogFormatFactory logformat = LogFormatFactory.getInstance("|");

    /** 邮件内容使用的默认编码方式 */
    private String defaultCharset = "UTF-8";

    /** 发件人邮箱地址，这个必须要和发邮件的帐号匹配，否则邮件发不出去 */
    private String fromAddress;

    /** 发件人名称，如 XX个人中心 */
    private String fromName = "";

    /** 发送不成功时的最大重试次数 */
    private int maxRetryTime = 0;

    /** 发送不成功时的重试间隔，单位为毫秒 */
    private long retryIdle = 0;

    /** 重试间隔加倍系统 */
    private int retryIdleFactor = 2;

    /** Spring提供的邮件发送类的对象 */
    private JavaMailSender sender;

    public MailTemplate(JavaMailSender sender, String fromAddress) {
        this.sender = sender;
        this.fromAddress = fromAddress;
    }

    public MailTemplate(JavaMailSender sender, String fromAddress, String fromName, String defaultCharset, int maxTryTime, long retryIdle) {
        this.sender = sender;
        this.fromAddress = fromAddress;
        this.fromName = fromName;
        this.defaultCharset = defaultCharset;
        this.maxRetryTime = maxTryTime;
        this.retryIdle = retryIdle;
    }

    /**
     * 发送邮件功能主体部分
     *
     * @param to 收件人列表
     * @param subject 邮件标题
     * @param content 邮件正文内容
     * @param isMime 是否是MIME邮件
     * @param asHtml 邮件内容是否是HTML内容
     * @param attachments 附件列表
     *
     * @return 发送成功返回true失败返回false
     */
    private MailSendResult send(String[] to, String subject, String content, boolean isMime, boolean asHtml, File... attachments) {
        boolean r = false;
        MailSendResult sendResult = new MailSendResult();

        Exception ex = null;
        String result = "OK";
        long idle = retryIdle;
        long firstNano = System.nanoTime(); // 用于对历次重试做唯一性标识
        // 按最大重试次数进行重试
        for (int i = 0; i <= maxRetryTime; i++) {
            try {
                // 对提交的邮件参数进行校验
                if (CollectionTools.isEmpty(to)) {
                    throw new NullPointerException("To is null");
                }
                if (StringTools.isEmpty(subject)) {
                    throw new NullPointerException("Subject is empty");
                }
                if (StringTools.isEmpty(content)) {
                    throw new NullPointerException("Content is empty");
                }
                // 分是否是MIME邮件进行分别处理
                if (isMime) {
                    MimeMessage msg = sender.createMimeMessage();
                    boolean multipart = false;
                    if (!CollectionTools.isEmpty(attachments)) {
                        multipart = true;
                    }
                    MimeMessageHelper mail = new MimeMessageHelper(msg, multipart, defaultCharset);
                    if (StringTools.isEmpty(fromName)) {
                        mail.setFrom(fromAddress);
                    } else {
                        mail.setFrom(fromAddress, fromName);
                    }
                    mail.setTo(to);
                    mail.setSubject(subject);
                    mail.setText(content, asHtml);
                    // 将内嵌附件加入邮件中
                    if (!CollectionTools.isEmpty(attachments)) {
                        for (File file : attachments) {
                            // 用addAttachment在QQ邮箱里才能正确显示附件名 fix@2016-10-13 by yuanhaoliang
                            mail.addAttachment(file.getName(),file);
//                            mail.addInline(file.getName(), file);
                        }
                    }
                    sender.send(msg);
                    sendResult.messageId = msg.getMessageID();// 获取消息ID
                } else {
                    SimpleMailMessage mail = new SimpleMailMessage();
                    mail.setFrom(fromAddress);
                    mail.setTo(to);
                    mail.setSubject(subject);
                    mail.setText(content);
                    sender.send(mail);
                }
                r = true;
                sendResult.result = true;
            } catch (Exception e) {
                ex = e;
                result = e.getClass().getName() + ": " + StringTools.escapeWhitespace(e.getMessage());
            } finally {
                if (r) {
                    Object[] args = {
                        firstNano,
                        result,
                        isMime ? "HTML" : "TEXT",
                        i + 1,
                        to,
                        subject,
                        StringTools.escapeWhitespace(content)
                    };
                    log.debug(logformat.getFormat(args), args);
                } else {
                    Object[] args = {
                        firstNano,
                        result,
                        isMime ? "HTML" : "TEXT",
                        i + 1,
                        to,
                        subject,
                        StringTools.escapeWhitespace(content),
                        null == ex ? "OK" : StringTools.escapeWhitespace(ex.getMessage())
                    };
                    log.error(logformat.getFormat(args), args);
                }
            }
            if (r || ex instanceof NullPointerException) {
                break;
            }
            try {
                Thread.sleep(idle);
                idle *= retryIdleFactor; // 下一次重试时间间隔延长
            } catch (Exception e) {
                log.error("", e);
            }
        }
        return sendResult;
    }

    /**
     * 发送MIME邮件
     *
     * @param to 收件人邮箱地址
     * @param subject 邮件标题
     * @param content 邮件正文内容
     */
    public MailSendResult sendMimeMail(String to, String subject, String content, File... attachments) {
        return send(new String[] {
            to
        }, subject, content, true, true, attachments);
    }

    /**
     * 发送MIME邮件
     *
     * @param to 收件人列表
     * @param subject 邮件标题
     * @param content 邮件正文内容
     */
    public MailSendResult sendMimeMail(String[] to, String subject, String content, File... attachments) {
        return send(to, subject, content, true, true, attachments);
    }

    /**
     * 发送MIME邮件
     *
     * @param to 收件人列表
     * @param subject 邮件标题
     * @param content 邮件正文内容
     * @param asHtml 邮件内容是否是以HTML方式发送，默认为true。如果用false则可用于发送带附件的纯文本邮件
     */
    public MailSendResult sendMimeMail(String[] to, String subject, String content, boolean asHtml, File... attachments) {
        return send(to, subject, content, true, asHtml, attachments);
    }

    /**
     * 发送MIME邮件
     *
     * @param to 收件人列表
     * @param subject 邮件标题
     * @param content 邮件正文内容
     * @param asHtml 邮件内容是否是以HTML方式发送，默认为true。如果用false则可用于发送带附件的纯文本邮件
     */
    public MailSendResult sendMimeMail(String to, String subject, String content, boolean asHtml, File... attachments) {
        return send(new String[] {
            to
        }, subject, content, true, asHtml, attachments);
    }

    /**
     * 发送纯文本邮件
     *
     * @param to 收件人邮箱地址
     * @param subject 邮件标题
     * @param content 邮件正文内容
     */
    public MailSendResult sendTextMail(String to, String subject, String content) {
        return send(new String[] {
            to
        }, subject, content, false, false);
    }

    /**
     * 发送纯文本邮件
     *
     * @param to 收件人列表
     * @param subject 邮件标题
     * @param content 邮件正文内容
     */
    public MailSendResult sendTextMail(String[] to, String subject, String content) {
        return send(to, subject, content, false, false);
    }

    /**
     * 设置默认的字符
     */
    public void setDefaultCharset(String defaultCharset) {
        this.defaultCharset = defaultCharset;
    }

    /**
     * 设置发送人地址
     */
    public void setFromAddress(String fromAddress) {
        this.fromAddress = fromAddress;
    }

    /**
     * 设置发送人姓名
     */
    public void setFromName(String fromName) {
        this.fromName = fromName;
    }

    /**
     * 设置最大重试次数
     */
    public void setMaxRetryTime(int maxRetryTime) {
        this.maxRetryTime = maxRetryTime;
    }

    /**
     * 设置最大重试间隔，单位毫秒
     */
    public void setRetryIdle(long retryIdle) {
        this.retryIdle = retryIdle;
    }

    /**
     * 设置重试间隔加倍因数
     */
    public void setRetryIdleFactor(int retryIdleFactor) {
        this.retryIdleFactor = retryIdleFactor;
    }

    /**
     * 设置发送器
     */
    public void setSender(JavaMailSender sender) {
        this.sender = sender;
    }

    /**
     * 邮件发送结果
     */
    public static class MailSendResult {

        /** 发送结果,是否已发出 */
        public boolean result;

        /** 发出邮件的ID号 ,只有发MIME邮件 有 */
        public String messageId = "";
    }
}
