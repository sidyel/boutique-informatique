package sn.boutique.commande.web;

import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import sn.boutique.commande.facture.DocumentsCommandeService.Document;

/**
 * Réponse de téléchargement d'une facture ou d'un avoir : PDF en pièce jointe, jamais mis en cache (document
 * personnel). Le nom de fichier est un numéro de pièce, sans caractère à échapper.
 */
final class ReponsePdf {

    private ReponsePdf() {
    }

    static ResponseEntity<byte[]> de(Document document) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.PRAGMA, "no-cache")
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(document.nomFichier()).build().toString())
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(document.octets().length)
                .body(document.octets());
    }
}
