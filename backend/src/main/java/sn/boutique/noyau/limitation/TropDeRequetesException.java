package sn.boutique.noyau.limitation;

import lombok.Getter;

/** Limite de débit atteinte : réponse 429 avec l'en-tête Retry-After. */
@Getter
public class TropDeRequetesException extends RuntimeException {

    /** Secondes avant que la fenêtre ne se rouvre. */
    private final long reessayerDans;

    public TropDeRequetesException(String regle, long reessayerDans) {
        super("Limite « " + regle + " » atteinte");
        this.reessayerDans = reessayerDans;
    }
}
