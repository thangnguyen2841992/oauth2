package com.thang.user.service.user;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/** One active session per user in the shared SQL store. The no-arg constructor is a pure unit-test fixture. */
@Service
public class SessionService {
    private final com.thang.user.repository.AuthSessionRepository store;
    private final SessionTokenCipher cipher;
    /** Pure unit-test constructor; Spring always uses the shared-store constructor. */
    public SessionService(){store=null;cipher=null;}
    @org.springframework.beans.factory.annotation.Autowired
    public SessionService(com.thang.user.repository.AuthSessionRepository store,SessionTokenCipher cipher){this.store=store;this.cipher=cipher;}
    private final ConcurrentHashMap<String, Session> sessions = new ConcurrentHashMap<>();
    @Value("${jwt.admin-idle-timeout:1800000}")
    private long adminIdleTimeout = 1800000;

    @org.springframework.transaction.annotation.Transactional
    public void create(String userId, String sid, String token, long expires, boolean admin) {
        long now = System.currentTimeMillis();
        if(store!=null){store.ensure(userId);var row=store.lock(userId).orElseThrow();row.setSid(sid);row.setEncryptedRefresh(cipher.encrypt(userId,token));row.setCurrentHash(hash(token));row.setPreviousHash(null);row.setRotateAfter(0);row.setExpiresAt(expires);row.setIdleUntil(admin?Math.min(expires,now+adminIdleTimeout):expires);row.setAdminSession(admin);return;}
        sessions.put(userId, new Session(sid, token, hash(token), null, 0, expires,
                admin ? Math.min(expires, now + adminIdleTimeout) : expires, admin));
    }

    @org.springframework.transaction.annotation.Transactional(readOnly=true)
    public String getSession(String userId) {
        if(store!=null)return store.deadline(userId).filter(d->valid(d,System.currentTimeMillis())).map(com.thang.user.repository.AuthSessionRepository.Deadline::getSid).orElse(null);
        Session session = sessions.get(userId);
        if (session == null) return null;
        long now = System.currentTimeMillis();
        if (session.expires <= now || session.idle <= now) {
            sessions.remove(userId, session);
            return null;
        }
        return session.sid;
    }

    @org.springframework.transaction.annotation.Transactional
    public void removeSession(String userId) {
        if(store!=null){if(userId!=null)store.revoke(userId,null);return;}
        if (userId != null) sessions.remove(userId);
    }

    @org.springframework.transaction.annotation.Transactional
    public void removeSession(String userId, String sid) {
        if(store!=null){if(userId!=null&&sid!=null)store.revoke(userId,sid);return;}
        if (userId != null && sid != null) sessions.computeIfPresent(userId,
                (ignored, session) -> sid.equals(session.sid) ? null : session);
    }

    @org.springframework.transaction.annotation.Transactional
    public boolean isValidSession(String userId, String sid) {
        return deadline(userId, sid, false) != null;
    }

    @org.springframework.transaction.annotation.Transactional
    public Long deadline(String userId, String sid, boolean activity) {
        if (userId == null || sid == null) return null;
        long now = System.currentTimeMillis();
        if(store!=null){var d=store.deadline(userId).orElse(null);if(d==null||!sid.equals(d.getSid())||!valid(d,now))return null;if(!activity)return Math.min(d.getExpiresAt(),d.getIdleUntil());var row=store.lock(userId).orElse(null);if(row==null||!sid.equals(row.getSid())||row.getExpiresAt()<=now||row.getIdleUntil()<=now)return null;if(row.isAdminSession())row.setIdleUntil(Math.min(row.getExpiresAt(),now+adminIdleTimeout));return Math.min(row.getExpiresAt(),row.getIdleUntil());}
        if (!activity) {
            Session session = sessions.get(userId);
            if (session == null || !sid.equals(session.sid)) return null;
            if (session.expires <= now || session.idle <= now) {
                sessions.remove(userId, session);
                return null;
            }
            // Avoid a map write and per-user lock on every session validation.
            return sessions.get(userId) == session ? Math.min(session.expires, session.idle) : null;
        }
        AtomicReference<Long> result = new AtomicReference<>();
        sessions.computeIfPresent(userId, (ignored, session) -> {
            if (!sid.equals(session.sid)) return session;
            if (session.expires <= now || session.idle <= now) return null;
            long idle = activity && session.admin ? Math.min(session.expires, now + adminIdleTimeout) : session.idle;
            result.set(Math.min(session.expires, idle));
            return idle == session.idle ? session : session.withIdle(idle);
        });
        return result.get();
    }

