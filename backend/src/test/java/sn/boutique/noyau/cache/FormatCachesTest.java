package sn.boutique.noyau.cache;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import sn.boutique.noyau.config.CacheConfig;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.json.JsonMapper;

/**
 * Garde-fou de {@link CachesPublics#VERSION_FORMAT} : l'empreinte de la forme de tous les DTO mis en cache
 * (noms et types des champs, valeurs des enums, récursivement) est rattachée à la version du format.
 * <p>
 * Si ce test échoue, un DTO mis en cache a changé : incrémenter {@code VERSION_FORMAT} et ajouter la
 * nouvelle empreinte (affichée dans le message d'échec) à {@link #EMPREINTES}. Une ancienne et une nouvelle
 * version du backend pourront alors tourner ensemble pendant un déploiement sans lire les entrées de l'autre.
 */
class FormatCachesTest {

    /** Version du format → empreinte de la forme des DTO. Une ligne par version, jamais modifiée ensuite. */
    private static final Map<Integer, String> EMPREINTES = Map.of(
            1, "a4b151be420ae3da8ab09cdc",
            2, "ae2f573e434a4589ab239b60",
            3, "76add00af2bd0288e0c4f967");

    @Test
    void laVersionDuFormatSuitLaFormeDesDtoMisEnCache() {
        String empreinte = empreinte();
        assertThat(EMPREINTES.get(CachesPublics.VERSION_FORMAT))
                .as("La forme d'un DTO mis en cache a changé (empreinte %s) : incrémenter "
                        + "CachesPublics.VERSION_FORMAT et ajouter « %d, \"%s\" » à EMPREINTES.",
                        empreinte, CachesPublics.VERSION_FORMAT + 1, empreinte)
                .isEqualTo(empreinte);
    }

    @Test
    void laDescriptionCouvreLesDtoImbriquesEtLesEnums() {
        // Un champ ajouté à un DTO imbriqué (ex. la politique de retour dans les paramètres) change l'empreinte
        assertThat(description()).containsKeys(
                "sn.boutique.contenu.dto.ParametresPublicsReponse$PolitiqueRetour",
                "sn.boutique.catalogue.dto.CategorieNoeudReponse",
                "sn.boutique.catalogue.dto.ProduitResumeReponse",
                "cache sitemap");
        assertThat(description()).anySatisfy((cle, forme) -> assertThat(cle).endsWith("FraisRetour"));
    }

    @Test
    void lePrefixeDesClesContientLaVersion() {
        assertThat(CachesPublics.PREFIXE).isEqualTo("boutique:v" + CachesPublics.VERSION_FORMAT + ":");
    }

    static String empreinte() {
        String texte = description().entrySet().stream().map(e -> e.getKey() + " = " + e.getValue())
                .collect(Collectors.joining("\n"));
        try {
            byte[] hache = MessageDigest.getInstance("SHA-256").digest(texte.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hache, 0, 12);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** Type de chaque cache et forme de chaque classe du projet atteinte depuis ces types. */
    static Map<String, String> description() {
        Map<String, JavaType> types = CacheConfig.typesParCache(JsonMapper.builder().build().getTypeFactory());
        Map<String, String> description = new TreeMap<>();
        types.forEach((cache, type) -> description.put("cache " + cache, type.toCanonical()));
        for (JavaType type : types.values()) {
            decrire(type.getRawClass(), description);
            for (int i = 0; i < type.containedTypeCount(); i++) {
                decrire(type.containedType(i).getRawClass(), description);
            }
        }
        return description;
    }

    /** Ajoute la forme de la classe (et des classes du projet qu'elle référence) à la description. */
    private static void decrire(Class<?> classe, Map<String, String> description) {
        if (classe == null || classe.isPrimitive() || !classe.getName().startsWith("sn.boutique.")
                || description.containsKey(classe.getName())) {
            return;
        }
        if (classe.isEnum()) {
            description.put(classe.getName(), Arrays.toString(classe.getEnumConstants()));
            return;
        }
        List<String> champs = new ArrayList<>();
        List<Type> references = new ArrayList<>();
        if (classe.isRecord()) {
            for (RecordComponent composant : classe.getRecordComponents()) {
                champs.add(composant.getName() + ":" + composant.getGenericType().getTypeName());
                references.add(composant.getGenericType());
            }
        } else {
            for (Class<?> c = classe; c != null && c != Object.class; c = c.getSuperclass()) {
                for (Field champ : c.getDeclaredFields()) {
                    if (!Modifier.isStatic(champ.getModifiers()) && !Modifier.isTransient(champ.getModifiers())) {
                        champs.add(champ.getName() + ":" + champ.getGenericType().getTypeName());
                        references.add(champ.getGenericType());
                    }
                }
            }
            champs.sort(null);
        }
        description.put(classe.getName(), String.join(", ", champs));
        references.forEach(type -> parcourir(type, description));
    }

    private static void parcourir(Type type, Map<String, String> description) {
        switch (type) {
            case Class<?> c -> decrire(c.isArray() ? c.getComponentType() : c, description);
            case ParameterizedType p -> {
                parcourir(p.getRawType(), description);
                Arrays.stream(p.getActualTypeArguments()).forEach(a -> parcourir(a, description));
            }
            case GenericArrayType g -> parcourir(g.getGenericComponentType(), description);
            case WildcardType w -> Arrays.stream(w.getUpperBounds()).forEach(b -> parcourir(b, description));
            case TypeVariable<?> v -> { }
            default -> { }
        }
    }
}
