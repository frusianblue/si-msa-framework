package com.company.framework.mfa.store;

import com.company.framework.mfa.core.MfaMethod;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 단일 인스턴스/로컬 전용 등록 저장소. 재기동 시 휘발하므로 운영 다중 인스턴스에는 부적합(jdbc 사용).
 */
public class InMemoryMfaEnrollmentStore implements MfaEnrollmentStore {

    private final Map<String, MfaEnrollment> map = new ConcurrentHashMap<>();

    private static String key(String userId, MfaMethod method) {
        return userId + "|" + method.name();
    }

    @Override
    public Optional<MfaEnrollment> find(String userId, MfaMethod method) {
        return Optional.ofNullable(map.get(key(userId, method)));
    }

    @Override
    public List<MfaEnrollment> findByUser(String userId) {
        List<MfaEnrollment> result = new ArrayList<>();
        for (MfaEnrollment e : map.values()) {
            if (e.userId().equals(userId)) {
                result.add(e);
            }
        }
        return result;
    }

    @Override
    public void save(MfaEnrollment enrollment) {
        map.put(key(enrollment.userId(), enrollment.method()), enrollment);
    }

    @Override
    public void delete(String userId, MfaMethod method) {
        map.remove(key(userId, method));
    }
}
