import com.kumuluz.ee.discovery.annotations.RegisterService;
import org.eclipse.microprofile.auth.LoginConfig;
import org.eclipse.microprofile.openapi.annotations.OpenAPIDefinition;
import org.eclipse.microprofile.openapi.annotations.enums.SecuritySchemeType;
import org.eclipse.microprofile.openapi.annotations.info.Info;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.security.SecurityScheme;
import org.eclipse.microprofile.openapi.annotations.servers.Server;

import javax.ws.rs.ApplicationPath;
import javax.ws.rs.core.Application;
import java.util.logging.Logger;

@ApplicationPath("/")
@LoginConfig(authMethod = "MP-JWT")
@RegisterService
//@SecurityScheme(securitySchemeName = "openid-connect", type = SecuritySchemeType.OPENIDCONNECT,
//        openIdConnectUrl = "https://cognito-idp.us-east-1.amazonaws.com/us-east-1_cl8iVMzUw/.well-known/openid-configuration")
//@OpenAPIDefinition(
//        info = @Info(title = "Product Catalog API", version = "1.0.0"),
//        servers = @Server(url = "http://localhost:8080"),
//        security = @SecurityRequirement(name = "openid-connect"))
public class ProductCatalogApplication extends Application {
    private static final Logger LOG = Logger.getLogger(ProductCatalogApplication.class.getName());
    public ProductCatalogApplication() {
        LOG.info("ProductCatalogApplication started!");
    }
}
