import com.kumuluz.ee.configuration.cdi.ConfigBundle;
import com.kumuluz.ee.configuration.cdi.ConfigValue;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

@ApplicationScoped
@ConfigBundle("aws-config")
public class ConfigProperties {

    @ConfigValue(watch = true)
    private String dynamoDbRegion;

    @ConfigValue(watch = true)
    private String tableName;

    @ConfigValue(watch = true)
    private String issuer;

    // getter and setter methods

    public String getDynamoDbRegion() {
        return dynamoDbRegion;
    }

    public void setDynamoDbRegion(String dynamoDbRegion) {
        this.dynamoDbRegion = dynamoDbRegion;
    }

    public String getTableName() {
        return tableName;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }
}
