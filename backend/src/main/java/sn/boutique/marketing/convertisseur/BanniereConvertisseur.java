package sn.boutique.marketing.convertisseur;

import org.mapstruct.Mapper;
import sn.boutique.marketing.dto.BanniereReponse;
import sn.boutique.marketing.modele.Banniere;

@Mapper
public interface BanniereConvertisseur {

    BanniereReponse versReponse(Banniere banniere);
}
