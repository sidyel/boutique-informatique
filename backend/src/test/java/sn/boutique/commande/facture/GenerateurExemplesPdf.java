package sn.boutique.commande.facture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import sn.boutique.contenu.modele.RegimeTva;

/**
 * Génère des exemplaires de facture et d'avoir à montrer au comptable, avec des données fictives clairement marquées
 * « EXEMPLE ». Ne tourne que sur demande :
 * {@code ./mvnw test -Dtest=GenerateurExemplesPdf -Dexemples.dossier=../docs/exemples}.
 * <p>
 * Les montants passent par {@link CalculFacture} et le même rendu que la production : ce sont de vraies pièces, pas
 * des maquettes.
 */
@EnabledIfSystemProperty(named = "exemples.dossier", matches = ".+")
class GenerateurExemplesPdf {

    private static final Instant EMISSION = Instant.parse("2026-11-27T10:30:00Z");

    private static RenduPdfService service() {
        var resolveur = new org.thymeleaf.templateresolver.ClassLoaderTemplateResolver();
        resolveur.setPrefix("templates/");
        resolveur.setSuffix(".html");
        resolveur.setTemplateMode("HTML");
        resolveur.setCharacterEncoding("UTF-8");
        var moteur = new org.thymeleaf.spring6.SpringTemplateEngine();
        moteur.setTemplateResolver(resolveur);
        return new RenduPdfService(moteur);
    }

    private static PieceImprimable.Vendeur vendeur() {
        return new PieceImprimable.Vendeur("EXEMPLE – Boutique Informatique SARL (société fictive)",
                "NINEA EXEMPLE 0000000 0X0", "RCCM EXEMPLE SN-DKR-0000-B-0000",
                "EXEMPLE – 1 avenue de l'Exemple, Dakar (adresse fictive)");
    }

    private static PieceImprimable.Client client() {
        return new PieceImprimable.Client("EXEMPLE – Aïssatou Ndiaye-Sène", "+221 77 000 00 00",
                "exemple@exemple.sn", "EXEMPLE – Rue 10 x 11, Médina, Dakar");
    }

    /** Lignes à partir de prix TTC, avec la TVA répartie comme à l'émission. */
    private static List<PieceImprimable.Ligne> lignes(int tauxBp, Object[][] articles) {
        List<Long> ttc = new ArrayList<>();
        for (Object[] a : articles) {
            ttc.add((long) (int) a[2] * (int) a[3] - (int) a[4]);
        }
        List<CalculFacture.Part> parts = CalculFacture.extraireTva(ttc, tauxBp);
        List<PieceImprimable.Ligne> lignes = new ArrayList<>();
        for (int i = 0; i < articles.length; i++) {
            Object[] a = articles[i];
            lignes.add(new PieceImprimable.Ligne((String) a[0], (String) a[1], (int) a[3], (int) a[2], (int) a[4],
                    ttc.get(i), parts.get(i).ht(), parts.get(i).tva()));
        }
        return lignes;
    }

    private static PieceImprimable piece(String titre, String numero, String factureOrigine, String motif,
                                         RegimeTva regime, String mention, List<PieceImprimable.Ligne> lignes) {
        long ttc = lignes.stream().mapToLong(PieceImprimable.Ligne::totalTtc).sum();
        long ht = lignes.stream().mapToLong(PieceImprimable.Ligne::totalHt).sum();
        long tva = lignes.stream().mapToLong(PieceImprimable.Ligne::montantTva).sum();
        return new PieceImprimable(titre, numero, EMISSION, "CMD-2026-000042", factureOrigine, motif, vendeur(),
                client(), "Paiement en ligne (mobile money)", regime, 1800, mention, lignes, ttc, ht, tva);
    }

    @Test
    void genererLesExemplaires() throws Exception {
        Path dossier = Path.of(System.getProperty("exemples.dossier"));
        Files.createDirectories(dossier);
        Object[][] commande = {
                {"EXEMPLE-PC-001", "PC portable 15,6\" Ryzen 5 – 16 Go / 512 Go SSD", 389_000, 1, 0},
                {"EXEMPLE-SOU-002", "Souris sans fil « silencieuse »", 8_500, 2, 0},
                {"EXEMPLE-SAC-003", "Sac à dos pour portable 15,6\" – remise code EXEMPLE", 15_000, 1, 5_000},
                {"LIVRAISON", "Livraison – Dakar (24 h)", 2_000, 1, 0}};

        Files.write(dossier.resolve("facture-exemple-tva-18.pdf"), service().rendre(piece("Facture",
                "FAC-2026-000001", null, null, RegimeTva.ASSUJETTI, "TVA 18 % incluse dans les prix TTC",
                lignes(1800, commande))));
        Files.write(dossier.resolve("facture-exemple-tva-non-applicable.pdf"), service().rendre(piece("Facture",
                "FAC-2026-000002", null, null, RegimeTva.NON_APPLICABLE, "TVA non applicable",
                lignes(0, commande))));
        Object[][] credit = {
                {"EXEMPLE-SOU-002", "Souris sans fil « silencieuse »", 8_500, 1, 0}};
        Files.write(dossier.resolve("avoir-exemple-partiel.pdf"), service().rendre(piece("Avoir", "AV-2026-000001",
                "FAC-2026-000001", "Une souris défectueuse retournée par le client", RegimeTva.ASSUJETTI,
                "TVA 18 % incluse dans les prix TTC", lignes(1800, credit))));

        try (var fichiers = Files.list(dossier)) {
            assertThat(fichiers.filter(f -> f.toString().endsWith(".pdf")).count()).isGreaterThanOrEqualTo(3);
        }
    }
}
