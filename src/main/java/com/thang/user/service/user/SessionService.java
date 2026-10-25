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
    // Requests reaching this service were already checked by the gateway. Like the other services
    // (RemoteSessionVerifier), a confirmed session is reused for a few seconds instead of a DB query per request;
    // every session change made here (login, rotation, logout, revocation, cleanup) drops the cached answer at once.
    @Value("${auth.session-cache-ttl:5000}")
    private long confirmedTtl = 5000;
    private record Confirmed(String sid, Object role, long until, long deadline) {}
    private final ConcurrentHashMap<String, Confirmed> confirmed = new ConcurrentHashMap<>();
    private void forget(String userId) { if (userId != null) confirmed.remove(userId); }
    private void remember(String userId, String sid, com.thang.user.repository.AuthSessionRepository.Deadline d, long now) {
        long deadline = Math.min(d.getExpiresAt(), d.getIdleUntil());
        if (confirmedTtl > 0) confirmed.put(userId, new Confirmed(sid, d.getRole(), Math.min(now + confirmedTtl, deadline), deadline));
    }
    // Idle limits by role. ADMIN and STAFF are extended only by real interactions (POST /api/auth/activity), never by
    // token refresh, so an open but unattended tab still expires. USER is extended by each refresh, i.e. while the
    // learner keeps using the site, up to the absolute lifetime (JwtService.sessionDuration).
    @Value("${jwt.admin-idle-timeout:1800000}")
    private long adminIdleTimeout = 1800000;
    @Value("${jwt.staff-idle-timeout:7200000}")
    private long staffIdleTimeout = 7_200_000;
    @Value("${jwt.user-idle-timeout:604800000}")
    private long userIdleTimeout = 604_800_000;
    // How long the previous refresh token is still answered with its successor (parallel tabs, a lost response
    // retried on a slow mobile network). Outside this window, presenting it revokes the session.
    @Value("${jwt.refresh-reuse-grace-ms:30000}")
    private long reuseGrace = 30_000;

    long idleTimeout(String role) {
        return "ADMIN".equals(role) ? adminIdleTimeout : "STAFF".equals(role) ? staffIdleTimeout : userIdleTimeout;
    }
    private static boolean interactive(String role) { return "ADMIN".equals(role) || "STAFF".equals(role); }

    @org.springframework.transaction.annotation.Transactional
    public void create(String userId, String sid, String token, long expires, boolean admin) {
        create(userId, sid, token, expires, admin ? "ADMIN" : "USER");
    }

    @org.springframework.transaction.annotation.Transactional
    public void create(String userId, String sid, String token, long expires, String role) {
        forget(userId);
        long now = System.currentTimeMillis();
        long idle = Math.min(expires, now + idleTimeout(role));
        // admin_session marks sessions with an interaction-based idle limit (ADMIN and STAFF).
        if(store!=null){store.ensure(userId);var row=store.lock(userId).orElseThrow();row.setSid(sid);row.setEncryptedRefresh(cipher.encrypt(userId,token));row.setCurrentHash(hash(token));row.setPreviousHash(null);row.setRotateAfter(0);row.setExpiresAt(expires);row.setIdleUntil(idle);row.setAdminSession(interactive(role));return;}
        sessions.put(userId, new Session(sid, token, hash(token), null, 0, expires, idle, interactive(role), idleTimeout(role)));
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
        forget(userId);
        if(store!=null){if(userId!=null)store.revoke(userId,null);return;}
        if (userId != null) sessions.remove(userId);
    }

    @org.springframework.transaction.annotation.Transactional
    public void removeSession(String userId, String sid) {
        forget(userId);
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
        if(store!=null){var d=store.deadline(userId).orElse(null);if(d==null||!sid.equals(d.getSid())||!valid(d,now))return null;if(!activity)return Math.min(d.getExpiresAt(),d.getIdleUntil());var row=store.lock(userId).orElse(null);if(row==null||!sid.equals(row.getSid())||row.getExpiresAt()<=now||row.getIdleUntil()<=now)return null;if(row.isAdminSession())row.setIdleUntil(Math.min(row.getExpiresAt(),now+idleTimeout(d.getRole())));return Math.min(row.getExpiresAt(),row.getIdleUntil());}
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
            long idle = activity && session.admin ? Math.min(session.expires, now + session.idleTimeout) : session.idle;
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
        forget(userId);
        if(store!=null){var row=store.lock(userId).orElse(null);if(row==null||!sid.equals(row.getSid()))return null;if(row.getExpiresAt()<=now||row.getIdleUntil()<=now){store.delete(row);return null;}if(presented.equals(row.getCurrentHash())){if(now<row.getRotateAfter())return cipher.decrypt(userId,row.getEncryptedRefresh());row.setEncryptedRefresh(cipher.encrypt(userId,newToken));row.setPreviousHash(row.getCurrentHash());row.setCurrentHash(hash(newToken));row.setRotateAfter(now+reuseGrace);if(!row.isAdminSession())row.setIdleUntil(Math.min(row.getExpiresAt(),now+userIdleTimeout));return newToken;}if(now<row.getRotateAfter()&&presented.equals(row.getPreviousHash()))return cipher.decrypt(userId,row.getEncryptedRefresh());store.delete(row);return null;}
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
                return session.rotated(newToken, hash(newToken), session.hash, now + reuseGrace,
                        session.admin ? session.idle : Math.min(session.expires, now + session.idleTimeout));
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
        confirmed.clear();
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
    /**
     * A recently confirmed deadline for this exact session and role, or null. Not transactional, so a hit costs no
     * pooled connection; the gateway and every service validating the same session within a few seconds share it.
     */
    public Long confirmedDeadline(String userId, String sid, java.util.List<?> roles) {
        if (store == null || userId == null || sid == null || roles == null || roles.size() != 1) return null;
        var hit = confirmed.get(userId);
        return hit != null && hit.until() > System.currentTimeMillis() && hit.sid().equals(sid) && hit.role().equals(roles.get(0))
                ? hit.deadline() : null;
    }

    @org.springframework.transaction.annotation.Transactional
    public Long accessDeadline(String userId,String sid,java.util.List<?> roles,boolean activity){if(userId==null||sid==null||roles==null||roles.size()!=1)return null;if(store==null)return deadline(userId,sid,activity);long now=System.currentTimeMillis();if(activity)forget(userId);var d=store.deadline(userId).orElse(null);if(d==null||!valid(d,now)||!sid.equals(d.getSid())||!d.getRole().equals(roles.get(0)))return null;if(!activity){remember(userId,sid,d,now);return Math.min(d.getExpiresAt(),d.getIdleUntil());}var row=store.lock(userId).orElse(null);if(row==null||!sid.equals(row.getSid())||row.getExpiresAt()<=now||row.getIdleUntil()<=now)return null;if(row.isAdminSession())row.setIdleUntil(Math.min(row.getExpiresAt(),now+idleTimeout(d.getRole())));return Math.min(row.getExpiresAt(),row.getIdleUntil());}
    // Not transactional: a cache hit must not borrow a pooled connection; the repository query has its own.
    public boolean roleMatches(String userId,String sid,java.util.List<?> roles){
        if(roles==null||roles.size()!=1||userId==null||sid==null)return false;
        if(store==null)return isValidSession(userId,sid);
        long now=System.currentTimeMillis();
        var hit=confirmed.get(userId);
        if(hit!=null&&hit.until()>now&&hit.sid().equals(sid)&&hit.role().equals(roles.get(0)))return true;
        var d=store.deadline(userId).filter(x->valid(x,now)&&sid.equals(x.getSid())&&x.getRole().equals(roles.get(0))).orElse(null);
        if(d==null){confirmed.remove(userId,hit);return false;}
        remember(userId,sid,d,now);
        return true;
    }
    @org.springframework.transaction.annotation.Transactional(readOnly=true)
    public java.util.List<String> invalidSessions(java.util.List<com.nihongo.security.RemoteSessionVerifier.SessionRef> refs){java.util.Map<String,com.thang.user.repository.AuthSessionRepository.Deadline> rows=new java.util.HashMap<>();store.deadlines(refs.stream().map(com.nihongo.security.RemoteSessionVerifier.SessionRef::userId).distinct().toList()).forEach(d->rows.put(d.getUserId(),d));long now=System.currentTimeMillis();return refs.stream().filter(r->{var d=rows.get(r.userId());return d==null||!valid(d,now)||!d.getSid().equals(r.sessionId())||r.roles()==null||r.roles().size()!=1||!d.getRole().equals(r.roles().get(0));}).map(r->r.userId()+":"+r.sessionId()).toList();}

    // admin = interaction-based idle limit (ADMIN/STAFF); idleTimeout = that role's idle limit.
    private record Session(String sid, String token, String hash, String previousHash,
                           long rotateAfter, long expires, long idle, boolean admin, long idleTimeout) {
        Session withIdle(long nextIdle) {
            return new Session(sid, token, hash, previousHash, rotateAfter, expires, nextIdle, admin, idleTimeout);
        }
        Session rotated(String nextToken, String nextHash, String oldHash, long nextRotateAfter, long nextIdle) {
            return new Session(sid, nextToken, nextHash, oldHash, nextRotateAfter, expires, nextIdle, admin, idleTimeout);
        }
    }
}
