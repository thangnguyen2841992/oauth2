package com.thang.user.service.user;
import org.junit.jupiter.api.Test;import java.nio.charset.StandardCharsets;import static org.junit.jupiter.api.Assertions.*;
class TotpCodesTest {
 @Test void followsRfc6238Sha1Vectors(){String secret=TotpCodes.encode("12345678901234567890".getBytes(StandardCharsets.US_ASCII));assertEquals("287082",TotpCodes.code(secret,59/30));assertEquals("081804",TotpCodes.code(secret,1111111109L/30));assertEquals("050471",TotpCodes.code(secret,1111111111L/30));assertEquals("353130",TotpCodes.code(secret,20000000000L/30));}
 @Test void preventsReusingAcceptedCountersAndRejectsMalformedCodes(){String secret=TotpCodes.secret();long counter=System.currentTimeMillis()/30000;String code=TotpCodes.code(secret,counter);assertEquals(counter,TotpCodes.verify(secret,code,counter*30000,-1));assertEquals(-1,TotpCodes.verify(secret,code,counter*30000,counter));assertEquals(-1,TotpCodes.verify(secret,"000000000000",counter*30000,-1));assertArrayEquals(TotpCodes.decode(secret),TotpCodes.decode(secret.toLowerCase(java.util.Locale.ROOT)));}
}
