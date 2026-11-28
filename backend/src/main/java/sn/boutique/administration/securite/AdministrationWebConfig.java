package sn.boutique.administration.securite;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import sn.boutique.administration.journal.JournalInterceptor;

/** Intercepteurs de l'API d'administration : réauthentification (avant), journal générique (après). */
@Configuration
@RequiredArgsConstructor
public class AdministrationWebConfig implements WebMvcConfigurer {

    private final CompteActifInterceptor compteActif;
    private final ReauthentificationInterceptor reauthentification;
    private final JournalInterceptor journal;

    @Override
    public void addInterceptors(InterceptorRegistry registre) {
        registre.addInterceptor(compteActif).addPathPatterns("/api/admin/**");
        registre.addInterceptor(reauthentification).addPathPatterns("/api/admin/**");
        registre.addInterceptor(journal).addPathPatterns("/api/admin/**");
    }
}
