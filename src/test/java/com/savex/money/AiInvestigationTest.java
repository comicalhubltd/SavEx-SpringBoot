package com.savex.money;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class AiInvestigationTest {
    final AiSettings settings=new AiSettings();
    final AiRecordRepository records=mock(AiRecordRepository.class);
    final AiModelClient model=mock(AiModelClient.class);
    final AiEvidence evidence=mock(AiEvidence.class);
    final AiBudget budget=mock(AiBudget.class);
    final ObjectMapper json=new ObjectMapper();
    final AiInvestigationService service=new AiInvestigationService(settings,records,model,evidence,budget,json);
    @Test void disabledNeverCollectsEvidenceOrCallsModel() {
        assertThatThrownBy(()->service.investigate(UUID.randomUUID(),"savex-fixture")).hasMessageContaining("AI is disabled");
        verifyNoInteractions(records,model,evidence,budget);
    }
    @Test void onlyApprovedGuidanceAndRedactedEvidenceArePassedAndFailuresAreSafe() throws Exception {
        settings.setEnabled(true);settings.setApiKey("fixture-not-real");
        var facts=json.readTree("{\"localStatus\":\"PENDING\",\"bankReceiptRecorded\":false}");
        when(evidence.collect("savex-fixture")).thenReturn(facts);
        var lesson=new AiRecord("LESSON","APPROVED","lesson",null,null,"{\"guidance\":\"Explain missing bank evidence\"}");
        when(records.findTop10ByKindAndStateOrderByCreatedAtDesc("LESSON","APPROVED")).thenReturn(List.of(lesson));
        when(model.explain(facts,List.of("Explain missing bank evidence"))).thenReturn(json.readTree("{\"summary\":\"Pending\",\"uncertainties\":\"No bank evidence\",\"nextSteps\":\"Investigate\"}"));
        when(records.save(any())).thenAnswer(call->call.getArgument(0));
        var run=service.investigate(UUID.randomUUID(),"savex-fixture");
        assertThat(run.state()).isEqualTo("UNVERIFIED");assertThat(run.content().path("lessonIds").size()).isEqualTo(1);
        verify(budget).reserve();
        when(model.explain(any(),any())).thenThrow(new RuntimeException("secret-key-provider-body"));
        assertThatThrownBy(()->service.investigate(UUID.randomUUID(),"savex-fixture")).hasMessageContaining("No financial action").hasMessageNotContaining("secret-key");
        verify(records,times(1)).save(any());
    }
    @Test void feedbackIsOncePerAdministratorAndRun() {
        UUID actor=UUID.randomUUID(),id=UUID.randomUUID();var run=new AiRecord("RUN","UNVERIFIED",null,actor,null,"{}");
        when(records.findById(id)).thenReturn(Optional.of(run));when(records.save(any())).thenAnswer(call->call.getArgument(0));
        var feedback=service.feedback(actor,id,AiInvestigationService.Feedback.HELPFUL);
        assertThat(feedback.kind()).isEqualTo("FEEDBACK");
        when(records.existsByRecordKey("FEEDBACK:"+id+":"+actor)).thenReturn(true);
        assertThatThrownBy(()->service.feedback(actor,id,AiInvestigationService.Feedback.HELPFUL)).hasMessageContaining("already recorded");
    }
    @Test void dailyReviewProposesButNeverApprovesGuidanceAndIsIdempotent() throws Exception {
        LocalDate day=LocalDate.of(2026,10,12);var saved=new ArrayList<AiRecord>();
        when(records.findTop100ByKindOrderByCreatedAtDesc("DAILY")).thenAnswer(call->saved.stream().filter(r->r.getKind().equals("DAILY")).toList());
        when(records.findTop1000ByKindAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtAsc(eq("FEEDBACK"),any(),any()))
            .thenReturn(List.of(new AiRecord("FEEDBACK","MISSING_EVIDENCE",null,null,null,"{}")));
        when(records.countByKindAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(eq("FEEDBACK"),any(),any())).thenReturn(1L);
        when(records.save(any())).thenAnswer(call->{AiRecord row=call.getArgument(0);saved.add(row);return row;});
        var daily=service.summarize(day);assertThat(daily.content().path("feedbackTotal").asInt()).isEqualTo(1);
        assertThat(saved).hasSize(2);assertThat(saved.get(0).getState()).isEqualTo("PENDING");
        assertThat(service.summarize(day).id()).isEqualTo(daily.id());assertThat(saved).hasSize(2);verifyNoInteractions(model,evidence,budget);
        UUID reviewer=UUID.randomUUID();var lesson=saved.get(0);when(records.lock(lesson.getId())).thenReturn(Optional.of(lesson));
        service.review(reviewer,lesson.getId(),true);assertThat(lesson.getState()).isEqualTo("APPROVED");assertThat(lesson.getReviewedBy()).isEqualTo(reviewer);
        service.review(reviewer,lesson.getId(),false);assertThat(lesson.getState()).isEqualTo("REJECTED");
    }
    @Test void durableBudgetStopsFurtherCallsAndCountsAttempts() {
        settings.setDailyLimit(2);var row=new AiRecord("BUDGET","ACTIVE","fixture",null,null,"1");
        when(records.lock(any())).thenReturn(Optional.of(row));
        var dailyBudget=new AiBudget(records,settings);dailyBudget.reserve();assertThat(row.getPayload()).isEqualTo("2");
        assertThatThrownBy(dailyBudget::reserve).hasMessageContaining("Daily AI request limit");
    }
}
