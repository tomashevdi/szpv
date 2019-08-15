package ru.tdi.misintegration.szpv.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.context.SecurityContextPersistenceFilter;

@Configuration
@Order(1)
public class PARegisterSecurityConfigurationAdapter extends WebSecurityConfigurerAdapter {

    @Value("${paregister.token}")
    String authToken;

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http.sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS);
        http.antMatcher("/rfsz/webszp/reg/**").
                csrf().disable().
                addFilterBefore(new PARegisterFilter(authToken),SecurityContextPersistenceFilter.class);
    }
}
