import com.kumuluz.ee.health.checks.KumuluzHealthCheck;
import org.eclipse.microprofile.health.*;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.Arrays;
import java.util.List;

@ApplicationScoped
@Liveness
@Readiness
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

    private static final List<String> REQUIRED_TABLES = Arrays.asList("CartDB", "CommentDB", "OrdersDB", "ProductCatalog");

    @Override
    public HealthCheckResponse call() {
        HealthCheckResponseBuilder responseBuilder = HealthCheckResponse.named("DynamoDB health check");
        try {
            ListTablesResponse listTablesResponse = dynamoDB.listTables(ListTablesRequest.builder().build());
            List<String> tables = listTablesResponse.tableNames();

            for (String requiredTable : REQUIRED_TABLES) {
                if (!tables.contains(requiredTable)) {
                    return responseBuilder.down().withData("error", "Table " + requiredTable + " does not exist").build();
                }

                DescribeTableResponse describeTableResponse = dynamoDB.describeTable(DescribeTableRequest.builder().tableName(requiredTable).build());
                ProvisionedThroughputDescription throughput = describeTableResponse.table().provisionedThroughput();

                if (throughput.readCapacityUnits() < 1 || throughput.writeCapacityUnits() < 1) {
                    return responseBuilder.down().withData("error", "Table " + requiredTable + " has insufficient read/write capacity").build();
                }
            }

            return responseBuilder.up().build();
        } catch (Exception e) {
            return responseBuilder.down().withData("error", e.getMessage()).build();
        }
    }
}
