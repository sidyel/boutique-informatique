package sn.boutique.authentification.dto;

/** Secret TOTP à enregistrer dans l'application d'authentification : QR code (image SVG en {@code data:}) ou saisie manuelle. */
public record EnrolementReponse(String secret, String uri, String codeQr) {
}
