package studio.aakar.api.media.internal;

import java.util.List;
import studio.aakar.api.media.ContentTermDto;

/** The active content terms, most specific first (longest normalised form, then alphabetical): what the upload scanner reads. */
@FunctionalInterface
interface ActiveTerms {

    List<ContentTermDto> active();
}
