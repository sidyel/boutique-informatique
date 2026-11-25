package sn.boutique.media;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class LienYoutubeTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
            "https://youtube.com/watch?feature=share&v=dQw4w9WgXcQ&t=42",
            "http://m.youtube.com/watch?v=dQw4w9WgXcQ",
            "https://youtu.be/dQw4w9WgXcQ?si=abcdef",
            "https://www.youtube.com/shorts/dQw4w9WgXcQ",
            "https://www.youtube.com/embed/dQw4w9WgXcQ?rel=0",
            "https://www.youtube-nocookie.com/embed/dQw4w9WgXcQ",
            "  https://www.youtube.com/live/dQw4w9WgXcQ  "})
    void lesFormesCourantesDonnentLIdentifiant(String lien) {
        assertThat(LienYoutube.extraireIdentifiant(lien)).contains("dQw4w9WgXcQ");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "dQw4w9WgXcQ",
            "https://www.youtube.com/watch?v=trop-court",
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ<script>",
            "https://www.youtube.com.site-pirate.com/watch?v=dQw4w9WgXcQ",
            "https://site-pirate.com/youtu.be/dQw4w9WgXcQ",
            "https://utilisateur@www.youtube.com/watch?v=dQw4w9WgXcQ",
            "javascript://www.youtube.com/watch?v=dQw4w9WgXcQ",
            "ftp://youtu.be/dQw4w9WgXcQ",
            "https://vimeo.com/123456789"})
    void lesAutresLiensSontRefuses(String lien) {
        assertThat(LienYoutube.extraireIdentifiant(lien)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"dQw4w9WgXcQ"})
    void lUrlCanoniqueEstLaPageDeLaVideo(String identifiant) {
        assertThat(LienYoutube.urlCanonique(identifiant)).isEqualTo("https://www.youtube.com/watch?v=dQw4w9WgXcQ");
    }
}
