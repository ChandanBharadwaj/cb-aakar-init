package studio.aakar.api.media.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The guardrail's word rule: case, spaces and punctuation never count; a term of six letters or digits or fewer must stand as a
 * whole word of the text (so short terms never fire inside unrelated words or names), a longer one may sit anywhere.
 */
class TermMatcherTest {

    @Test
    void normaliseKeepsFoldedLowercaseLettersAndDigits() {
        assertThat(TermMatcher.normalise("Iron-Man_2.stl")).isEqualTo("ironman2stl");
        assertThat(TermMatcher.normalise("Spider-Man")).isEqualTo("spiderman");
        assertThat(TermMatcher.normalise("  S.P.I.D.E.R  man ")).isEqualTo("spiderman");
        assertThat(TermMatcher.normalise(null)).isEmpty();
        assertThat(TermMatcher.normalise("!! -- ??")).isEmpty();
        // accents, full-width letters and ligatures fold, so they can't hide a term
        assertThat(TermMatcher.normalise("Bätman")).isEqualTo("batman");
        assertThat(TermMatcher.normalise("ＢＡＴＭＡＮ")).isEqualTo("batman");
        assertThat(TermMatcher.normalise("ﬂash")).isEqualTo("flash");
        // Indic vowel signs are marks: dropped, the consonants stay
        assertThat(TermMatcher.normalise("बैटमैन")).isEqualTo("बटमन");
    }

    @Test
    void longTermsMatchAnywhereWhateverTheSpacingCaseAndPunctuation() {
        for (String text : List.of("iron man", "Iron-Man", "IRONMAN", "Iron_Man!", "Marvel_IronMan-poster.png", "my ironmanfan club", "I.R.O.N. M.A.N")) {
            assertThat(TermMatcher.text(text).mentions("ironman")).as(text).isTrue();
        }
        assertThat(TermMatcher.text("spidermanmask.stl").mentions("spiderman")).isTrue();
        assertThat(TermMatcher.text("the aquamanfan").mentions("aquaman")).as("seven letters: anywhere").isTrue();
        assertThat(TermMatcher.text("Iron Mandir").mentions("ironman")).as("the price of matching anywhere").isTrue();
        assertThat(TermMatcher.text("iron mask").mentions("ironman")).isFalse();
    }

    @Test
    void shortTermsMustStandAsWholeWords() {
        for (String text : List.of("DC", "dc", "D.C.", "d c", "I love DC", "dc-comics", "DCComics", "DC2024", "DC_logo.png", "Marvel vs DC")) {
            assertThat(TermMatcher.text(text).mentions("dc")).as(text).isTrue();
        }
        for (String text : List.of("Thor", "THOR", "Thor's Hammer", "ThorHammer.stl", "thor_v2", "mighty-thor")) {
            assertThat(TermMatcher.text(text).mentions("thor")).as(text).isTrue();
        }
        for (String text : List.of("Batman", "bAtMaN", "Bat Man", "Bat-Man", "B.A.T.M.A.N", "BatmanBeyond", "batman2")) {
            assertThat(TermMatcher.text(text).mentions("batman")).as(text).isTrue();
        }
        assertThat(TermMatcher.text("Marvel's hero").mentions("marvel")).isTrue();
        assertThat(TermMatcher.text("Nagraj").mentions("nagraj")).isTrue();
    }

    @Test
    void shortTermsNeverFireInsideOtherWordsOrNames() {
        for (String text : List.of("Adcock", "ABDC", "adc", "Hudco", "dcxyz")) {
            assertThat(TermMatcher.text(text).mentions("dc")).as(text).isFalse();
        }
        for (String text : List.of("Asha Thorat", "Author", "Arthor", "Thorne")) {
            assertThat(TermMatcher.text(text).mentions("thor")).as(text).isFalse();
        }
        for (String text : List.of("Marvellous Mum", "marvelous", "Marvelling")) {
            assertThat(TermMatcher.text(text).mentions("marvel")).as(text).isFalse();
        }
        for (String text : List.of("Nagrajan", "Nagraju", "Shri Nagrajappa")) {
            assertThat(TermMatcher.text(text).mentions("nagraj")).as(text).isFalse();
        }
        assertThat(TermMatcher.text("Hulking").mentions("hulk")).isFalse();
        assertThat(TermMatcher.text("Abatman").mentions("batman")).isFalse();
        assertThat(TermMatcher.text("Jokers").mentions("joker")).isFalse();
    }

    @Test
    void sixLettersOrFewerAreWholeWords() {
        assertThat(TermMatcher.WHOLE_WORD_MAX).isEqualTo(6);
        assertThat(TermMatcher.wholeWord("dc")).isTrue();
        assertThat(TermMatcher.wholeWord("marvel")).isTrue();
        assertThat(TermMatcher.wholeWord("ironman")).isFalse();
        assertThat(TermMatcher.wholeWord("बटमन")).as("counted in code points").isTrue();
        assertThat(TermMatcher.wholeWord(null)).isFalse();
    }

    @Test
    void indicTermsMatchAsWordsToo() {
        String batman = TermMatcher.normalise("बैटमैन");
        assertThat(TermMatcher.text("मेरा बैटमैन").mentions(batman)).isTrue();
        assertThat(TermMatcher.text("बैटमैन.png").mentions(batman)).isTrue();
        // the vowel signs never split a word, so a longer word that contains it is not a match
        assertThat(TermMatcher.text("बैटमैनजी").mentions(batman)).isFalse();
    }

    @Test
    void emptyTextsAndTermsMentionNothing() {
        assertThat(TermMatcher.text(null).isEmpty()).isTrue();
        assertThat(TermMatcher.text("").mentions("dc")).isFalse();
        assertThat(TermMatcher.text("DC").mentions("")).isFalse();
        assertThat(TermMatcher.text("DC").mentions(null)).isFalse();
    }
}
