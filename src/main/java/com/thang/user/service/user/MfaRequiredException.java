package com.thang.user.service.user;
public final class MfaRequiredException extends RuntimeException {
 private final String challenge;
 public MfaRequiredException(String challenge){super("Cần xác thực hai bước");this.challenge=challenge;}
 public String challenge(){return challenge;}
}
