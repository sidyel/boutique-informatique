package sn.boutique.support;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.junit.jupiter.api.ClassOrderer;
import org.junit.jupiter.api.ClassDescriptor;
import org.junit.jupiter.api.ClassOrdererContext;
import org.junit.jupiter.api.MethodDescriptor;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.MethodOrdererContext;

/**
 * Ordre aléatoire des classes et des méthodes de test, <strong>rejouable</strong> : la graine est affichée au démarrage
 * (« GRAINE_ORDRE_TESTS=… ») et se fixe avec {@code -Djunit.jupiter.execution.order.random.seed=N}. Le but est de
 * débusquer les tests qui dépendent de l'ordre d'exécution (CLAUDE.md : aucun test ne dépend de l'ordre).
 * <p>
 * Les éléments sont d'abord triés par nom puis mélangés avec la graine : l'ordre ne dépend ni du système de fichiers ni
 * de la réflexion, seulement de la graine. (L'ordre des classes est aussi réglé par {@code surefire.runOrder.random.seed}
 * quand Surefire lance les classes une à une ; en CI les deux reçoivent la même valeur.)
 */
public class OrdreAleatoire implements MethodOrderer, ClassOrderer {

    static final String PROPRIETE = "junit.jupiter.execution.order.random.seed";
    static final long GRAINE = graine();

    private static long graine() {
        long valeur = Long.getLong(PROPRIETE, System.nanoTime());
        System.out.println("GRAINE_ORDRE_TESTS=" + valeur + "  (rejouer : -D" + PROPRIETE + "=" + valeur
                + " -Dsurefire.runOrder.random.seed=" + valeur + ")");
        return valeur;
    }

    @Override
    public void orderMethods(MethodOrdererContext contexte) {
        List<MethodDescriptor> methodes = new ArrayList<>(contexte.getMethodDescriptors());
        methodes.sort(Comparator.comparing(MethodDescriptor::getDisplayName));
        Collections.shuffle(methodes, new java.util.Random(GRAINE ^ contexte.getTestClass().getName().hashCode()));
        contexte.getMethodDescriptors().clear();
        contexte.getMethodDescriptors().addAll(methodes);
    }

    @Override
    public void orderClasses(ClassOrdererContext contexte) {
        List<ClassDescriptor> classes = new ArrayList<>(contexte.getClassDescriptors());
        classes.sort(Comparator.comparing(c -> c.getTestClass().getName()));
        Collections.shuffle(classes, new java.util.Random(GRAINE));
        contexte.getClassDescriptors().clear();
        contexte.getClassDescriptors().addAll(classes);
    }
}
