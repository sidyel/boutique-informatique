package sn.boutique.noyau.horloge;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import sn.boutique.noyau.config.HorlogeConfig;

/** Règle de CLAUDE.md : l'heure vient toujours du bean Clock, à la microseconde. */
class RegleHorlogeTest {

    /** {@code Instant.now()}, {@code LocalDate.now()}…, {@code System.currentTimeMillis()} hors commentaires. */
    private static final Pattern HEURE_SYSTEME = Pattern.compile(
            "\\b(Instant|LocalDate|LocalDateTime|LocalTime|OffsetDateTime|ZonedDateTime|Year|YearMonth)\\.now\\(\\s*\\)"
                    + "|System\\.currentTimeMillis\\(\\)|Clock\\.system(UTC|DefaultZone)\\(\\)");

    @Test
    void aucuneLectureDirecteDeLHeureSystemeDansLeCode() throws IOException {
        try (Stream<Path> fichiers = Files.walk(Path.of("src/main/java"))) {
            List<String> violations = fichiers
                    .filter(f -> f.toString().endsWith(".java"))
                    .filter(f -> !f.endsWith(Path.of("noyau/config/HorlogeConfig.java")))
                    .flatMap(RegleHorlogeTest::lignesFautives)
                    .toList();

            assertThat(violations).as("Utiliser le bean Clock « horloge » (voir CLAUDE.md)").isEmpty();
        }
    }

    @Test
    void lHorlogeEstEnUtcEtTronqueeALaMicroseconde() {
        Clock horloge = new HorlogeConfig().horloge();
        Instant instant = horloge.instant();

        assertThat(horloge.getZone().getId()).isEqualTo("Z");
        assertThat(instant).isEqualTo(instant.truncatedTo(ChronoUnit.MICROS));
    }

    private static Stream<String> lignesFautives(Path fichier) {
        try {
            List<String> lignes = Files.readAllLines(fichier);
            return java.util.stream.IntStream.range(0, lignes.size())
                    .filter(i -> {
                        String ligne = lignes.get(i).strip();
                        return !ligne.startsWith("*") && !ligne.startsWith("//") && !ligne.startsWith("/*")
                                && HEURE_SYSTEME.matcher(ligne).find();
                    })
                    .mapToObj(i -> fichier + ":" + (i + 1) + " " + lignes.get(i).strip());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
