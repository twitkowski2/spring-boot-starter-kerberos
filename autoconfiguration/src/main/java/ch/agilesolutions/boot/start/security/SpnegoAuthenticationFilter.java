package ch.agilesolutions.boot.start.security;



import java.io.IOException;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationDetailsSource;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.codec.Base64;
import org.springframework.security.kerberos.authentication.KerberosServiceAuthenticationProvider;
import org.springframework.security.kerberos.authentication.KerberosServiceRequestToken;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.security.web.authentication.session.NullAuthenticatedSessionStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.util.Assert;
import org.springframework.web.filter.GenericFilterBean;

import ch.agilesolutions.boot.start.service.AuthenticationService;

/**
 * Parses the SPNEGO authentication Header, which was generated by the React JS client
 * and creates a {@link KerberosServiceRequestToken} out if it. It will then
 * call the {@link AuthenticationManager}.
 *
 * <p>A typical Spring Security configuration might look like this:</p>
 *
 * <pre>
 * &lt;beans xmlns=&quot;http://www.springframework.org/schema/beans&quot;
 * xmlns:xsi=&quot;http://www.w3.org/2001/XMLSchema-instance&quot; xmlns:sec=&quot;http://www.springframework.org/schema/security&quot;
 * xsi:schemaLocation=&quot;http://www.springframework.org/schema/beans http://www.springframework.org/schema/beans/spring-beans-2.0.xsd
 * 	http://www.springframework.org/schema/security http://www.springframework.org/schema/security/spring-security-3.0.xsd&quot;&gt;
 *
 * &lt;sec:http entry-point-ref=&quot;spnegoEntryPoint&quot;&gt;
 * 	&lt;sec:intercept-url pattern=&quot;/secure/**&quot; access=&quot;IS_AUTHENTICATED_FULLY&quot; /&gt;
 * 	&lt;sec:custom-filter ref=&quot;spnegoAuthenticationProcessingFilter&quot; position=&quot;BASIC_AUTH_FILTER&quot; /&gt;
 * &lt;/sec:http&gt;
 *
 * &lt;bean id=&quot;spnegoEntryPoint&quot; class=&quot;org.springframework.security.kerberos.web.authentication.SpnegoEntryPoint&quot; /&gt;
 *
 * &lt;bean id=&quot;spnegoAuthenticationProcessingFilter&quot;
 * 	class=&quot;org.springframework.security.kerberos.web.authentication.SpnegoAuthenticationProcessingFilter&quot;&gt;
 * 	&lt;property name=&quot;authenticationManager&quot; ref=&quot;authenticationManager&quot; /&gt;
 * &lt;/bean&gt;
 *
 * &lt;sec:authentication-manager alias=&quot;authenticationManager&quot;&gt;
 * 	&lt;sec:authentication-provider ref=&quot;kerberosServiceAuthenticationProvider&quot; /&gt;
 * &lt;/sec:authentication-manager&gt;
 *
 * &lt;bean id=&quot;kerberosServiceAuthenticationProvider&quot;
 * 	class=&quot;org.springframework.security.kerberos.authenitcation.KerberosServiceAuthenticationProvider&quot;&gt;
 * 	&lt;property name=&quot;ticketValidator&quot;&gt;
 * 		&lt;bean class=&quot;org.springframework.security.kerberos.authentication.sun.SunJaasKerberosTicketValidator&quot;&gt;
 * 			&lt;property name=&quot;servicePrincipal&quot; value=&quot;HTTP/web.springsource.com&quot; /&gt;
 * 			&lt;property name=&quot;keyTabLocation&quot; value=&quot;classpath:http-java.keytab&quot; /&gt;
 * 		&lt;/bean&gt;
 * 	&lt;/property&gt;
 * 	&lt;property name=&quot;userDetailsService&quot; ref=&quot;inMemoryUserDetailsService&quot; /&gt;
 * &lt;/bean&gt;
 *
 * &lt;bean id=&quot;inMemoryUserDetailsService&quot;
 * 	class=&quot;org.springframework.security.core.userdetails.memory.InMemoryDaoImpl&quot;&gt;
 * 	&lt;property name=&quot;userProperties&quot;&gt;
 * 		&lt;value&gt;
 * 			mike@SECPOD.DE=notUsed,ROLE_ADMIN
 * 		&lt;/value&gt;
 * 	&lt;/property&gt;
 * &lt;/bean&gt;
 * &lt;/beans&gt;
 * </pre>
 *
 * <p>If you get a "GSSException: Channel binding mismatch (Mechanism
 * level:ChannelBinding not provided!) have a look at this <a
 * href="http://bugs.sun.com/view_bug.do?bug_id=6851973">bug</a>.</p>
 * <p>A workaround unti this is fixed in the JVM is to change</p>
 * HKEY_LOCAL_MACHINE\System
 * \CurrentControlSet\Control\LSA\SuppressExtendedProtection to 0x02
 *
 *
 *The Web Server responds with
 * HTTP/1.1 401 Unauthorized
 * WWW-Authenticate: Negotiate
 * the client will need to send a header like
 * Authorization: Negotiate YY.....
 *
 * @author Mike Wiesner
 * @author Jeremy Stone
 * @since 1.0
 * @see KerberosServiceAuthenticationProvider
 * @see SpnegoEntryPoint
 */
public class SpnegoAuthenticationFilter extends GenericFilterBean {

