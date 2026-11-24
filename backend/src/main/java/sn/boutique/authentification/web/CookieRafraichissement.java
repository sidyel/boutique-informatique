package sn.boutique.authentification.web;

import java.time.Duration;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import sn.boutique.authentification.config.ProprietesAuthentification;

/**
 * Cookie du jeton de rafraîchissement :
 * {@code HttpOnly} (illisible par JavaScript), {@code Secure}, {@code SameSite=Strict}
 * et {@code Path=/api/auth} (envoyé uniquement aux routes d'authentification, jamais au reste de l'API).
 */
@Component
@RequiredArgsConstructor
public class CookieRafraichissement {

    private final ProprietesAuthentification proprietes;

    public String nom() {
        return proprietes.cookie().nom();
    }

    public ResponseCookie creer(String jeton, Duration duree) {
        return base(jeton).maxAge(duree).build();
    }

    /** Efface le cookie dans le navigateur (mêmes attributs, Max-Age=0). */
    public ResponseCookie effacer() {
        return base("").maxAge(0).build();
    }

    private ResponseCookie.ResponseCookieBuilder base(String valeur) {
        ProprietesAuthentification.Cookie cookie = proprietes.cookie();
        return ResponseCookie.from(cookie.nom(), valeur)
                .httpOnly(true)
                .secure(cookie.secure())
                .sameSite("Strict")
                .path(cookie.chemin());
    }
}
