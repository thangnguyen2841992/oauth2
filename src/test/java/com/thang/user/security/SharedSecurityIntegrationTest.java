package com.thang.user.security;
import com.thang.user.UserServiceApplication;
import com.thang.user.model.entity.User;
import com.thang.user.repository.*;
import com.thang.user.service.user.*;
import com.nihongo.security.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.net.*;
import java.net.http.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes=UserServiceApplication.class,webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
 "spring.config.import=","spring.datasource.url=jdbc:h2:mem:shared_security;MODE=MySQL;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000","spring.datasource.driver-class-name=org.h2.Driver","spring.datasource.username=sa","spring.datasource.password=",
 "spring.jpa.hibernate.ddl-auto=create-drop","spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect","spring.jpa.show-sql=false","spring.cloud.discovery.enabled=false","eureka.client.enabled=false","spring.kafka.admin.auto-create=false","app.bootstrap.accounts-enabled=false",
 "security.jwt.mode=rsa","security.jwt.allow-legacy-hmac=false","security.jwt.validate-claims=true","security.session-validation.enabled=true","security.origin-check.enabled=true","COOKIE_SECURE=false","FRONTEND_ORIGIN=http://localhost:5173"
})
class SharedSecurityIntegrationTest {
 @Autowired SessionService sessions;@Autowired SessionTokenCipher cipher;@Autowired AuthSessionRepository store;@Autowired IUserRepository users;@Autowired PasswordEncoder passwords;
 @Autowired JwtService jwt;@Autowired TokenService tokens;@Autowired RateLimitService rates;@Autowired AuthRateBucketRepository buckets;@Autowired PlatformTransactionManager transactions;@Autowired InternalCredentials internal;
 @Value("${local.server.port}") int port;
 User user;
 @BeforeEach void setup(){store.deleteAll();buckets.deleteAll();users.deleteAll();user=new User();user.setUserId(UUID.randomUUID().toString());user.setEmail("security@example.com");user.setFirstName("Security");user.setLastName("Test");user.setRoleName("STAFF");user.setActive(true);user.setPassword(passwords.encode("Strong1!pass"));users.saveAndFlush(user);}
 URI uri(String path){return URI.create("http://127.0.0.1:"+port+path);}
 HttpResponse<String> request(String method,String path,String body,String token,String origin)throws Exception{var b=HttpRequest.newBuilder(uri(path));if(token!=null)b.header("Authorization","Bearer "+token);if(origin!=null)b.header("Origin",origin);if(body!=null)b.header("Content-Type","application/json");return HttpClient.newHttpClient().send(b.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());}
 @Test void rsaTokenAndDirectApiRejectRevokedSessionAndChangedRole()throws Exception{
  var token=tokens.generateToken(user,"sid-1").getAccess_token();String header=new String(Base64.getUrlDecoder().decode(token.split("\\.")[0]),java.nio.charset.StandardCharsets.UTF_8);assertTrue(header.contains("RS256"));
  assertEquals(200,request("GET","/api/users/staff-directory",null,token,null).statusCode());
  user.setRoleName("USER");users.saveAndFlush(user);assertEquals(401,request("GET","/api/users/staff-directory",null,token,null).statusCode());
  user.setRoleName("STAFF");users.saveAndFlush(user);sessions.removeSession(user.getUserId());assertEquals(401,request("GET","/api/users/staff-directory",null,token,null).statusCode());
 }
 @Test void localHttpLoginCookiesWorkWhileMissingAndHostileOriginsAreBlocked()throws Exception{
  String body="{\"email\":\"security@example.com\",\"password\":\"Strong1!pass\"}";
  assertEquals(403,request("POST","/api/auth/login",body,null,null).statusCode());assertEquals(403,request("POST","/api/auth/login",body,null,"http://evil.example").statusCode());
  var r=request("POST","/api/auth/login",body,null,"http://localhost:5173");assertEquals(200,r.statusCode(),r.body());var cookies=r.headers().allValues("set-cookie");assertEquals(2,cookies.size());assertTrue(cookies.stream().allMatch(c->c.contains("HttpOnly")&&c.contains("SameSite=Lax")&&!c.contains("Secure")));
  String access=cookies.stream().filter(c->c.startsWith("accessToken=")).findFirst().orElseThrow().split(";",2)[0];var authenticated=HttpClient.newHttpClient().send(HttpRequest.newBuilder(uri("/api/auth/checkLogin")).header("Cookie",access).GET().build(),HttpResponse.BodyHandlers.ofString());assertEquals(200,authenticated.statusCode(),authenticated.body());
 }
 @Test void secondInstanceReadsPersistentSessionAndRefreshIsEncrypted(){
  String token=jwt.generateRefreshToken(user,"sid-1");sessions.create(user.getUserId(),"sid-1",token,System.currentTimeMillis()+60000,false);
  var second=new SessionService(store,cipher);assertEquals("sid-1",second.getSession(user.getUserId()));var row=store.findById(user.getUserId()).orElseThrow();assertNotEquals(token,row.getEncryptedRefresh());assertFalse(row.getEncryptedRefresh().contains(token));assertEquals(token,cipher.decrypt(user.getUserId(),row.getEncryptedRefresh()));
  sessions.removeSession(user.getUserId(),"other");assertEquals("sid-1",second.getSession(user.getUserId()));sessions.removeSession(user.getUserId(),"sid-1");assertNull(second.getSession(user.getUserId()));
 }
 @Test void concurrentRefreshAcrossTwoInstancesHasOneSuccessorAndReplayRevokes()throws Exception{
  sessions.create(user.getUserId(),"sid-1","old",System.currentTimeMillis()+60000,false);var second=new SessionService(store,cipher);var tx=new TransactionTemplate(transactions);var pool=Executors.newFixedThreadPool(4);var start=new CountDownLatch(1);
  try{List<Future<String>> results=new ArrayList<>();for(int i=0;i<12;i++){int n=i;results.add(pool.submit(()->{start.await();return n%2==0?sessions.rotate(user.getUserId(),"sid-1","old","next-"+n):tx.execute(s->second.rotate(user.getUserId(),"sid-1","old","next-"+n));}));}start.countDown();Set<String> successors=new HashSet<>();for(var r:results)successors.add(r.get(15,TimeUnit.SECONDS));assertEquals(1,successors.size());assertFalse(successors.contains(null));assertNull(sessions.rotate(user.getUserId(),"sid-1","stale","replay"));assertNull(second.getSession(user.getUserId()));}finally{pool.shutdownNow();}
 }
 @Test void sharedQuotaSurvivesNewServiceInstanceAndConcurrentRequestsCannotOverConsume()throws Exception{
  var second=new RateLimitService(buckets);var tx=new TransactionTemplate(transactions);var pool=Executors.newFixedThreadPool(4);var start=new CountDownLatch(1);
  try{List<Future<Boolean>> results=new ArrayList<>();for(int i=0;i<20;i++){int n=i;results.add(pool.submit(()->{start.await();var input=new RateLimitService.Limit("AI_USER",user.getUserId());return n%2==0?rates.consume(input).allowed():tx.execute(s->second.consume(input).allowed());}));}start.countDown();int passed=0;for(var r:results)if(r.get(15,TimeUnit.SECONDS))passed++;assertEquals(6,passed);assertFalse(tx.execute(s->second.consume(new RateLimitService.Limit("AI_USER",user.getUserId()))).allowed());}finally{pool.shutdownNow();}
 }
 @Test void internalEndpointsRequireInternalCredentialsAndSignedActiveToken()throws Exception{
  assertEquals(403,request("POST","/api/auth/internal/sessions/check","[]",null,null).statusCode());assertEquals(401,request("POST","/api/auth/internal/sessions/check","[]",null,"http://localhost:5173").statusCode());
  String token=tokens.generateToken(user,"sid-1").getAccess_token();var b=HttpRequest.newBuilder(uri("/api/auth/session/validate")).header("Authorization","Bearer "+token).GET();assertEquals(401,HttpClient.newHttpClient().send(b.build(),HttpResponse.BodyHandlers.discarding()).statusCode());
  var valid=b.header(InternalCredentials.HEADER,internal.value()).build();assertEquals(204,HttpClient.newHttpClient().send(valid,HttpResponse.BodyHandlers.discarding()).statusCode());sessions.removeSession(user.getUserId());assertEquals(401,HttpClient.newHttpClient().send(valid,HttpResponse.BodyHandlers.discarding()).statusCode());
 }
}
