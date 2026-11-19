package seekfactory.axoraa.services;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import seekfactory.axoraa.entity.User;
import seekfactory.axoraa.exceptions.BadRequestException;
import seekfactory.axoraa.exceptions.DuplicateResourceException;
import seekfactory.axoraa.repository.UserRepository;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/** Syntax/uniqueness only: a phone number is NOT proof of ownership or manufacturer verification. */
@Service
@RequiredArgsConstructor
public class PhoneNumberService {
    private static final PhoneNumberUtil PHONES = PhoneNumberUtil.getInstance();
    private static final int CHINA_COUNTRY_CODE = 86;
    private final UserRepository users;

    /** New writes require an explicit international prefix; never infer it from the server location. */
    public String normalize(String input, boolean required) {
        if (input == null || input.isBlank()) {
            if (required) throw new BadRequestException("A phone number with country code is required");
            return null;
        }
        String value = input.trim();
        if (value.length() > 50 || !value.matches("\\+[0-9 ()\\-.]+")) {
            throw new BadRequestException("Enter a valid international phone number with country code");
        }
        String normalized = parse(value, null);
        if (normalized == null) throw new BadRequestException("Enter a valid international phone number with country code");
        return normalized;
    }

    /** Signup policy: manufacturers register with a China (+86) number, buyers with any other country's. */
    public void requireSignupCountry(String phone, boolean manufacturer) {
        if (phone == null) return;
        boolean china;
        try {
            china = PHONES.parse(phone, null).getCountryCode() == CHINA_COUNTRY_CODE;
        } catch (NumberParseException ex) {
            throw new BadRequestException("Enter a valid international phone number with country code");
        }
        if (manufacturer && !china) {
            throw new BadRequestException("Manufacturers must register with a China (+86) phone number");
        }
        if (!manufacturer && china) {
            throw new BadRequestException("Buyers cannot register with a China (+86) phone number");
        }
    }

    public void requireAvailable(String phone, String currentUserId) {
        if (phone != null && matchingIds(phone).stream().anyMatch(id -> !id.equals(currentUserId))) {
            throw new DuplicateResourceException("Account", "phone", "already registered");
        }
        // Existing users.phone UNIQUE is the race-safe final guard. Every new write uses E.164.
    }

    public Optional<User> findExisting(String phone) {
        Set<String> ids = matchingIds(phone);
        if (ids.size() > 1) {
            // Never choose or merge an identity when legacy numbers collide after normalization.
            throw new BadRequestException("Please sign in with email and contact support about this phone number");
        }
        return ids.stream().findFirst().flatMap(users::findById);
    }

    private Set<String> matchingIds(String phone) {
        Set<String> ids = new LinkedHashSet<>();
        users.findByPhone(phone).ifPresent(user -> ids.add(user.getId()));
        try {
            String national = PHONES.getNationalSignificantNumber(PHONES.parse(phone, null));
            for (UserRepository.PhoneCandidate candidate : users.findPhoneCandidates(national)) {
                String legacy = candidate.getPhone().trim();
                if (legacy.startsWith("00")) legacy = "+" + legacy.substring(2);
                String canonical = parse(legacy, region(candidate.getCountry()));
                if (phone.equals(canonical)) ids.add(candidate.getId());
            }
        } catch (NumberParseException ignored) {
            throw new BadRequestException("Invalid phone number");
        }
        return ids;
    }

    private static String parse(String value, String region) {
        try {
            var number = PHONES.parse(value, region);
            if (number.hasExtension() || !PHONES.isValidNumber(number)) return null;
            return PHONES.format(number, PhoneNumberUtil.PhoneNumberFormat.E164);
        } catch (NumberParseException ex) {
            return null;
        }
    }

    private static String region(String country) {
        if (country == null || country.isBlank()) return null;
        for (String code : Locale.getISOCountries()) {
            if (code.equalsIgnoreCase(country.trim()) || new Locale("", code)
                    .getDisplayCountry(Locale.ENGLISH).equalsIgnoreCase(country.trim())) return code;
        }
        return null; // Do not guess the country of legacy national-only numbers.
    }
}
