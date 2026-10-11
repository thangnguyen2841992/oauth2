package com.thang.user.service.user;
import com.thang.user.model.entity.User;
import com.nihongo.security.*;
import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import javax.crypto.SecretKey;
import java.security.*;
import java.util.*;

@Service
public class JwtService {
 private final SecretKey secretKey;
 private final PrivateKey privateKey;
 private final PublicKey publicKey;
 private final String issuer,audience;
 private final long accessExpiration,refreshExpiration;
 @Value("${jwt.admin-refresh-expiration:28800000}") private long adminRefreshExpiration=28800000;
 @Autowired public JwtService(Environment env){
  boolean legacy=SecurityJwtDecoders.legacy(env);
  secretKey=legacy?Keys.hmacShaKeyFor(Decoders.BASE64.decode(env.getRequiredProperty("jwt.secret"))):null;
  privateKey=legacy?null:SecurityKeyFiles.privateKey(env.getProperty("security.jwt.private-key-path",".local/security/jwt-private.pem"));
  publicKey=legacy?null:SecurityKeyFiles.publicKey(env.getProperty("security.jwt.public-key-path",".local/security/jwt-public.pem"));
  if(privateKey instanceof java.security.interfaces.RSAPrivateKey rsa&&publicKey instanceof java.security.interfaces.RSAPublicKey pub&&!rsa.getModulus().equals(pub.getModulus()))throw new IllegalStateException("RSA public and private keys do not match");
  if(publicKey instanceof java.security.interfaces.RSAPublicKey rsa&&rsa.getModulus().bitLength()<2048)throw new IllegalStateException("RSA key must contain at least 2048 bits");
  issuer=env.getProperty("security.jwt.issuer","nihongo-auth");audience=env.getProperty("security.jwt.audience","nihongo-api");
  accessExpiration=env.getProperty("jwt.access-expiration",Long.class,300000L);refreshExpiration=env.getProperty("jwt.refresh-expiration",Long.class,604800000L);
 }
 /** Explicit HMAC fixture constructor, not used by Spring. */
 public JwtService(String secret,long accessExpiration,long refreshExpiration){secretKey=Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));privateKey=null;publicKey=null;issuer="nihongo-auth";audience="nihongo-api";this.accessExpiration=accessExpiration;this.refreshExpiration=refreshExpiration;}
 public long sessionDuration(User user){return "ADMIN".equals(user.getRoleName())?adminRefreshExpiration:refreshExpiration;}
 private JwtBuilder base(){return Jwts.builder().issuer(issuer).claim("aud",List.of(audience)).issuedAt(new Date());}
 private String sign(JwtBuilder builder){return (privateKey==null?builder.signWith(secretKey,Jwts.SIG.HS256):builder.signWith(privateKey,Jwts.SIG.RS256)).compact();}
 public String generateAccessToken(User user,String sid){return generateAccessToken(user,sid,System.currentTimeMillis()+accessExpiration);}
 public String generateAccessToken(User user,String sid,long deadline){return sign(base().subject(user.getUserId()).claim("email",user.getEmail()).claim("name",user.getFirstName()+" "+user.getLastName()).claim("roles",List.of(user.getRoleName())).claim("sessionId",sid).claim("type","access").expiration(new Date(Math.min(deadline,System.currentTimeMillis()+accessExpiration))));}
 public String generateRefreshToken(User user,String sid){return generateRefreshToken(user,sid,System.currentTimeMillis()+sessionDuration(user));}
 public String generateRefreshToken(User user,String sid,long deadline){return sign(base().subject(user.getUserId()).claim("sessionId",sid).claim("type","refresh").id(UUID.randomUUID().toString()).expiration(new Date(deadline)));}
 public Claims parseAndValidate(String token){
  if(token==null||token.isBlank())throw new IllegalArgumentException("JWT không hợp lệ");
  try{JwtParserBuilder parser=Jwts.parser();if(publicKey==null)parser.verifyWith(secretKey);else parser.verifyWith(publicKey);
   var signed=parser.build().parseSignedClaims(token);var claims=signed.getPayload();
   if(!(privateKey==null?"HS256":"RS256").equals(signed.getHeader().getAlgorithm())||!issuer.equals(claims.getIssuer())||claims.getAudience()==null||!claims.getAudience().contains(audience))throw new IllegalArgumentException("JWT không hợp lệ");
   return claims;
  }catch(Exception e){throw new IllegalArgumentException("JWT không hợp lệ hoặc đã hết hạn",e);}
 }
 public String getUserId(String token){return parseAndValidate(token).getSubject();}
 public String getSessionId(String token){return parseAndValidate(token).get("sessionId",String.class);}
 public String getTokenType(String token){return parseAndValidate(token).get("type",String.class);}
 public String generateGoogleSetupToken(String email,String googleId,String firstName,String lastName){return sign(base().claim("email",email).claim("googleId",googleId).claim("firstName",firstName).claim("lastName",lastName).claim("type","google_setup").expiration(new Date(System.currentTimeMillis()+600000)));}
 public Claims parseGoogleSetupToken(String token){var claims=parseAndValidate(token);if(!"google_setup".equals(claims.get("type",String.class)))throw new IllegalArgumentException("Invalid setup token");return claims;}
}