	private AuthenticationDetailsSource<HttpServletRequest,?> authenticationDetailsSource = new WebAuthenticationDetailsSource();
    private AuthenticationManager authenticationManager;
    private AuthenticationSuccessHandler successHandler;
    private AuthenticationFailureHandler failureHandler;
    private SessionAuthenticationStrategy sessionStrategy = new NullAuthenticatedSessionStrategy();
    private boolean skipIfAlreadyAuthenticated = false;
    
    @Autowired
    AuthenticationService authenticationService;
    
    

    public SpnegoAuthenticationFilter(AuthenticationService authenticationService) {
		super();
		this.authenticationService = authenticationService;
	}

	@Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) res;

        if (skipIfAlreadyAuthenticated) {
            Authentication existingAuth = SecurityContextHolder.getContext().getAuthentication();

            if (existingAuth != null && existingAuth.isAuthenticated()
                    && (existingAuth instanceof AnonymousAuthenticationToken) == false) {
                chain.doFilter(request, response);
                return;
            }
        }

        String header = request.getHeader("Authorization");

        if (header != null && (header.startsWith("Negotiate ") || header.startsWith("Kerberos "))) {
            if (logger.isDebugEnabled()) {
                logger.debug("Received Negotiate Header for request " + request.getRequestURL() + ": " + header);
            }
            byte[] base64Token = header.substring(header.indexOf(" ") + 1).getBytes("UTF-8");
            byte[] kerberosTicket = Base64.decode(base64Token);
            KerberosServiceRequestToken authenticationRequest = new KerberosServiceRequestToken(kerberosTicket);
            authenticationRequest.setDetails(authenticationDetailsSource.buildDetails(request));
            Authentication authentication;
            try {
                authentication = authenticationManager.authenticate(authenticationRequest);
            } catch (AuthenticationException e) {
                // That shouldn't happen, as it is most likely a wrong
                // configuration on the server side
                logger.warn("Negotiate Header was invalid: " + header, e);
                SecurityContextHolder.clearContext();
                if (failureHandler != null) {
                    failureHandler.onAuthenticationFailure(request, response, e);
                } else {
                    response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                    response.flushBuffer();
                }
                return;
            }
            sessionStrategy.onAuthentication(authentication, request, response);
            SecurityContextHolder.getContext().setAuthentication(authentication);
            authenticationService.addToken(response, authenticationRequest);
            if (successHandler != null) {
                successHandler.onAuthenticationSuccess(request, response, authentication);
            }
            chain.doFilter(request, response);

        } else {
        	//resp.setStatus(401);//Unauthorized
        	response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setHeader("WWW-Authenticate", "Negotiate");

//            response.sendError(401, message);
            response.flushBuffer();
        	
        }


    }

    @Override
    public void afterPropertiesSet() throws ServletException {
        super.afterPropertiesSet();
        Assert.notNull(this.authenticationManager, "authenticationManager must be specified");
    }

    /**
     * The authentication manager for validating the ticket.
     *
     * @param authenticationManager the authentication manager
     */
    public void setAuthenticationManager(AuthenticationManager authenticationManager) {
        this.authenticationManager = authenticationManager;
    }

    /**
     * <p>This handler is called after a successful authentication. One can add
     * additional authentication behavior by setting this.</p>
     * <p>Default is null, which means nothing additional happens</p>
     *
     * @param successHandler the authentication success handler
     */
    public void setSuccessHandler(AuthenticationSuccessHandler successHandler) {
        this.successHandler = successHandler;
    }

    /**
     * <p>This handler is called after a failure authentication. In most cases you
     * only get Kerberos/SPNEGO failures with a wrong server or network
     * configurations and not during runtime. If the client encounters an error,
     * he will just stop the communication with server and therefore this
     * handler will not be called in this case.</p>
     * <p>Default is null, which means that the Filter returns the HTTP 500 code</p>
     *
     * @param failureHandler the authentication failure handler
     */
    public void setFailureHandler(AuthenticationFailureHandler failureHandler) {
        this.failureHandler = failureHandler;
    }


    /**
     * Should Kerberos authentication be skipped if a user is already authenticated
     * for this request (e.g. in the HTTP session).
     *
     * @param skipIfAlreadyAuthenticated default is true
     */
    public void setSkipIfAlreadyAuthenticated(boolean skipIfAlreadyAuthenticated) {
        this.skipIfAlreadyAuthenticated = skipIfAlreadyAuthenticated;
    }

    /**
	 * The session handling strategy which will be invoked immediately after an
	 * authentication request is successfully processed by the
	 * <tt>AuthenticationManager</tt>. Used, for example, to handle changing of
	 * the session identifier to prevent session fixation attacks.
	 *
	 * @param sessionStrategy the implementation to use. If not set a null
	 *                        implementation is used.
	 */
    public void setSessionAuthenticationStrategy(SessionAuthenticationStrategy sessionStrategy) {
        this.sessionStrategy = sessionStrategy;
    }


    /**
     * Sets the authentication details source.
     *
     * @param authenticationDetailsSource the authentication details source
     */
	public void setAuthenticationDetailsSource(
			AuthenticationDetailsSource<HttpServletRequest, ?> authenticationDetailsSource) {
        Assert.notNull(authenticationDetailsSource, "AuthenticationDetailsSource required");
        this.authenticationDetailsSource = authenticationDetailsSource;
    }

}
