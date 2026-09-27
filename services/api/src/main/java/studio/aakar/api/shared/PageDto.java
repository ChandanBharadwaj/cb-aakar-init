package studio.aakar.api.shared;

import java.util.List;

/** The page object every list endpoint of the management API answers with: {@code {items, page, size, total}}. */
public record PageDto<T>(List<T> items, int page, int size, long total) {

    public PageDto {
        items = items == null ? List.of() : List.copyOf(items);
    }

    public static <T> PageDto<T> of(org.springframework.data.domain.Page<T> page) {
        return new PageDto<>(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements());
    }
}
