package com.thang.user.service.user;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PasswordPolicyTest {
    @Test void enforcesStrengthAndUtf8ByteLimit() {
        assertTrue(PasswordPolicy.valid("Strong-pass9"));
        assertTrue(PasswordPolicy.valid("Aa1!"+"x".repeat(68)));
        assertFalse(PasswordPolicy.valid("Aa1!"+"x".repeat(69)));
        assertFalse(PasswordPolicy.valid("Aa1!"+"日".repeat(23)));
        for (String password : new String[]{"SHORT1!", "weakpass1!", "UPPERCASE1!", "NoDigits!", "NoSymbols1"}) {
            assertFalse(PasswordPolicy.valid(password));
        }
        assertFalse(PasswordPolicy.valid(null));
    }
}
