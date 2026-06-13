package com.leetjourney.alert_service.service;

import com.leetjourney.alert_service.entity.Alert;
import com.leetjourney.alert_service.repository.AlertRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private AlertRepository alertRepository;

    @InjectMocks
    private EmailService emailService;

    @Test
    void sendEmail_whenMailSenderSucceeds_persistsAlertWithSentTrue() {
        emailService.sendEmail("to@example.com", "Subj", "Body", 42L);

        ArgumentCaptor<SimpleMailMessage> messageCaptor =
                ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(messageCaptor.capture());
        SimpleMailMessage sent = messageCaptor.getValue();
        assertThat(sent.getTo()).containsExactly("to@example.com");
        assertThat(sent.getFrom()).isEqualTo("noreply@leetjourney.com");
        assertThat(sent.getSubject()).isEqualTo("Subj");
        assertThat(sent.getText()).isEqualTo("Body");

        ArgumentCaptor<Alert> alertCaptor = ArgumentCaptor.forClass(Alert.class);
        verify(alertRepository).saveAndFlush(alertCaptor.capture());
        Alert persisted = alertCaptor.getValue();
        assertThat(persisted.isSent()).isTrue();
        assertThat(persisted.getUserId()).isEqualTo(42L);
        assertThat(persisted.getCreatedAt()).isNotNull();
    }

    @Test
    void sendEmail_whenMailSenderThrowsMailException_persistsAlertWithSentFalse() {
        doThrow(new MailSendException("boom")).when(mailSender).send(any(SimpleMailMessage.class));

        emailService.sendEmail("to@example.com", "Subj", "Body", 7L);

        ArgumentCaptor<Alert> alertCaptor = ArgumentCaptor.forClass(Alert.class);
        verify(alertRepository).saveAndFlush(alertCaptor.capture());
        Alert persisted = alertCaptor.getValue();
        assertThat(persisted.isSent()).isFalse();
        assertThat(persisted.getUserId()).isEqualTo(7L);
        assertThat(persisted.getCreatedAt()).isNotNull();
    }
}
