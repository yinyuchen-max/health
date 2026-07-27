package com.health.service;

import com.health.domain.dto.AppointmentExtractionContext;
import com.health.domain.dto.AppointmentExtractionResult;
import com.health.domain.entity.DoctorAppointment;
import com.health.service.impl.AppointmentConversationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AppointmentConversationServiceTest {

    private DoctorAppointmentService doctorAppointmentService;
    private AppointmentInformationExtractor informationExtractor;
    private AppointmentConversationServiceImpl conversationService;

    @BeforeEach
    void setUp() {
        doctorAppointmentService = mock(DoctorAppointmentService.class);
        informationExtractor = mock(AppointmentInformationExtractor.class);
        conversationService = new AppointmentConversationServiceImpl(
                doctorAppointmentService,
                informationExtractor
        );
    }

    @Test
    void shouldAppendAppointmentOfferAfterNormalReply() {
        String reply = conversationService.appendAppointmentOffer("请注意休息。", 1L);

        assertTrue(reply.endsWith("是否需要预约？"));
    }

    @Test
    void shouldListMissingFieldsWhenAppointmentStarts() {
        when(informationExtractor.extract(anyString(), any(AppointmentExtractionContext.class)))
                .thenReturn(new AppointmentExtractionResult());

        String reply = conversationService.handleMessage("我要预约医生", 1L);

        assertTrue(reply.contains("姓名、年龄、预约时间、用户电话、预约科室"));
    }

    @Test
    void shouldRequireConfirmationBeforeSaving() {
        when(informationExtractor.extract(anyString(), any(AppointmentExtractionContext.class)))
                .thenReturn(completeExtraction());
        when(doctorAppointmentService.createAppointment(any(DoctorAppointment.class))).thenReturn(true);

        String confirmationReply = conversationService.handleMessage(
                "预约 56 2099.7.30 9:30 泌尿科 利口 17865387668",
                7L
        );

        assertTrue(confirmationReply.contains("请确认预约信息"));
        assertTrue(confirmationReply.contains("姓名：利口"));
        verify(doctorAppointmentService, never()).createAppointment(any(DoctorAppointment.class));

        String successReply = conversationService.handleMessage("确认预约", 7L);

        assertTrue(successReply.startsWith("预约成功"));
        ArgumentCaptor<DoctorAppointment> captor = ArgumentCaptor.forClass(DoctorAppointment.class);
        verify(doctorAppointmentService).createAppointment(captor.capture());
        assertEquals("利口", captor.getValue().getPatientName());
        assertEquals("泌尿外科", captor.getValue().getDepartment());
    }

    @Test
    void shouldCollectFieldsAcrossMessagesUsingAiExtraction() {
        AppointmentExtractionResult firstExtraction = new AppointmentExtractionResult();
        firstExtraction.setPatientName("李四");
        firstExtraction.setAge(56);

        AppointmentExtractionResult secondExtraction = new AppointmentExtractionResult();
        secondExtraction.setAppointmentTime("2099-07-30 09:30");
        secondExtraction.setPhone("17865387668");
        secondExtraction.setDepartment("泌尿科");

        when(informationExtractor.extract(anyString(), any(AppointmentExtractionContext.class)))
                .thenReturn(firstExtraction, secondExtraction);

        String firstReply = conversationService.handleMessage("我要预约，我叫李四，今年56岁", 2L);
        String secondReply = conversationService.handleMessage("7月30日上午9点半，泌尿科，电话17865387668", 2L);

        assertTrue(firstReply.contains("预约时间、用户电话、预约科室"));
        assertTrue(secondReply.contains("请确认预约信息"));
        assertTrue(secondReply.contains("预约科室：泌尿外科"));
    }

    @Test
    void shouldExplainWhenAiCannotRecognizeProvidedInformation() {
        when(informationExtractor.extract(anyString(), any(AppointmentExtractionContext.class)))
                .thenReturn(new AppointmentExtractionResult());

        String reply = conversationService.handleMessage("预约 利口的信息都在这里", 1L);

        assertTrue(reply.contains("未能从本次消息中识别出有效预约信息"));
        assertTrue(reply.contains("目前还缺少"));
    }

    private AppointmentExtractionResult completeExtraction() {
        AppointmentExtractionResult extraction = new AppointmentExtractionResult();
        extraction.setAppointmentIntent(true);
        extraction.setPatientName("利口");
        extraction.setAge(56);
        extraction.setAppointmentTime("2099-07-30 09:30");
        extraction.setPhone("17865387668");
        extraction.setDepartment("泌尿科");
        return extraction;
    }
}
