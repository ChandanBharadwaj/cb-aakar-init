package studio.aakar.api.admin.internal;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import studio.aakar.api.admin.StaffAccounts;
import studio.aakar.api.admin.StaffDto;
import studio.aakar.api.admin.StaffRole;
import studio.aakar.api.shared.ApiProblemException;

/**
 * Staff sign-in (bcrypt) and token authentication. On first start with an empty {@code staff_accounts} table the
 * seed owner {@code studio@aakar.local} is created with {@code aakar.admin.seed-password}; the hash is computed
 * here, never stored in SQL.
 */
@Service
class StaffAuthService implements StaffAccounts, SmartInitializingSingleton {

    static final String SEED_EMAIL = "studio@aakar.local";
    static final String SEED_NAME = "Aakar Studio";
    static final String STAFF_EXISTS = "staff_exists";
    private static final Logger log = LoggerFactory.getLogger(StaffAuthService.class);

    private final StaffAccountRepository accounts;
    private final StaffTokens tokens;
    private final AdminProperties properties;
    private final PasswordEncoder encoder = new BCryptPasswordEncoder();
    /** Checked when the email is unknown so a missing account costs the same time as a wrong password. */
    private final String decoyHash;
    private final TransactionTemplate transactions;
    private final Clock clock;

    @Autowired
    StaffAuthService(StaffAccountRepository accounts, AdminProperties properties, @Value("${aakar.identity.jwt-secret}") String jwtSecret,
            TransactionTemplate transactions, Clock clock) {
        this(accounts, new StaffTokens(jwtSecret), properties, transactions, clock);
    }

    StaffAuthService(StaffAccountRepository accounts, StaffTokens tokens, AdminProperties properties, TransactionTemplate transactions,
            Clock clock) {
        this.accounts = accounts;
        this.tokens = tokens;
        this.properties = properties;
        this.transactions = transactions;
        this.clock = clock;
        this.decoyHash = encoder.encode("decoy-" + System.nanoTime());
    }

    @Override
    public void afterSingletonsInstantiated() {
        transactions.executeWithoutResult(status -> seed());
    }

    void seed() {
        if (accounts.count() > 0) {
            return;
        }
        accounts.save(new StaffAccountEntity(SEED_EMAIL, SEED_NAME, StaffRole.owner, encoder.encode(properties.seedPassword()), clock.instant()));
        log.info("Seeded the staff owner account {} (password from aakar.admin.seed-password)", SEED_EMAIL);
    }

    @Transactional(readOnly = true)
    public StaffSession login(String email, String password) {
        Optional<StaffAccountEntity> account = accounts.findByEmailIgnoreCase(normalise(email));
        boolean matches = encoder.matches(password == null ? "" : password, account.map(StaffAccountEntity::passwordHash).orElse(decoyHash));
        if (account.isEmpty() || !matches) {
            log.info("Staff sign-in refused for {}", normalise(email));
            throw ApiProblemException.unauthenticated("That email and password don't match a staff account");
        }
        StaffPrincipal staff = account.get().principal();
        Instant now = clock.instant();
        Instant expiresAt = now.plus(properties.tokenTtl());
        log.info("Staff {} ({}) signed in", staff.email(), staff.role());
        return new StaffSession(tokens.issue(staff, now, expiresAt), StaffSession.BEARER, properties.tokenTtl().toSeconds(), staff.toDto());
    }

    /** The authentication for a bearer token, or empty when it is not a live staff token for an existing account. */
    @Transactional(readOnly = true)
    public Optional<StaffAuthentication> authenticate(String token) {
        Instant now = clock.instant();
        return tokens.parse(token)
                .filter(parsed -> parsed.expiresAt().isAfter(now))
                .flatMap(parsed -> accounts.findById(parsed.staffId()))
                .map(account -> new StaffAuthentication(account.principal()));
    }

    @Transactional(readOnly = true)
    public StaffDto me(StaffPrincipal staff) {
        return accounts.findById(staff.id()).map(StaffAccountEntity::toDto)
                .orElseThrow(() -> ApiProblemException.unauthenticated("This staff account no longer exists"));
    }

    @Override
    @Transactional
    public StaffDto create(String email, String name, StaffRole role, String password) {
        String normalised = normalise(email);
        if (accounts.findByEmailIgnoreCase(normalised).isPresent()) {
            throw ApiProblemException.conflict(STAFF_EXISTS, "Staff account exists", "A staff account for " + normalised + " already exists");
        }
        if (password == null || password.length() < 8) {
            throw ApiProblemException.validation("password must be at least 8 characters");
        }
        StaffAccountEntity saved = accounts.save(new StaffAccountEntity(normalised, name, role, encoder.encode(password), clock.instant()));
        log.info("Staff account {} ({}) created", normalised, role);
        return saved.toDto();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<StaffDto> byEmail(String email) {
        return accounts.findByEmailIgnoreCase(normalise(email)).map(StaffAccountEntity::toDto);
    }

    private static String normalise(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
