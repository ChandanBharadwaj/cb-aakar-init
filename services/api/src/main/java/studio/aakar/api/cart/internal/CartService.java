package studio.aakar.api.cart.internal;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import studio.aakar.api.cart.AddCartItemRequest;
import studio.aakar.api.cart.CartDto;
import studio.aakar.api.cart.CartItemDto;
import studio.aakar.api.cart.Carts;
import studio.aakar.api.cart.UpdateCartItemRequest;
import studio.aakar.api.catalog.Catalog;
import studio.aakar.api.catalog.MaterialDto;
import studio.aakar.api.design.DesignResponse;
import studio.aakar.api.design.DesignVersionResponse;
import studio.aakar.api.design.Designs;
import studio.aakar.api.design.VersionStatus;
import studio.aakar.api.pricing.PriceBreakdown;
import studio.aakar.api.pricing.PriceCalculator;
import studio.aakar.api.pricing.PricingPolicy;
import studio.aakar.api.pricing.PricingPolicyStore;
import studio.aakar.api.shared.ApiProblemException;
import studio.aakar.api.shared.Identity;
import studio.aakar.api.shared.ProblemCodes;

@Service
class CartService implements Carts {

    private static final Logger log = LoggerFactory.getLogger(CartService.class);
    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() { };

    private final CartRepository carts;
    private final CartItemRepository items;
    private final Designs designs;
    private final Catalog catalog;
    private final PriceCalculator calculator;
    private final PricingPolicyStore policies;
    private final ObjectMapper json;
    private final Clock clock;

    CartService(CartRepository carts, CartItemRepository items, Designs designs, Catalog catalog, PriceCalculator calculator,
            PricingPolicyStore policies, ObjectMapper json, Clock clock) {
        this.carts = carts;
        this.items = items;
        this.designs = designs;
        this.catalog = catalog;
        this.calculator = calculator;
        this.policies = policies;
        this.json = json;
        this.clock = clock;
    }

    @Override
    @Transactional
    public CartDto cart(Identity identity) {
        return render(cartFor(identity));
    }

    @Override
    @Transactional
    public CartDto add(Identity identity, AddCartItemRequest request) {
        CartEntity cart = cartFor(identity);
        DesignVersionResponse version = requirePurchasable(request.versionId());
        MaterialDto material = requireMaterial(request.material());
        PricingPolicy policy = policies.active();
        PriceBreakdown price = price(version, material, policy);
        Instant now = clock.instant();

        Optional<CartItemEntity> existing = items.findByCartIdAndVersionIdAndMaterialId(cart.id(), version.id(), material.id());
        if (existing.isPresent()) {
            CartItemEntity item = existing.get();
            item.setQty(CartMerge.combinedQty(item.qty(), request.qtyOrOne()));
            item.reprice(material.id(), toMap(price), policy.version());
        } else {
            items.save(new CartItemEntity(cart.id(), version.id(), material.id(), request.qtyOrOne(), toMap(price), policy.version(), now));
        }
        cart.touch(now);
        log.info("Cart {} ({}): added version {} in {} × {}", cart.id(), identity, version.id(), material.id(), request.qtyOrOne());
        return render(cart);
    }

    @Override
    @Transactional
    public CartDto update(Identity identity, UUID itemId, UpdateCartItemRequest request) {
        CartEntity cart = cartFor(identity);
        CartItemEntity item = items.findByIdAndCartId(itemId, cart.id()).orElseThrow(() -> ApiProblemException.notFound("Cart item", itemId));
        if (request.changesMaterial() && !request.material().equals(item.materialId())) {
            MaterialDto material = requireMaterial(request.material());
            DesignVersionResponse version = requirePurchasable(item.versionId());
            PricingPolicy policy = policies.active();
            Optional<CartItemEntity> clash = items.findByCartIdAndVersionIdAndMaterialId(cart.id(), item.versionId(), material.id());
            if (clash.isPresent()) {
                // Switching onto a finish already in the cart: fold this line into that one.
                CartItemEntity target = clash.get();
                target.setQty(CartMerge.combinedQty(target.qty(), request.qty() == null ? item.qty() : request.qty()));
                target.reprice(material.id(), toMap(price(version, material, policy)), policy.version());
                items.delete(item);
                cart.touch(clock.instant());
                return render(cart);
            }
            item.reprice(material.id(), toMap(price(version, material, policy)), policy.version());
        }
        if (request.qty() != null) {
            item.setQty(request.qty());
        }
        cart.touch(clock.instant());
        return render(cart);
    }

    @Override
    @Transactional
    public CartDto remove(Identity identity, UUID itemId) {
        CartEntity cart = cartFor(identity);
        CartItemEntity item = items.findByIdAndCartId(itemId, cart.id()).orElseThrow(() -> ApiProblemException.notFound("Cart item", itemId));
        items.delete(item);
        cart.touch(clock.instant());
        return render(cart);
    }

    @Override
    @Transactional
    public CartDto clear(Identity identity) {
        CartEntity cart = cartFor(identity);
        items.deleteByCartId(cart.id());
        cart.touch(clock.instant());
        return render(cart);
    }

