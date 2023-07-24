import com.kumuluz.ee.discovery.annotations.RegisterService;

import javax.ws.rs.ApplicationPath;
import javax.ws.rs.core.Application;
import java.util.logging.Logger;

@ApplicationPath("/")
@RegisterService
public class ProductCatalogApplication extends Application {
    private static final Logger LOG = Logger.getLogger(ProductCatalogApplication.class.getName());

    public ProductCatalogApplication() {
        LOG.info("ProductCatalogApplication started!");
    }
}
