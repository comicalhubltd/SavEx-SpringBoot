package com.savex.verification;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import com.savex.exception.BadRequestException;

@Service
public class VerificationEmailSender {
    private final JavaMailSender sender;
    private final String from, password;
    public VerificationEmailSender(JavaMailSender sender,@Value("${spring.mail.username}") String from,
                                  @Value("${spring.mail.password:}") String password) {
        this.sender=sender;this.from=from;this.password=password;
    }
    public void send(String email,String code) {
        if(password.isBlank()) throw new BadRequestException("Email delivery is not configured. Contact support.");
        var message=new SimpleMailMessage();message.setFrom(from);message.setTo(email);
        message.setSubject("Your SavEx email verification code");
        message.setText("Your SavEx verification code is "+code+". It expires in 10 minutes. Do not share it. If you did not request this, ignore this email.");
        try { sender.send(message); }
        catch(MailException ex) { throw new BadRequestException("Could not send the verification email. Please try later."); }
    }
}
