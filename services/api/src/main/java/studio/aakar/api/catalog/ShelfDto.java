package studio.aakar.api.catalog;

/** A Shop shelf (catalog category) as served by {@code GET /api/catalog/shelves}; {@code Shelf} in both contracts. */
public record ShelfDto(String id, String label, int sortOrder) {
}
