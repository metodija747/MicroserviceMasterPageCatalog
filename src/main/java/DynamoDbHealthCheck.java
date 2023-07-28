import org.eclipse.microprofile.health.*;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;

@ApplicationScoped
@Liveness
@Readiness
@RequestScoped
public class DynamoDbHealthCheck implements HealthCheck {

    private DynamoDbClient dynamoDB;
    @Inject
    private ConfigProperties configProperties;

    @PostConstruct
    public void init() {
        this.dynamoDB = DynamoDbClient.builder()
                .region(Region.of(configProperties.getDynamoRegion()))
                .build();
    }

    private static final String REQUIRED_TABLE = "ProductCatalog";

    @Override
    public HealthCheckResponse call() {
        HealthCheckResponseBuilder responseBuilder = HealthCheckResponse.named("DynamoDB health check");
        try {
            DescribeTableResponse describeTableResponse = dynamoDB.describeTable(DescribeTableRequest.builder().tableName(REQUIRED_TABLE).build());
            ProvisionedThroughputDescription throughput = describeTableResponse.table().provisionedThroughput();

            if (throughput.readCapacityUnits() < 1 || throughput.writeCapacityUnits() < 1) {
                return responseBuilder.down().withData("error", "Table " + REQUIRED_TABLE + " has insufficient read/write capacity").build();
            }

            return responseBuilder.up().build();
        } catch (DynamoDbException e) {
            return responseBuilder.down().withData("error", "Table " + REQUIRED_TABLE + " does not exist or another error occurred").build();
        }
    }
}