    @Override
    @Transactional
    public int mergeGuestCart(UUID guestId, UUID userId) {
        Optional<CartEntity> guestCart = carts.findByGuestId(guestId);
        if (guestCart.isEmpty()) {
            return 0;
        }
        List<CartItemEntity> guestItems = items.findByCartIdOrderByAddedAtAsc(guestCart.get().id());
        if (guestItems.isEmpty()) {
            carts.delete(guestCart.get());
            return 0;
        }
        Instant now = clock.instant();
        CartEntity userCart = carts.findByUserId(userId).orElseGet(() -> carts.save(new CartEntity(Identity.user(userId), now)));
        CartMerge.Plan<CartItemEntity> plan = CartMerge.plan(items.findByCartIdOrderByAddedAtAsc(userCart.id()), guestItems);
        plan.moved().forEach(item -> item.moveTo(userCart.id()));
        plan.combined().forEach(c -> {
            c.target().setQty(c.qty());
            items.delete(c.source());
        });
        items.flush();
        carts.delete(guestCart.get());
        userCart.touch(now);
        return plan.guestLines();
    }

    /** After a successful payment the user's cart is emptied (event-driven from the payment module). */
    @Transactional
    public void clearForUser(UUID userId) {
        carts.findByUserId(userId).ifPresent(cart -> {
            items.deleteByCartId(cart.id());
            cart.touch(clock.instant());
        });
    }

    private CartEntity cartFor(Identity identity) {
        UUID id = identity.requireKnown();
        Optional<CartEntity> existing = identity.isUser() ? carts.findByUserId(id) : carts.findByGuestId(id);
        return existing.orElseGet(() -> carts.save(new CartEntity(identity, clock.instant())));
    }

    /** Re-prices every line against the active policy and renders the cart. */
    private CartDto render(CartEntity cart) {
        PricingPolicy policy = policies.active();
        List<CartItemDto> lines = new ArrayList<>();
        long subtotal = 0;
        for (CartItemEntity item : items.findByCartIdOrderByAddedAtAsc(cart.id())) {
            Optional<DesignVersionResponse> version = designs.findVersion(item.versionId());
            Optional<MaterialDto> material = catalog.material(item.materialId());
            boolean purchasable = material.isPresent() && CartPricing.purchasable(version);
            boolean repriced = false;
            if (purchasable && CartPricing.needsReprice(item.policyVersion(), policy.version())) {
                item.reprice(material.get().id(), toMap(price(version.get(), material.get(), policy)), policy.version());
                repriced = true;
            }
            PriceBreakdown unitPrice = json.convertValue(item.unitPrice(), PriceBreakdown.class);
            long lineTotal = CartPricing.lineTotal(unitPrice, item.qty());
            subtotal += lineTotal;
            DesignVersionResponse v = version.orElse(null);
            String title = version.flatMap(ver -> designs.find(ver.designId())).map(DesignResponse::title).orElse("Design");
            lines.add(new CartItemDto(item.id(), v == null ? null : v.designId(), item.versionId(), v == null ? null : v.versionNo(), title,
                    CartPricing.specsLine(material.map(MaterialDto::name).orElse(item.materialId()), v, unitPrice), item.materialId(),
                    material.map(MaterialDto::name).orElse(null), item.qty(), unitPrice, lineTotal, CartPricing.thumbnailUrl(v),
                    purchasable, repriced));
        }
        PriceCalculator.Shipping shipping = lines.isEmpty() ? new PriceCalculator.Shipping(0, policy.shippingLabel())
                : PriceCalculator.shipping(subtotal, policy);
        return new CartDto(cart.id(), cart.owner(), List.copyOf(lines), subtotal, shipping.paise(), shipping.label(),
                subtotal + shipping.paise(), policy.version(), cart.updatedAt());
    }

    private DesignVersionResponse requirePurchasable(UUID versionId) {
        DesignVersionResponse version = designs.findVersion(versionId).orElseThrow(() -> ApiProblemException.notFound("Version", versionId));
        if (version.status() != VersionStatus.ready) {
            throw ApiProblemException.conflict(ProblemCodes.VERSION_NOT_READY, "Version not ready",
                    "Version " + versionId + " is " + version.status() + "; only a ready version can be added to the cart");
        }
        if (version.printability() == null || !Boolean.TRUE.equals(version.printability().get("passed"))) {
            throw ApiProblemException.conflict(ProblemCodes.NOT_PRINTABLE, "Not printable",
                    "Version " + versionId + " did not pass the printability check; adjust the design before ordering");
        }
        if (CartPricing.estimate(version).isEmpty()) {
            throw ApiProblemException.conflict(ProblemCodes.VERSION_NOT_READY, "Version not ready",
                    "Version " + versionId + " has no print estimate to price from");
        }
        return version;
    }

    private MaterialDto requireMaterial(String materialId) {
        return catalog.material(materialId).orElseThrow(() -> new ApiProblemException(HttpStatus.UNPROCESSABLE_ENTITY,
                ProblemCodes.UNKNOWN_MATERIAL, "Unknown material", "Material '" + materialId + "' is not offered"));
    }

    private PriceBreakdown price(DesignVersionResponse version, MaterialDto material, PricingPolicy policy) {
        return calculator.price(CartPricing.estimate(version).orElseThrow(), CartPricing.inputs(material), policy);
    }

    private Map<String, Object> toMap(PriceBreakdown price) {
        return json.convertValue(price, MAP);
    }
}
