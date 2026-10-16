package com.savex.money;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.savex.exception.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.dao.DataIntegrityViolationException;
import java.time.*;
import java.util.*;
import java.util.concurrent.Semaphore;

@Service @RequiredArgsConstructor
public class AiInvestigationService {
    private final AiSettings settings;
    private final AiRecordRepository records;
    private final AiModelClient model;
    private final AiEvidence evidence;
    private final AiBudget budget;
    private final ObjectMapper json;
    private final Semaphore generation=new Semaphore(1);
    public enum Feedback { HELPFUL, INACCURATE_STATUS, MISSING_EVIDENCE, UNCLEAR_NEXT_STEP }
    public record Entry(UUID id,String kind,String state,Instant createdAt,UUID reviewedBy,Instant reviewedAt,JsonNode content) {}
    private Entry entry(AiRecord record){try{return new Entry(record.getId(),record.getKind(),record.getState(),record.getCreatedAt(),record.getReviewedBy(),record.getReviewedAt(),json.readTree(record.getPayload()));}catch(Exception e){throw new IllegalStateException("Invalid saved AI report");}}
    private String encode(Object value){try{return json.writeValueAsString(value);}catch(Exception e){throw new IllegalStateException("Could not save AI report");}}
    public Entry investigate(UUID actor,String reference) {
        if(!settings.isEnabled() || settings.getApiKey().isBlank())throw new BadRequestException("AI is disabled. Configure OPENAI_API_KEY and SAVEX_AI_ENABLED=true, then restart SavEx. Existing reports remain available.");
        if(!generation.tryAcquire())throw new BadRequestException("Another AI investigation is running. Try again shortly.");
        try {
            JsonNode facts=evidence.collect(reference);
            var lessons=records.findTop10ByKindAndStateOrderByCreatedAtDesc("LESSON","APPROVED");
            List<String> guidance=lessons.stream().map(row->entry(row).content().path("guidance").asText()).toList();
            budget.reserve();
            ObjectNode report=json.createObjectNode();
            report.put("reference",reference);report.put("model",settings.getModel());report.put("policyVersion","investigation-v1");
            report.set("evidence",facts);report.set("lessonIds",json.valueToTree(lessons.stream().map(AiRecord::getId).toList()));
            try {report.set("analysis",model.explain(facts,guidance));}
            catch(RuntimeException failure){throw new BadRequestException("AI analysis unavailable or incomplete. No financial action was taken. Check the deterministic reports; the attempt counts towards the daily AI limit.");}
            report.put("warning","AI advice is unverified. Evidence is authoritative; no financial action was taken. Review all recommendations.");
            return entry(records.save(new AiRecord("RUN","UNVERIFIED",null,actor,null,report.toString())));
        } finally {generation.release();}
    }
    public List<Entry> list(String kind) {
        if(!Set.of("RUN","DAILY","LESSON").contains(kind))throw new BadRequestException("Invalid AI report type");
        return records.findTop100ByKindOrderByCreatedAtDesc(kind).stream().map(this::entry).toList();
    }
    public Entry feedback(UUID actor,UUID run,Feedback feedback) {
        var parent=records.findById(run).filter(r->"RUN".equals(r.getKind())).orElseThrow(()->new NotFoundException("Investigation not found"));
        String key="FEEDBACK:"+run+":"+actor;
        if(records.existsByRecordKey(key))throw new ConflictException("Your feedback is already recorded for this investigation");
        return entry(records.save(new AiRecord("FEEDBACK",feedback.name(),key,actor,parent.getId(),encode(Map.of("rating",feedback.name())))));
    }
    @Transactional public Entry review(UUID actor,UUID id,boolean approve) {
        var lesson=records.lock(id).filter(r->"LESSON".equals(r.getKind())).orElseThrow(()->new NotFoundException("Lesson not found"));
        lesson.setState(approve?"APPROVED":"REJECTED");lesson.setReviewedBy(actor);lesson.setReviewedAt(Instant.now());
        records.save(new AiRecord("REVIEW",lesson.getState(),null,actor,id,encode(Map.of("lessonId",id.toString(),"decision",lesson.getState(),"reviewedAt",lesson.getReviewedAt().toString()))));
        return entry(records.save(lesson));
    }
    // Runs locally while SavEx is running. First hourly tick also catches up yesterday after a restart.
    @Scheduled(initialDelay=60000,fixedDelay=3600000)
    public void daily() {
        if(!settings.isLearningEnabled())return;
        try {summarize(LocalDate.now(ZoneId.of("Africa/Lagos")).minusDays(1));}
        catch(RuntimeException ignored) { /* Retried on the next tick. No provider payloads or secrets are logged. */ }
    }
    @Transactional public Entry summarize(LocalDate day) {
        String key="DAILY:"+day;
        var existing=records.findTop100ByKindOrderByCreatedAtDesc("DAILY").stream().filter(r->key.equals(r.getRecordKey())).findFirst();
        if(existing.isPresent())return entry(existing.get());
        Instant from=day.atStartOfDay(ZoneId.of("Africa/Lagos")).toInstant(),to=day.plusDays(1).atStartOfDay(ZoneId.of("Africa/Lagos")).toInstant();
        var feedback=records.findTop1000ByKindAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtAsc("FEEDBACK",from,to);
        Map<String,Long> counts=new TreeMap<>();feedback.forEach(r->counts.merge(r.getState(),1L,Long::sum));
        long total=records.countByKindAndCreatedAtGreaterThanEqualAndCreatedAtLessThan("FEEDBACK",from,to);
        Map<String,String> suggestions=Map.of("INACCURATE_STATUS","Always state the local recorded status separately from any cached provider observation. Do not infer a terminal result.",
            "MISSING_EVIDENCE","Explicitly list missing live provider verification, settlement or bank-receipt evidence before drawing a conclusion.",
            "UNCLEAR_NEXT_STEP","Give a short human investigation checklist tied to the supplied evidence. Never recommend a replacement for an uncertain payout.");
        for(var suggestion:suggestions.entrySet())if(counts.getOrDefault(suggestion.getKey(),0L)>0 && !records.existsByRecordKey("LESSON:"+day+":"+suggestion.getKey()))
            records.save(new AiRecord("LESSON","PENDING","LESSON:"+day+":"+suggestion.getKey(),null,null,
                encode(Map.of("day",day.toString(),"category",suggestion.getKey(),"feedbackCount",counts.get(suggestion.getKey()),"guidance",suggestion.getValue()))));
        return entry(records.save(new AiRecord("DAILY","COMPLETE",key,null,null,encode(Map.of("day",day.toString(),"feedbackCounts",counts,"feedbackTotal",total,
            "complete",feedback.size()==total,"message","Local feedback review. Proposed lessons require administrator approval. No model training or financial action occurred.")))));
    }
}
