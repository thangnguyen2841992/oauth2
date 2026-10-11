package com.thang.user.service.user;
import com.thang.user.model.entity.AuthRateBucket;import com.thang.user.repository.AuthRateBucketRepository;import lombok.RequiredArgsConstructor;import org.springframework.stereotype.Service;import org.springframework.transaction.annotation.Transactional;import org.springframework.scheduling.annotation.Scheduled;import java.security.MessageDigest;import java.nio.charset.StandardCharsets;import java.util.HexFormat;
@Service @RequiredArgsConstructor
public class RateLimitService {
 private final AuthRateBucketRepository buckets;
 public record Limit(String category,String identity){}
 public record Result(boolean allowed,long retryAfter){}
 @Transactional public Result consumeAll(java.util.List<Limit> limits){boolean allowed=true;long retry=0;for(var limit:limits.stream().sorted(java.util.Comparator.comparing(l->l.category()+":"+l.identity())).toList()){var result=consume(limit);allowed&=result.allowed();retry=Math.max(retry,result.retryAfter());}return new Result(allowed,retry);}
 @Transactional public Result consume(Limit limit){
  if(limit.category()==null||limit.identity()==null||limit.identity().isBlank()||limit.identity().length()>320)throw new IllegalArgumentException("Invalid rate-limit identity");
  double capacity,refill;switch(limit.category()){case "LOGIN_IP"->{capacity=10;refill=1.0/6;}case "LOGIN_ACCOUNT","MFA_VERIFY_ACCOUNT"->{capacity=5;refill=1.0/60;}case "PASSWORD_RECOVERY_ACCOUNT"->{capacity=3;refill=1.0/1200;}case "REGISTER_IP"->{capacity=3;refill=1.0/120;}case "AUTH_IP"->{capacity=120;refill=2;}case "AI_USER"->{capacity=6;refill=0.1;}case "WALLET_USER"->{capacity=10;refill=1;}case "API_USER"->{capacity=100;refill=50;}case "API_IP"->{capacity=60;refill=10;}default->throw new IllegalArgumentException("Unknown rate-limit category");}
  String id;try{id=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest((limit.category()+":"+limit.identity()).getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}
  long now=System.currentTimeMillis();buckets.ensure(id,capacity,now,now+3600000);AuthRateBucket b=buckets.lock(id).orElseThrow();double tokens=Math.min(capacity,b.getTokens()+Math.max(0,now-b.getUpdatedAt())/1000d*refill);b.setUpdatedAt(Math.max(now,b.getUpdatedAt()));b.setExpiresAt(now+3600000);
  boolean allowed=tokens>=1;b.setTokens(allowed?tokens-1:tokens);return new Result(allowed,allowed?0:Math.max(1,(long)Math.ceil((1-tokens)/refill)));
 }
 @Scheduled(fixedDelay=300000) @Transactional public void cleanup(){buckets.cleanup(System.currentTimeMillis());}
}
