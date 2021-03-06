package ch.agilesolutions.boot.start.service;

import static java.util.Collections.emptyList;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.kerberos.authentication.KerberosServiceRequestToken;
import org.springframework.stereotype.Service;

import ch.agilesolutions.boot.start.config.KerberosConfig;
import ch.agilesolutions.boot.start.config.KerberosPoperties;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;

@Service
public class AuthenticationService {
	static final long EXPIRATIONTIME = 864_000_00; // 1 day in milliseconds
	static final String PREFIX = "Bearer";

	@Autowired
	private KerberosPoperties kerberosProperties;

	public AuthenticationService(KerberosPoperties kerberosProperties) {
		super();
		this.kerberosProperties = kerberosProperties;
	}

	public void addToken(HttpServletResponse res, KerberosServiceRequestToken token) {

		Map<String, Object> claims = new HashMap<>();

		SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream().forEach(r -> {
			claims.put(r.getAuthority(), r.getAuthority());
		});

		String JwtToken = Jwts.builder()
				.setSubject(((KerberosServiceRequestToken)SecurityContextHolder.getContext().getAuthentication()).getName())
				.setClaims(claims)
				.setExpiration(new Date(System.currentTimeMillis() + EXPIRATIONTIME))
				.signWith(SignatureAlgorithm.HS512, kerberosProperties.getSigningkey())
				.compact();
		res.addHeader("Authorization", PREFIX + " " + JwtToken);
		res.addHeader("Access-Control-Expose-Headers", "Authorization");
	}

	public Authentication getAuthentication(HttpServletRequest request) {
		String token = request.getHeader("Authorization");
		if (token != null) {
			String user = Jwts.parser().setSigningKey(kerberosProperties.getSigningkey()).parseClaimsJws(token.replace(PREFIX, "")).getBody().getSubject();

			if (user != null)
				return new UsernamePasswordAuthenticationToken(user, null, emptyList());
		}
		return null;
	}
}