package com.savex.money;
import com.savex.exception.BadRequestException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
@Service @RequiredArgsConstructor
public class AiBudget {
    private final AiRecordRepository records;
    private final AiSettings settings;
    @Transactional public void reserve() {
        String key="AI_BUDGET:"+LocalDate.now(ZoneId.of("Africa/Lagos"));
        UUID id=UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8));
        AiRecord row=records.lock(id).orElse(null);
        if(row==null){row=new AiRecord("BUDGET","ACTIVE",key,null,null,"0");row.setId(id);records.saveAndFlush(row);}
        int used=Integer.parseInt(row.getPayload());
        if(used>=settings.getDailyLimit())throw new BadRequestException("Daily AI request limit reached. Deterministic reports remain available.");
        row.setPayload(Integer.toString(used+1));records.save(row);
    }
}
