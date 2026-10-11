package com.thang.user.service.user;
import javax.crypto.Mac;import javax.crypto.spec.SecretKeySpec;import java.nio.ByteBuffer;import java.security.*;import java.nio.charset.StandardCharsets;import java.util.*;
public final class TotpCodes {
 private static final String ALPHABET="ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
 private TotpCodes(){}
 public static String secret(){byte[] bytes=new byte[20];new SecureRandom().nextBytes(bytes);return encode(bytes);}
 static String encode(byte[] bytes){StringBuilder result=new StringBuilder();int bits=0,value=0;for(byte b:bytes){value=(value<<8)|(b&255);bits+=8;while(bits>=5){bits-=5;result.append(ALPHABET.charAt((value>>bits)&31));}}if(bits>0)result.append(ALPHABET.charAt((value<<(5-bits))&31));return result.toString();}
 static byte[] decode(String value){var result=new java.io.ByteArrayOutputStream();int bits=0,buffer=0;for(char c:value.toUpperCase(Locale.ROOT).toCharArray()){int index=ALPHABET.indexOf(c);if(index<0)throw new IllegalArgumentException("Invalid authenticator secret");buffer=(buffer<<5)|index;bits+=5;if(bits>=8){bits-=8;result.write((buffer>>bits)&255);}}return result.toByteArray();}
 public static String code(String secret,long counter){try{Mac mac=Mac.getInstance("HmacSHA1");mac.init(new SecretKeySpec(decode(secret),"HmacSHA1"));byte[] hash=mac.doFinal(ByteBuffer.allocate(8).putLong(counter).array());int offset=hash[hash.length-1]&15;int number=((hash[offset]&127)<<24)|((hash[offset+1]&255)<<16)|((hash[offset+2]&255)<<8)|(hash[offset+3]&255);return String.format(Locale.ROOT,"%06d",number%1000000);}catch(GeneralSecurityException e){throw new IllegalStateException("Authenticator unavailable",e);}}
 public static long verify(String secret,String code,long nowMillis,long previous){if(code==null||!code.matches("[0-9]{6}"))return -1;long counter=nowMillis/30000;for(long current=counter-1;current<=counter+1;current++)if(current>previous&&MessageDigest.isEqual(code(secret,current).getBytes(StandardCharsets.US_ASCII),code.getBytes(StandardCharsets.US_ASCII)))return current;return -1;}
}
