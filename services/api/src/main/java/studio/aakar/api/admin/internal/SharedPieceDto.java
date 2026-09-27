package studio.aakar.api.admin.internal;

import java.time.LocalDate;
import java.util.UUID;

/** What the unboxing card's /k/{code} link resolves to: the piece and where to reprint or remix it. No customer data. */
public record SharedPieceDto(
        String code,
        String title,
        String specsLine,
        String materialId,
        String materialName,
        UUID designId,
        UUID versionId,
        String templateId,
        LocalDate printedAt,
        String studio,
        String thumbnailUrl,
        String reprintPath,
        String remixPath) {
}
