package com.smartretail.auth.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {
    @Mock
    private JavaMailSender mailSender;

    @InjectMocks
    private EmailService emailService;

    @Test
    void otpRegistrationAndPasswordNotificationsUseTheConfiguredMailSender() {
        emailService.sendOtpEmail("owner@example.com", "123456");
        emailService.sendCredentialsEmail("owner@example.com", "owner", "temporary-password");
        emailService.sendRegistrationSuccessEmail("owner@example.com", "Shop Owner", "Corner Shop");
        emailService.sendPasswordChangedEmail("owner@example.com", "Shop Owner", "Corner Shop");

        ArgumentCaptor<SimpleMailMessage> messages = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, times(4)).send(messages.capture());
        assertEquals(4, messages.getAllValues().size());
        assertTrue(messages.getAllValues().stream()
                .allMatch(message -> message.getTo() != null && message.getTo().length == 1
                        && "owner@example.com".equals(message.getTo()[0])));
        assertTrue(messages.getAllValues().get(0).getText().contains("123456"));
        assertTrue(messages.getAllValues().get(3).getText().contains("password"));
    }
}
