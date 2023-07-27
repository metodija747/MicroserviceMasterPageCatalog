import com.kumuluz.ee.configuration.cdi.ConfigBundle;
import com.kumuluz.ee.configuration.cdi.ConfigValue;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

@ApplicationScoped
@ConfigBundle("aws-config")
public class ConfigProperties {

    @ConfigValue(watch = true)
    private String dynamoRegion;

    @ConfigValue(watch = true)
    private String tableName;

    @ConfigValue(watch = true)
    private String cognitoIssuer;

    // getter and setter methods

    public String getDynamoRegion() {
        return dynamoRegion;
    }

    public void setDynamoRegion(String dynamoRegion) {
        this.dynamoRegion = dynamoRegion;
    }

    public String getTableName() {
        return tableName;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    public String getCognitoIssuer() {
        return cognitoIssuer;
    }

    public void setCognitoIssuer(String cognitoIssuer) {
        this.cognitoIssuer = cognitoIssuer;
    }
}
