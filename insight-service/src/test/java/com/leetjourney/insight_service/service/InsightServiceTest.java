package com.leetjourney.insight_service.service;

import com.leetjourney.insight_service.client.UsageClient;
import com.leetjourney.insight_service.dto.DeviceDto;
import com.leetjourney.insight_service.dto.InsightDto;
import com.leetjourney.insight_service.dto.UsageDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.OllamaChatModel;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InsightServiceTest {

    @Mock
    private UsageClient usageClient;

    @Mock
    private OllamaChatModel ollamaChatModel;

    @InjectMocks
    private InsightService insightService;

    private ChatResponse responseWith(String text) {
        Generation gen = new Generation(new AssistantMessage(text));
        return new ChatResponse(List.of(gen));
    }

    private UsageDto usageWith(double... values) {
        List<DeviceDto> devs = java.util.stream.IntStream.range(0, values.length)
                .mapToObj(i -> DeviceDto.builder()
                        .id((long) i).name("d" + i).type("LIGHT")
                        .location("loc").energyConsumed(values[i]).build())
                .toList();
        return UsageDto.builder().userId(1L).devices(devs).build();
    }

    @Test
    void getSavingsTips_sumsDeviceEnergyAndReturnsAiTextAsTips() {
        when(usageClient.getXDaysUsageForUser(1L, 3))
                .thenReturn(usageWith(2.0, 3.5, 1.0));
        when(ollamaChatModel.call(any(Prompt.class)))
                .thenReturn(responseWith("Switch off lights at night."));

        InsightDto result = insightService.getSavingsTips(1L);

        assertThat(result.userId()).isEqualTo(1L);
        assertThat(result.tips()).isEqualTo("Switch off lights at night.");
        assertThat(result.energyUsage()).isCloseTo(6.5, offset(0.0001));
    }

    @Test
    void getSavingsTips_promptIncludesTotalConsumption() {
        when(usageClient.getXDaysUsageForUser(1L, 3))
                .thenReturn(usageWith(10.0, 20.0));
        when(ollamaChatModel.call(any(Prompt.class)))
                .thenReturn(responseWith("ok"));

        insightService.getSavingsTips(1L);

        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        org.mockito.Mockito.verify(ollamaChatModel).call(promptCaptor.capture());
        String prompt = promptCaptor.getValue().getContents();
        assertThat(prompt).contains("30.0");
        assertThat(prompt).contains("reduce my energy consumption");
    }

    @Test
    void getOverview_returnsAiTextAndTotalUsage() {
        when(usageClient.getXDaysUsageForUser(2L, 3))
                .thenReturn(usageWith(1.0, 1.0, 1.0));
        when(ollamaChatModel.call(any(Prompt.class)))
                .thenReturn(responseWith("Your usage looks normal."));

        InsightDto result = insightService.getOverview(2L);

        assertThat(result.userId()).isEqualTo(2L);
        assertThat(result.tips()).isEqualTo("Your usage looks normal.");
        assertThat(result.energyUsage()).isCloseTo(3.0, offset(0.0001));
    }

    @Test
    void getOverview_promptUsesAnalyseLanguage() {
        when(usageClient.getXDaysUsageForUser(2L, 3))
                .thenReturn(usageWith(5.0));
        when(ollamaChatModel.call(any(Prompt.class)))
                .thenReturn(responseWith("x"));

        insightService.getOverview(2L);

        ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);
        org.mockito.Mockito.verify(ollamaChatModel).call(captor.capture());
        assertThat(captor.getValue().getContents()).containsIgnoringCase("analyse");
    }
}
