package studio.aakar.api.media;

/**
 * What a content term protects: a publisher or brand ({@code trademark}: Marvel, DC), a character ({@code character}:
 * Spider-Man, Batman, Nagraj) or anything else the studio won't print ({@code other}).
 */
public enum ContentTermKind {
    trademark("trademark"),
    character("character"),
    other("term");

    private final String noun;

    ContentTermKind(String noun) {
        this.noun = noun;
    }

    /** The word a reviewer reads: "a protected trademark", "a protected character", "a protected term". */
    public String noun() {
        return noun;
    }
}
