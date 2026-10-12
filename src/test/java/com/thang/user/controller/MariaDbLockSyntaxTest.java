package com.thang.user.controller;

import org.hibernate.dialect.DatabaseVersion;
import org.hibernate.dialect.MariaDBDialect;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MariaDbLockSyntaxTest {
    @Test void pessimisticLocksDoNotIncludeTheUnsupportedOfTableAlias() {
        var dialect = new MariaDBDialect(DatabaseVersion.make(10, 11));
        String lock = dialect.getForUpdateString("aao1_0").trim();
        assertEquals("for update", lock);
        assertFalse(lock.contains("aao1_0"));
    }
}
