package com.savex.money;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
public interface CustomerReviewRepository extends JpaRepository<CustomerReview,UUID> {
    @org.springframework.data.jpa.repository.Query("select distinct r.userId from CustomerReview r where r.userId in :ids and r.decision='APPROVED' and r.providerMode='LIVE'")
    java.util.List<UUID> liveApprovedIds(@org.springframework.data.repository.query.Param("ids") java.util.Collection<UUID> ids);
    boolean existsByUserIdAndDecisionAndProviderMode(UUID user,String decision,String mode);
}
