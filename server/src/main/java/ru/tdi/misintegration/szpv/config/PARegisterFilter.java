package ru.tdi.misintegration.szpv.config;

import org.springframework.web.filter.GenericFilterBean;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class PARegisterFilter extends GenericFilterBean {

    private String authToken;

    public PARegisterFilter(String authToken) {
        this.authToken = authToken;
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {

        String token = servletRequest.getParameter("token");
        if (token==null || !authToken.equals(token)) {
            HttpServletResponse httpResponse = (HttpServletResponse) servletResponse;
            httpResponse.sendError(HttpServletResponse.SC_FORBIDDEN, "Unauthorized");
        }

        filterChain.doFilter(servletRequest,servletResponse);
    }
}
