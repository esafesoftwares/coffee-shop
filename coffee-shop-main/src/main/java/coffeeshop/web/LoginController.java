package coffeeshop.web;

import coffeeshop.ejb.InitManager;
import coffeeshop.web.util.MessageBundle;
import javax.annotation.PostConstruct;
import javax.enterprise.context.RequestScoped;
import javax.faces.application.FacesMessage;
import javax.faces.context.FacesContext;
import javax.faces.validator.ValidatorException;
import javax.inject.Inject;
import javax.inject.Named;
import javax.security.enterprise.AuthenticationStatus;
import javax.security.enterprise.SecurityContext;
import javax.security.enterprise.authentication.mechanism.http.AuthenticationParameters;
import javax.security.enterprise.credential.Credential;
import javax.security.enterprise.credential.Password;
import javax.security.enterprise.credential.UsernamePasswordCredential;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.security.Principal;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.ejb.EJB;

@Named
@RequestScoped
public class LoginController {

    private static final Logger LOG = Logger.getLogger(LoginController.class.getName());

    @SuppressWarnings("CdiInjectionPointsInspection")
    @Inject
    private SecurityContext securityContext;

    @Inject
    private MessageBundle bundle;
    
    @EJB
    private InitManager initManager;

    private FacesContext facesContext;
    // User name and password for login
    private String username, password;

    @PostConstruct
    private void getFacesContext() {
        facesContext = FacesContext.getCurrentInstance();
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String login() {
        initManager.checkAndAddDefaultAdminUser();
        if (isLoggedIn()) {
            facesContext.addMessage(null, new FacesMessage(bundle.getString("Ui.Message.LogoutFirst")));
            return null;
        }
        Credential credential = new UsernamePasswordCredential(username, new Password(password));
        AuthenticationStatus status = securityContext.authenticate(
                getRequest(),
                getResponse(),
                AuthenticationParameters.withParams()
                        .credential(credential));
        LOG.log(Level.INFO, "Called authentication, status: {0}", status);
        Principal principal = securityContext.getCallerPrincipal();
        if (principal != null) {
            LOG.log(Level.INFO, "User current principal: {0}, name: {1}",
                    new Object[]{principal, principal.getName()});
        }
        switch (status) {
            case NOT_DONE:
                throw new ValidatorException(new FacesMessage(FacesMessage.SEVERITY_ERROR,
                        "Authentication Failed",
                        "Internal error occurred"
                ));
            case SEND_FAILURE:
                facesContext.addMessage(null, new FacesMessage(FacesMessage.SEVERITY_ERROR,
                        bundle.getString("Ui.Message.AuthFailedTitle"),
                        bundle.getString("Ui.Message.AuthFailedDetail")
                ));
                break;
            case SEND_CONTINUE:
            case SUCCESS:
                facesContext.addMessage(null, new FacesMessage(FacesMessage.SEVERITY_INFO,
                        bundle.getFormatted("Ui.Message.AuthSuccess", username),
                        null
                ));
                break;
        }
        String outcome = null;
        switch (status) {
            case SEND_CONTINUE:
                facesContext.responseComplete();
                break;
            case SUCCESS:
                if (securityContext.isCallerInRole("admin")) {
                    outcome = "/admin/console";
                } else if (securityContext.isCallerInRole("staff")) {
                    outcome = "/store/console";
                } else {
                    outcome = "/index";
                }
                break;
            case SEND_FAILURE:
                break;
            default:
                break;
        }
        return outcome;
    }

    private boolean isLoggedIn() {
        return securityContext.getCallerPrincipal() != null;
    }

    public String logout() throws ServletException {
        if (isLoggedIn()) {
            getRequest().logout();
            facesContext.addMessage(null, new FacesMessage(FacesMessage.SEVERITY_INFO,
                    bundle.getString("Ui.Message.LogoutTitle"),
                    null
            ));
        } else {
            LOG.log(Level.WARNING, "Try to logout from a user not logged in");
        }
        return "/index";
    }

    private HttpServletRequest getRequest() {
        return (HttpServletRequest) facesContext.getExternalContext().getRequest();
    }

    private HttpServletResponse getResponse() {
        return (HttpServletResponse) facesContext.getExternalContext().getResponse();
    }
}
