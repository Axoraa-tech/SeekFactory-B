package seekfactory.axoraa.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import seekfactory.axoraa.entity.User;
import seekfactory.axoraa.exceptions.BadRequestException;
import seekfactory.axoraa.exceptions.DuplicateResourceException;
import seekfactory.axoraa.repository.UserRepository;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PhoneNumberServiceTest {
    private UserRepository users;
    private PhoneNumberService phones;

    @BeforeEach void setup() {
        users = mock(UserRepository.class);
        phones = new PhoneNumberService(users);
        when(users.findByPhone(anyString())).thenReturn(Optional.empty());
        when(users.findPhoneCandidates(anyString())).thenReturn(List.of());
    }

    @Test void normalizesInternationalNumbers() {
        assertEquals("+8613812345678", phones.normalize("+86 138 1234 5678", true));
        assertEquals("+919876543210", phones.normalize("+91 98765-43210", true));
        assertEquals("+442079460018", phones.normalize("+44 (20) 7946 0018", true));
    }

    @Test void requiresManufacturerPhoneButAllowsAbsentBuyerPhone() {
        assertThrows(BadRequestException.class, () -> phones.normalize(null, true));
        assertThrows(BadRequestException.class, () -> phones.normalize(" ", true));
        assertNull(phones.normalize(null, false));
        assertNull(phones.normalize("", false));
    }

    @Test void rejectsNationalOnlyInvalidAndExtensionInputs() {
        for (String value : List.of("13812345678", "+86", "+86123", "+91 9876543210 ext 1", "+1FLOWERS")) {
            assertThrows(BadRequestException.class, () -> phones.normalize(value, true));
        }
    }

    @Test void manufacturersSignUpWithChinaNumbersAndBuyersWithAnyOtherCountry() {
        assertDoesNotThrow(() -> phones.requireSignupCountry("+8613812345678", true));
        assertThrows(BadRequestException.class, () -> phones.requireSignupCountry("+919876543210", true));
        assertThrows(BadRequestException.class, () -> phones.requireSignupCountry("+85291234567", true));

        assertThrows(BadRequestException.class, () -> phones.requireSignupCountry("+8613812345678", false));
        assertDoesNotThrow(() -> phones.requireSignupCountry("+919876543210", false));
        assertDoesNotThrow(() -> phones.requireSignupCountry("+442079460018", false));
        assertDoesNotThrow(() -> phones.requireSignupCountry("+85291234567", false));
    }

    @Test void rejectsExistingNumberButAllowsSameAccount() {
        User user = User.builder().build();
        user.setId("one");
        when(users.findByPhone("+8613812345678")).thenReturn(Optional.of(user));
        assertThrows(DuplicateResourceException.class, () -> phones.requireAvailable("+8613812345678", null));
        assertDoesNotThrow(() -> phones.requireAvailable("+8613812345678", "one"));
    }

    @Test void detectsLegacyNationalNumberUsingStoredCountryWithoutRewritingIt() {
        var existing = candidate("one", "138 1234 5678", "China");
        when(users.findPhoneCandidates("13812345678")).thenReturn(List.of(existing));
        assertThrows(DuplicateResourceException.class, () -> phones.requireAvailable("+8613812345678", null));
        verify(users, never()).save(any());
    }

    @Test void detectsFormattedInternationalNumberWithoutCountry() {
        when(users.findPhoneCandidates("9876543210")).thenReturn(List.of(candidate("one", "+91 98765 43210", null)));
        assertThrows(DuplicateResourceException.class, () -> phones.requireAvailable("+919876543210", null));
    }

    @Test void doesNotGuessCountryOrConfuseMatchingNationalNumbersAcrossCountries() {
        when(users.findPhoneCandidates("9876543210")).thenReturn(List.of(candidate("one", "9876543210", null)));
        assertDoesNotThrow(() -> phones.requireAvailable("+919876543210", null));
    }

    @Test void refusesAmbiguousLegacyPhoneLogin() {
        when(users.findPhoneCandidates("13812345678")).thenReturn(List.of(
                candidate("one", "+86 13812345678", null), candidate("two", "13812345678", "CN")));
        assertThrows(BadRequestException.class, () -> phones.findExisting("+8613812345678"));
    }

    private UserRepository.PhoneCandidate candidate(String id, String phone, String country) {
        var candidate = mock(UserRepository.PhoneCandidate.class);
        when(candidate.getId()).thenReturn(id);
        when(candidate.getPhone()).thenReturn(phone);
        when(candidate.getCountry()).thenReturn(country);
        return candidate;
    }
}
