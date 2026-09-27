package studio.aakar.api.admin.internal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import studio.aakar.api.order.OrderStatus;
import studio.aakar.api.shared.PageDto;

@RestController
@RequestMapping("/admin/api/orders")
@Tag(name = "admin · orders")
@SecurityRequirement(name = "staffBearer")
class AdminOrderController {

    static final MediaType APPLICATION_ZIP = MediaType.parseMediaType("application/zip");

    private final AdminOrderService orders;

    AdminOrderController(AdminOrderService orders) {
        this.orders = orders;
    }

    @GetMapping
    @Operation(summary = "Orders queue", description = "Newest first. `status` is a comma-separated list of OrderStatus values; `q` matches an "
            + "order-number prefix (`AK-0001`, `000012`) or digits of the customer's phone.")
    PageDto<Map<String, Object>> list(@RequestParam(required = false) String status, @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "25") int size, StaffPrincipal staff) {
        return orders.search(status, q, page, size);
    }

    @GetMapping("/{orderId}")
    @Operation(summary = "An order with its customer, next actions, QC photos and messages")
    Map<String, Object> order(@PathVariable UUID orderId, StaffPrincipal staff) {
        return orders.get(orderId);
    }

    @PostMapping("/{orderId}/advance")
    @Operation(summary = "Move the order to its next status", description = "Checks the transition table (409 `invalid_transition`); `message` "
            + "defaults per status (\"Slicing your piece\", \"Printing\", \"Hand sanding & sealing\", \"Quality check\", \"Packed\", \"Shipped\", "
            + "\"Delivered\"); `detail` is stored on the event. Packed books the shipment; printing, shipped and delivered record the customer "
            + "message. Audited as `order.advance`.")
    Map<String, Object> advance(@PathVariable UUID orderId, @Valid @RequestBody AdvanceRequest request, StaffPrincipal staff) {
        return orders.advance(orderId, request.status(), request.message(), request.detail(), staff);
    }

    @GetMapping("/{orderId}/print-pack")
    @Operation(summary = "Everything the outsourced printer needs, as one zip", description = "Per item `item-<n>/print-sheet.txt`, `model.3mf` "
            + "and `model.stl` (downloaded from the version's assets; a file that cannot be fetched is noted on the sheet).")
    ResponseEntity<byte[]> printPack(@PathVariable UUID orderId, StaffPrincipal staff) {
        Map<String, Object> order = orders.get(orderId);
        byte[] zip = orders.printPack(orderId);
        return ResponseEntity.ok()
                .contentType(APPLICATION_ZIP)
                .header(HttpHeaders.CONTENT_DISPOSITION, attachment(PrintPack.filename(String.valueOf(order.get("number")))))
                .body(zip);
    }

    @PostMapping(path = "/{orderId}/qc-photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Attach a QC photo", description = "Multipart `file` (at most 10 MB; 413 `payload_too_large`) and optional `note`. "
            + "Stored in the media store and served at its `url`. Audited as `order.qc_photo`.")
    ResponseEntity<MediaAssetDto> qcPhoto(@PathVariable UUID orderId, @RequestPart("file") MultipartFile file,
            @RequestParam(required = false) @Size(max = 200) String note, StaffPrincipal staff) {
        return ResponseEntity.status(HttpStatus.CREATED).body(orders.addQcPhoto(orderId, file, note, staff));
    }

    @GetMapping("/{orderId}/packaging-card.pdf")
    @Operation(summary = "The card that goes in the box", description = "One-page PDF: \"Designed by You. Crafted by Aakar.\", the piece, "
            + "its finish, where and when it was printed, the order number and the /k/{code} reprint link as text and QR code. "
            + "409 `order_not_packed` before the order is packed.")
    ResponseEntity<byte[]> packagingCard(@PathVariable UUID orderId, StaffPrincipal staff) {
        Map<String, Object> order = orders.get(orderId);
        byte[] pdf = orders.packagingCard(orderId);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, attachment(order.get("number") + "-packaging-card.pdf"))
                .body(pdf);
    }

    private static String attachment(String filename) {
        return ContentDisposition.attachment().filename(filename).build().toString();
    }

    record AdvanceRequest(
            @NotNull(message = "status is required") OrderStatus status,
            @Size(max = 200, message = "message must be at most 200 characters") String message,
            Map<String, Object> detail) {
    }
}
