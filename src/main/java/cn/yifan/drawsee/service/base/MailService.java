package cn.yifan.drawsee.service.base;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

/**
 * @FileName EmailService @Description @Author yifan
 *
 * @date 2025-03-25 15:08
 */
@Service
@Slf4j
public class MailService {

  @Autowired private JavaMailSender mailSender;

  @Autowired private TemplateEngine templateEngine;

  @Value("${spring.mail.username}")
  private String from;

  public void sendInvitationMail(String to, String userName, String invitationCode)
      throws MessagingException {
    MimeMessage message = mailSender.createMimeMessage();
    MimeMessageHelper helper = new MimeMessageHelper(message, true);

    // 设置邮件信息
    helper.setFrom(from);
    helper.setTo(to);
    helper.setSubject("昭析（DrawSee）邀请函");

    // 准备模板上下文
    Context context = new Context();
    context.setVariable("userName", userName);
    context.setVariable("invitationCode", invitationCode);

    // 渲染HTML内容
    String htmlContent = templateEngine.process("InvitationCodeMail", context);
    helper.setText(htmlContent, true);

    // 发送邮件
    mailSender.send(message);
  }
}
