package com.leetjourney.alert_service.service;

import com.leetjourney.kafka.event.AlertingEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AlertServiceTest {

    @Mock
    private EmailService emailService;

    @InjectMocks
    private AlertService alertService;

    @Test
    void energyUsageAlertEvent_buildsSubjectAndBodyAndDelegatesToEmailService() {
        AlertingEvent event = AlertingEvent.builder()
                .userId(7L)
                .message("Energy consumption threshold exceeded")
                .threshold(100.0)
                .energyConsumed(150.0)
                .email("user@example.com")
                .build();

        alertService.energyUsageAlertEvent(event);

        ArgumentCaptor<String> to = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> subject = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Long> userId = ArgumentCaptor.forClass(Long.class);
        verify(emailService).sendEmail(to.capture(), subject.capture(), body.capture(), userId.capture());

        assertThat(to.getValue()).isEqualTo("user@example.com");
        assertThat(subject.getValue()).isEqualTo("Energy Usage Alert for User 7");
        assertThat(body.getValue())
                .contains("Energy consumption threshold exceeded")
                .contains("Threshold: 100.0")
                .contains("Energy Consumed: 150.0");
        assertThat(userId.getValue()).isEqualTo(7L);
    }

    @Test
    void energyUsageAlertEvent_passesThroughNullEmailWithoutModification() {
        AlertingEvent event = AlertingEvent.builder()
                .userId(99L)
                .message("m")
                .threshold(1.0)
                .energyConsumed(2.0)
                .email(null)
                .build();

        alertService.energyUsageAlertEvent(event);

        verify(emailService).sendEmail(
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.eq("Energy Usage Alert for User 99"),
                org.mockito.ArgumentMatchers.contains("Energy Consumed: 2.0"),
                org.mockito.ArgumentMatchers.eq(99L));
    }
}
