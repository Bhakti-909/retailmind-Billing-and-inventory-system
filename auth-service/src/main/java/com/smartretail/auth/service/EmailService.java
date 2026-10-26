package com.smartretail.auth.service;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private final JavaMailSender mailSender;
    private final String senderAddress;

    public EmailService(
            JavaMailSender mailSender,
            @Value("${spring.mail.username:}") String senderAddress
    ) {
        this.mailSender = mailSender;
        this.senderAddress = senderAddress;
    }

    public void sendOtpEmail(
            String email,
            String otp
    ) {

        SimpleMailMessage message =
                new SimpleMailMessage();

        message.setTo(email);

        message.setSubject(
                "Smart Retail - Email Verification OTP"
        );

        message.setText(
                "Hello,\n\n" +
                "Thank you for registering with Smart Retail.\n\n" +
                "Your email verification OTP is:\n\n" +
                otp +
                "\n\nThis OTP is valid for 15 minutes.\n\n" +
                "Regards,\n" +
                "Smart Retail Team"
        );

        mailSender.send(message);
    }

    public void sendPasswordResetOtpEmail(String email, String otp, String ownerName, String shopName) {
        SimpleMailMessage message = new SimpleMailMessage();
        if (!senderAddress.isBlank()) message.setFrom(senderAddress);
        message.setTo(email);
        message.setSubject("Reset your Smart Retail password");
        message.setText(
                "Hello " + (ownerName == null || ownerName.isBlank() ? "there" : ownerName) + ",\n\n" +
                "We received a request to reset the password for your Smart Retail account" +
                (shopName == null || shopName.isBlank() ? "." : " for " + shopName + ".") + "\n\n" +
                "Your verification code is: " + otp + "\n\n" +
                "This code expires in 5 minutes and can be used once. If you did not request a password reset, you can ignore this email.\n\n" +
                "For your security, never share this code with anyone.\n\n" +
                "Regards,\nSmart Retail Team"
        );
        mailSender.send(message);
    }

    public void sendCredentialsEmail(
            String email,
            String username,
            String password
    ) {

        SimpleMailMessage message =
                new SimpleMailMessage();

        message.setTo(email);

        message.setSubject(
                "Smart Retail - Your Account Credentials"
        );

        message.setText(
                "Hello,\n\n" +
                "Your Smart Retail account has been created successfully.\n\n" +
                "Your login credentials are:\n\n" +
                "User ID: " + username + "\n" +
                "Password: " + password + "\n\n" +
                "Please keep these credentials secure.\n\n" +
                "Regards,\n" +
                "Smart Retail Team"
        );

        mailSender.send(message);
    }

    public void sendRegistrationSuccessEmail(
            String email,
            String ownerName,
            String shopName
    ) {

        SimpleMailMessage message =
                new SimpleMailMessage();

        message.setTo(email);

        message.setSubject(
                "🎉 Congratulations! Your Smart Retail Account is Ready"
        );

        message.setText(
                "Hello " + ownerName + ",\n\n" +

                "🎉 Congratulations!\n\n" +

                "Your Smart Retail account has been " +
                "successfully registered.\n\n" +

                "Shop Name: " + shopName + "\n\n" +

                "Your email has been successfully verified " +
                "and your shop has been created in the " +
                "Smart Retail system.\n\n" +

                "You can now use your Smart Retail account " +
                "to manage your business.\n\n" +

                "Your User ID and Password have been sent " +
                "to you separately in another email.\n\n" +

                "Thank you for choosing Smart Retail.\n\n" +

                "Regards,\n" +
                "Smart Retail Team"
        );

        mailSender.send(message);
    }

        public void sendPasswordChangedEmail(
                        String email,
                        String ownerName,
                        String shopName
        ) {
                SimpleMailMessage message = new SimpleMailMessage();
                message.setTo(email);
                message.setSubject("Smart Retail - Password changed");
                message.setText(
                                "Hello " + ownerName + ",\n\n" +
                                "The password for your Smart Retail account" +
                                (shopName == null || shopName.isBlank() ? "" : " for " + shopName) +
                                " was changed successfully.\n\n" +
                                "If you did not make this change, contact your administrator or support.\n\n" +
                                "For your security, this email does not contain your password.\n\n" +
                                "Regards,\nSmart Retail Team"
                );
                mailSender.send(message);
        }
}