    @org.springframework.transaction.annotation.Transactional
    public String rotate(String userId, String sid, String oldToken, String newToken) {
        if (userId == null || sid == null || oldToken == null || newToken == null) return null;
        long now = System.currentTimeMillis();
        String presented = hash(oldToken);
        if(store!=null){var row=store.lock(userId).orElse(null);if(row==null||!sid.equals(row.getSid()))return null;if(row.getExpiresAt()<=now||row.getIdleUntil()<=now){store.delete(row);return null;}if(presented.equals(row.getCurrentHash())){if(now<row.getRotateAfter())return cipher.decrypt(userId,row.getEncryptedRefresh());row.setEncryptedRefresh(cipher.encrypt(userId,newToken));row.setPreviousHash(row.getCurrentHash());row.setCurrentHash(hash(newToken));row.setRotateAfter(now+10000);return newToken;}if(now<row.getRotateAfter()&&presented.equals(row.getPreviousHash()))return cipher.decrypt(userId,row.getEncryptedRefresh());store.delete(row);return null;}
        AtomicReference<String> result = new AtomicReference<>();
        sessions.computeIfPresent(userId, (ignored, session) -> {
            if (!sid.equals(session.sid)) return session;
            if (session.expires <= now || session.idle <= now) return null;
            if (presented.equals(session.hash)) {
                if (now < session.rotateAfter) {
                    result.set(session.token);
                    return session;
                }
                result.set(newToken);
                return session.rotated(newToken, hash(newToken), session.hash, now + 10000);
            }
            if (now < session.rotateAfter && presented.equals(session.previousHash)) {
                result.set(session.token);
                return session;
            }
            // A stale refresh token outside the overlap revokes the session.
            return null;
        });
        return result.get();
    }

    @Scheduled(fixedDelay = 300000)
    @org.springframework.transaction.annotation.Transactional
    public void removeExpiredSessions() {
        long now = System.currentTimeMillis();
        if(store!=null){store.cleanup(now);return;}
        sessions.forEach((userId, session) -> {
            if (session.expires <= now || session.idle <= now) sessions.remove(userId, session);
        });
    }

    private static String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
    private static boolean valid(com.thang.user.repository.AuthSessionRepository.Deadline d,long now){return d.getActive()&&d.getExpiresAt()>now&&d.getIdleUntil()>now;}
    @org.springframework.transaction.annotation.Transactional
    public Long accessDeadline(String userId,String sid,java.util.List<?> roles,boolean activity){if(userId==null||sid==null||roles==null||roles.size()!=1)return null;if(store==null)return deadline(userId,sid,activity);long now=System.currentTimeMillis();var d=store.deadline(userId).orElse(null);if(d==null||!valid(d,now)||!sid.equals(d.getSid())||!d.getRole().equals(roles.get(0)))return null;if(!activity)return Math.min(d.getExpiresAt(),d.getIdleUntil());var row=store.lock(userId).orElse(null);if(row==null||!sid.equals(row.getSid())||row.getExpiresAt()<=now||row.getIdleUntil()<=now)return null;if(row.isAdminSession())row.setIdleUntil(Math.min(row.getExpiresAt(),now+adminIdleTimeout));return Math.min(row.getExpiresAt(),row.getIdleUntil());}
    @org.springframework.transaction.annotation.Transactional(readOnly=true)
    public boolean roleMatches(String userId,String sid,java.util.List<?> roles){if(roles==null||roles.size()!=1)return false;if(store==null)return isValidSession(userId,sid);return store.deadline(userId).filter(d->valid(d,System.currentTimeMillis())&&sid.equals(d.getSid())&&d.getRole().equals(roles.get(0))).isPresent();}
    @org.springframework.transaction.annotation.Transactional(readOnly=true)
    public java.util.List<String> invalidSessions(java.util.List<com.nihongo.security.RemoteSessionVerifier.SessionRef> refs){java.util.Map<String,com.thang.user.repository.AuthSessionRepository.Deadline> rows=new java.util.HashMap<>();store.deadlines(refs.stream().map(com.nihongo.security.RemoteSessionVerifier.SessionRef::userId).distinct().toList()).forEach(d->rows.put(d.getUserId(),d));long now=System.currentTimeMillis();return refs.stream().filter(r->{var d=rows.get(r.userId());return d==null||!valid(d,now)||!d.getSid().equals(r.sessionId())||r.roles()==null||r.roles().size()!=1||!d.getRole().equals(r.roles().get(0));}).map(r->r.userId()+":"+r.sessionId()).toList();}

    private record Session(String sid, String token, String hash, String previousHash,
                           long rotateAfter, long expires, long idle, boolean admin) {
        Session withIdle(long nextIdle) {
            return new Session(sid, token, hash, previousHash, rotateAfter, expires, nextIdle, admin);
        }
        Session rotated(String nextToken, String nextHash, String oldHash, long nextRotateAfter) {
            return new Session(sid, nextToken, nextHash, oldHash, nextRotateAfter, expires, idle, admin);
        }
    }
}
