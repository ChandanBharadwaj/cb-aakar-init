package studio.aakar.api.identity.internal;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import studio.aakar.api.identity.AddressDto;
import studio.aakar.api.identity.AddressInput;
import studio.aakar.api.identity.Addresses;
import studio.aakar.api.shared.ApiProblemException;

/** A customer's addresses. The first one becomes the default; marking another default demotes the rest. */
@Service
class AddressService implements Addresses {

    private final AddressRepository addresses;
    private final Clock clock;

    AddressService(AddressRepository addresses, Clock clock) {
        this.addresses = addresses;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public List<AddressDto> list(UUID userId) {
        return addresses.findByUserIdOrderByIsDefaultDescCreatedAtAsc(userId).stream().map(AddressEntity::toDto).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AddressDto> find(UUID userId, UUID addressId) {
        return addresses.findByIdAndUserId(addressId, userId).map(AddressEntity::toDto);
    }

    @Transactional
    public AddressDto create(UUID userId, AddressInput input) {
        Instant now = clock.instant();
        List<AddressEntity> existing = addresses.findByUserIdOrderByIsDefaultDescCreatedAtAsc(userId);
        boolean makeDefault = existing.isEmpty() || input.wantsDefault();
        if (makeDefault) {
            existing.forEach(a -> a.setDefault(false, now));
        }
        return addresses.save(new AddressEntity(userId, input, makeDefault, now)).toDto();
    }

    @Transactional
    public AddressDto update(UUID userId, UUID addressId, AddressInput input) {
        Instant now = clock.instant();
        AddressEntity address = require(userId, addressId);
        boolean makeDefault = input.wantsDefault() || address.isDefault();
        if (input.wantsDefault()) {
            addresses.findByUserIdOrderByIsDefaultDescCreatedAtAsc(userId).stream()
                    .filter(a -> !a.id().equals(addressId))
                    .forEach(a -> a.setDefault(false, now));
        }
        address.apply(input, makeDefault, now);
        return address.toDto();
    }

    @Transactional
    public void delete(UUID userId, UUID addressId) {
        AddressEntity address = require(userId, addressId);
        boolean wasDefault = address.isDefault();
        addresses.delete(address);
        addresses.flush();
        if (wasDefault) {
            addresses.findByUserIdOrderByIsDefaultDescCreatedAtAsc(userId).stream().findFirst()
                    .ifPresent(next -> next.setDefault(true, clock.instant()));
        }
    }

    private AddressEntity require(UUID userId, UUID addressId) {
        return addresses.findByIdAndUserId(addressId, userId).orElseThrow(() -> ApiProblemException.notFound("Address", addressId));
    }
}
